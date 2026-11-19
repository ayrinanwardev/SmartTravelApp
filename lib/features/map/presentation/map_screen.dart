import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:latlong2/latlong.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/location/location_service.dart';
import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/destination.dart';
import '../../../data/models/hotel.dart';
import 'map_widgets.dart';

enum _Kind { destination, attraction, hotel }

class _Place {
  const _Place(this.kind, this.name, this.subtitle, this.point, {this.id, this.imageUrl, this.rating});

  final _Kind kind;
  final String name;
  final String subtitle;
  final LatLng point;
  final String? id;
  final String? imageUrl;
  final double? rating;

  IconData get icon => switch (kind) {
        _Kind.destination => Icons.place_rounded,
        _Kind.attraction => Icons.photo_camera_rounded,
        _Kind.hotel => Icons.hotel_rounded,
      };

  Color get color => switch (kind) {
        _Kind.destination => AppColors.coral,
        _Kind.attraction => AppColors.ocean,
        _Kind.hotel => const Color(0xFF8B5CF6),
      };
}

/// Interactive map: a destination with its attractions and hotels, or (without
/// [destinationId]) every destination. Shows the user's position and distances.
class MapScreen extends ConsumerStatefulWidget {
  const MapScreen({super.key, this.destinationId});

  final String? destinationId;

  @override
  ConsumerState<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends ConsumerState<MapScreen> {
  final _map = MapController();
  _Place? _selected;
  LatLng? _me;
  bool _locating = false;
  final Set<_Kind> _visible = {..._Kind.values};

  List<_Place> _places(List<Destination> all, List<Hotel> hotels) {
    final id = widget.destinationId;
    if (id == null) {
      return [
        for (final d in all)
          if (d.hasLocation)
            _Place(_Kind.destination, d.name, d.country, LatLng(d.latitude!, d.longitude!),
                id: d.id, imageUrl: d.imageUrl, rating: d.rating),
      ];
    }
    final d = all.where((x) => x.id == id).firstOrNull;
    if (d == null) return const [];
    return [
      if (d.hasLocation)
        _Place(_Kind.destination, d.name, d.tagline, LatLng(d.latitude!, d.longitude!), id: d.id, imageUrl: d.imageUrl, rating: d.rating),
      for (final a in d.attractions)
        if (a.hasLocation)
          _Place(_Kind.attraction, a.name, a.type, LatLng(a.latitude!, a.longitude!), imageUrl: a.imageUrl, rating: a.rating),
      for (final h in hotels.where((h) => h.destinationId == id))
        if (h.hasLocation)
          _Place(_Kind.hotel, h.name, '${Fmt.bdt(h.pricePerNight)} / night', LatLng(h.latitude!, h.longitude!),
              id: h.id, imageUrl: h.imageUrls.firstOrNull, rating: h.rating),
    ];
  }

  void _fit(List<LatLng> points) {
    if (points.isEmpty) return;
    if (points.length == 1) {
      _map.move(points.first, 13);
      return;
    }
    _map.fitCamera(CameraFit.coordinates(coordinates: points, padding: const EdgeInsets.fromLTRB(48, 120, 48, 220)));
  }

  Future<void> _locate() async {
    setState(() => _locating = true);
    try {
      final me = await LocationService.current();
      if (!mounted) return;
      setState(() => _me = me);
      _map.move(me, 14);
    } on LocationUnavailable catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(e.message),
        action: e.canOpenSettings ? SnackBarAction(label: 'Settings', onPressed: LocationService.openSettings) : null,
      ));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Could not get your location: $e')));
    } finally {
      if (mounted) setState(() => _locating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final all = ref.watch(destinationsProvider).value ?? const <Destination>[];
    final hotels = ref.watch(hotelsProvider(null)).value ?? const <Hotel>[];
    final places = _places(all, hotels).where((p) => _visible.contains(p.kind)).toList();
    final dest = widget.destinationId == null ? null : all.where((d) => d.id == widget.destinationId).firstOrNull;
    final start = places.isEmpty ? const LatLng(23.685, 90.3563) : places.first.point; // fallback: Bangladesh

    return Scaffold(
      body: Stack(
        children: [
          FlutterMap(
            mapController: _map,
            options: MapOptions(
              initialCenter: start,
              initialZoom: widget.destinationId == null ? 3 : 11,
              onMapReady: () => _fit(places.map((p) => p.point).toList()),
              onTap: (_, _) => setState(() => _selected = null),
            ),
            children: [
              osmTiles(),
              MarkerLayer(
                markers: [
                  for (final p in places)
                    Marker(
                      point: p.point,
                      width: 110,
                      height: 72,
                      alignment: Alignment.topCenter,
                      child: GestureDetector(
                        onTap: () => setState(() => _selected = p),
                        child: MapPin(
                          icon: p.icon,
                          color: p.color,
                          selected: identical(p, _selected),
                          label: widget.destinationId == null ? p.name : null,
                        ),
                      ),
                    ),
                  if (_me != null) Marker(point: _me!, width: 44, height: 44, child: const UserDot()),
                ],
              ),
              const MapAttribution(),
            ],
          ),
          // Top bar: back, title, layer filters
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      CircleIconButton(icon: Icons.arrow_back_rounded, onPressed: () => context.pop()),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(24),
                            boxShadow: const [BoxShadow(color: Color(0x22000000), blurRadius: 8)],
                          ),
                          child: Text(dest == null ? 'All destinations' : '${dest.name} map',
                              style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                        ),
                      ),
                    ],
                  ),
                  if (widget.destinationId != null) ...[
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 8,
                      children: [
                        for (final (kind, label, icon) in [
                          (_Kind.attraction, 'Attractions', Icons.photo_camera_rounded),
                          (_Kind.hotel, 'Hotels', Icons.hotel_rounded),
                        ])
                          FilterChip(
                            avatar: Icon(icon, size: 18, color: _visible.contains(kind) ? Colors.white : AppColors.navy),
                            label: Text(label),
                            labelStyle: TextStyle(
                              fontWeight: FontWeight.w600,
                              color: _visible.contains(kind) ? Colors.white : AppColors.textPrimary,
                            ),
                            selected: _visible.contains(kind),
                            onSelected: (v) => setState(() => v ? _visible.add(kind) : _visible.remove(kind)),
                          ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
          Positioned(
            right: 16,
            bottom: _selected == null ? 32 : 200,
            child: FloatingActionButton(
              heroTag: 'locate-me',
              backgroundColor: Colors.white,
              foregroundColor: AppColors.ocean,
              onPressed: _locating ? null : _locate,
              tooltip: 'My location',
              child: _locating
                  ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                  : const Icon(Icons.my_location_rounded),
            ),
          ),
          if (_selected != null) Positioned(left: 16, right: 16, bottom: 24, child: _PlaceCard(place: _selected!, me: _me)),
        ],
      ),
    );
  }
}

class _PlaceCard extends StatelessWidget {
  const _PlaceCard({required this.place, required this.me});

  final _Place place;
  final LatLng? me;

  @override
  Widget build(BuildContext context) {
    final distance = me == null ? null : LocationService.distanceKm(me!, place.point);
    return SafeArea(
      top: false,
      child: Card(
        elevation: 6,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              if (place.imageUrl != null)
                NetImage(place.imageUrl!, width: 76, height: 76, borderRadius: BorderRadius.circular(14))
              else
                IconBubble(icon: place.icon, color: place.color, size: 76),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(place.name, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16), maxLines: 1, overflow: TextOverflow.ellipsis),
                    Text(place.subtitle, style: const TextStyle(color: AppColors.textSecondary), maxLines: 1, overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        if (place.rating != null) ...[
                          const Icon(Icons.star_rounded, color: AppColors.star, size: 16),
                          Text(' ${place.rating!.toStringAsFixed(1)}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
                          const SizedBox(width: 10),
                        ],
                        if (distance != null)
                          Text('${LocationService.formatDistance(distance)} away',
                              style: const TextStyle(fontSize: 13, color: AppColors.ocean, fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ],
                ),
              ),
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton.filled(
                    tooltip: 'Directions',
                    style: IconButton.styleFrom(backgroundColor: AppColors.coral),
                    onPressed: () => openDirections(place.point, from: me),
                    icon: const Icon(Icons.directions_rounded),
                  ),
                  if (place.kind != _Kind.attraction && place.id != null)
                    IconButton(
                      tooltip: 'Open',
                      onPressed: () => context.push(place.kind == _Kind.hotel ? '/hotel/${place.id}' : '/destination/${place.id}'),
                      icon: const Icon(Icons.chevron_right_rounded),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
