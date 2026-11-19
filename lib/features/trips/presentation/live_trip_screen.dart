import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:go_router/go_router.dart';
import 'package:latlong2/latlong.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/location/location_service.dart';
import '../../../core/network/realtime_client.dart';
import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/notification.dart';
import '../../map/presentation/map_widgets.dart';
import '../../notifications/providers/notifications_provider.dart';
import '../providers/trips_provider.dart';

/// Group trip, live: companions' positions on a map (WebSocket), share-my-location toggle,
/// who's online, and the invite code. Location is only shared while this screen is open.
class LiveTripScreen extends ConsumerStatefulWidget {
  const LiveTripScreen({super.key, required this.tripId});

  final String tripId;

  @override
  ConsumerState<LiveTripScreen> createState() => _LiveTripScreenState();
}

class _LiveTripScreenState extends ConsumerState<LiveTripScreen> {
  static const _palette = [AppColors.coral, Color(0xFF8B5CF6), AppColors.success, AppColors.warning, Color(0xFF0EA5E9)];

  final _map = MapController();
  final Map<int, MemberLocation> _others = {};
  List<TripMember> _members = const [];
  String? _shareCode;
  LatLng? _me;
  bool _sharing = false;
  StreamSubscription<RealtimeEvent>? _events;
  StreamSubscription<Position>? _gps;
  RealtimeClient? _client;
  bool _centered = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _attach());
  }

  void _attach() {
    final client = ref.read(realtimeProvider);
    _client = client;
    _loadMembers();
    final trip = ref.read(tripByIdProvider(widget.tripId));
    if (trip?.isOwner == true) _loadShareCode();
    if (client == null) return;
    client.connected.addListener(_onConnection);
    _events = client.events.listen(_onEvent);
    _watch();
  }

  /// (Re)subscribe — also after a reconnect, so the snapshot is fresh.
  void _onConnection() {
    if (_client?.connected.value == true) _watch();
  }

  void _watch() => _client?.send({'type': 'trip.watch', 'tripId': widget.tripId});

  void _onEvent(RealtimeEvent e) {
    if (e.data['tripId'] != widget.tripId) return;
    switch (e.type) {
      case 'location.snapshot':
        final list = (e.data['locations'] as List? ?? const []).cast<Map<String, dynamic>>();
        setState(() {
          _others
            ..clear()
            ..addEntries(list.map(MemberLocation.fromJson).map((l) => MapEntry(l.userId, l)));
        });
      case 'location.updated':
        final l = MemberLocation.fromJson(e.data);
        setState(() => _others[l.userId] = l);
        if (!_centered && _me == null) {
          _centered = true;
          _map.move(LatLng(l.lat, l.lng), 14);
        }
      case 'location.stopped':
        setState(() => _others.remove((e.data['userId'] as num).toInt()));
      case 'trip.member_joined':
        _loadMembers();
    }
  }

  Future<void> _loadMembers() async {
    final api = ref.read(apiProvider);
    if (api == null) return;
    try {
      final m = await api.tripMembers(widget.tripId);
      if (mounted) setState(() => _members = m);
    } catch (_) {}
  }

  Future<void> _loadShareCode() async {
    final api = ref.read(apiProvider);
    if (api == null) return;
    try {
      final code = await api.shareTrip(widget.tripId);
      if (mounted) setState(() => _shareCode = code);
    } catch (_) {}
  }

  Future<void> _toggleSharing(bool on) async {
    if (!on) return _stopSharing();
    try {
      await LocationService.ensurePermission();
    } on LocationUnavailable catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(e.message),
        action: e.canOpenSettings ? SnackBarAction(label: 'Settings', onPressed: LocationService.openSettings) : null,
      ));
      return;
    }
    setState(() => _sharing = true);
    _gps = LocationService.watch(distanceFilterMeters: 5).listen((p) {
      final here = LatLng(p.latitude, p.longitude);
      final first = _me == null;
      setState(() => _me = here);
      if (first) _map.move(here, 15);
      _client?.send({
        'type': 'location.update',
        'tripId': widget.tripId,
        'lat': p.latitude,
        'lng': p.longitude,
        'accuracy': p.accuracy,
      });
    }, onError: (Object e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Location error: $e')));
      _stopSharing();
    });
  }

  Future<void> _stopSharing() async {
    await _gps?.cancel();
    _gps = null;
    _client?.send({'type': 'location.stop', 'tripId': widget.tripId});
    if (mounted) setState(() => _sharing = false);
  }

  Color _colorFor(int userId) => _palette[userId % _palette.length];

  @override
  void dispose() {
    _gps?.cancel();
    if (_sharing) _client?.send({'type': 'location.stop', 'tripId': widget.tripId});
    _client?.connected.removeListener(_onConnection);
    _events?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final trip = ref.watch(tripByIdProvider(widget.tripId));
    final client = ref.watch(realtimeProvider);
    final dest = trip == null ? null : ref.watch(destinationByIdProvider(trip.destinationId));
    final center = dest?.hasLocation == true ? LatLng(dest!.latitude!, dest.longitude!) : const LatLng(23.685, 90.3563);

    if (client == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Live trip')),
        body: const EmptyState(
          icon: Icons.wifi_off_rounded,
          title: 'Live sharing needs the server',
          message: 'Run the app with the backend (not offline demo mode) to share locations in real time.',
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(trip?.title ?? 'Live trip'),
        actions: [
          ValueListenableBuilder<bool>(
            valueListenable: client.connected,
            builder: (_, on, _) => Padding(
              padding: const EdgeInsets.only(right: 12),
              child: Row(children: [
                Icon(Icons.circle, size: 10, color: on ? AppColors.success : AppColors.warning),
                const SizedBox(width: 6),
                Text(on ? 'Live' : 'Connecting…', style: const TextStyle(fontWeight: FontWeight.w600)),
              ]),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: FlutterMap(
              mapController: _map,
              options: MapOptions(initialCenter: center, initialZoom: 12),
              children: [
                osmTiles(),
                MarkerLayer(markers: [
                  for (final l in _others.values)
                    Marker(
                      point: LatLng(l.lat, l.lng),
                      width: 110,
                      height: 72,
                      alignment: Alignment.topCenter,
                      child: MapPin(icon: Icons.person_rounded, color: _colorFor(l.userId), label: l.name.split(' ').first),
                    ),
                  if (_me != null) Marker(point: _me!, width: 44, height: 44, child: const UserDot()),
                ]),
                const MapAttribution(),
              ],
            ),
          ),
          _Panel(
            sharing: _sharing,
            onToggle: _toggleSharing,
            members: _members,
            liveIds: _others.keys.toSet(),
            colorFor: _colorFor,
            shareCode: _shareCode,
            isOwner: trip?.isOwner ?? false,
            onLeave: trip == null || trip.isOwner
                ? null
                : () async {
                    final ok = await runApi(context, ref.read(tripsProvider.notifier).leave(widget.tripId).then((_) => true));
                    if (ok == true && context.mounted) context.go('/trips');
                  },
          ),
        ],
      ),
    );
  }
}

class _Panel extends StatelessWidget {
  const _Panel({
    required this.sharing,
    required this.onToggle,
    required this.members,
    required this.liveIds,
    required this.colorFor,
    required this.shareCode,
    required this.isOwner,
    required this.onLeave,
  });

  final bool sharing;
  final ValueChanged<bool> onToggle;
  final List<TripMember> members;
  final Set<int> liveIds;
  final Color Function(int) colorFor;
  final String? shareCode;
  final bool isOwner;
  final VoidCallback? onLeave;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.surface,
        boxShadow: [BoxShadow(color: Color(0x1A000000), blurRadius: 12, offset: Offset(0, -2))],
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                value: sharing,
                onChanged: onToggle,
                activeThumbColor: AppColors.coral,
                title: const Text('Share my live location', style: TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text(sharing ? 'Companions can see you on the map' : 'Only while this screen is open'),
              ),
              if (isOwner && shareCode != null)
                Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                  decoration: BoxDecoration(color: AppColors.surfaceMuted, borderRadius: BorderRadius.circular(14)),
                  child: Row(
                    children: [
                      const Icon(Icons.group_add_rounded, color: AppColors.ocean),
                      const SizedBox(width: 10),
                      const Text('Invite code  '),
                      SelectableText(shareCode!,
                          style: const TextStyle(fontWeight: FontWeight.w800, letterSpacing: 3, fontSize: 18)),
                      const Spacer(),
                      IconButton(
                        tooltip: 'Copy invite',
                        icon: const Icon(Icons.copy_rounded),
                        onPressed: () {
                          Clipboard.setData(ClipboardData(
                              text: 'Join my trip on Wanderly! Open My Trips → Join trip and enter code $shareCode'));
                          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Invite copied')));
                        },
                      ),
                    ],
                  ),
                ),
              Text('People on this trip (${members.length})', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 6),
              SizedBox(
                height: 64,
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  children: [
                    for (final m in members)
                      Padding(
                        padding: const EdgeInsets.only(right: 14),
                        child: Column(
                          children: [
                            Stack(
                              children: [
                                CircleAvatar(
                                  backgroundColor: colorFor(m.userId),
                                  child: Text(m.name.isEmpty ? '?' : m.name[0].toUpperCase(),
                                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                                ),
                                if (m.online || liveIds.contains(m.userId))
                                  Positioned(
                                    right: 0,
                                    bottom: 0,
                                    child: Container(
                                      width: 12,
                                      height: 12,
                                      decoration: BoxDecoration(
                                        color: liveIds.contains(m.userId) ? AppColors.coral : AppColors.success,
                                        shape: BoxShape.circle,
                                        border: Border.all(color: Colors.white, width: 2),
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(m.role == 'owner' ? '${m.name.split(' ').first} ★' : m.name.split(' ').first,
                                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600)),
                          ],
                        ),
                      ),
                  ],
                ),
              ),
              if (onLeave != null)
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton.icon(
                    onPressed: onLeave,
                    icon: const Icon(Icons.logout_rounded, color: AppColors.danger),
                    label: const Text('Leave trip', style: TextStyle(color: AppColors.danger)),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
