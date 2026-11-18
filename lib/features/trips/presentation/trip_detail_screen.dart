import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/trip.dart';
import '../providers/trips_provider.dart';

/// Day-by-day itinerary with add / edit / delete / drag-to-reorder.
class TripDetailScreen extends ConsumerStatefulWidget {
  const TripDetailScreen({super.key, required this.tripId});
  final String tripId;

  @override
  ConsumerState<TripDetailScreen> createState() => _TripDetailScreenState();
}

class _TripDetailScreenState extends ConsumerState<TripDetailScreen> {
  int _day = 0;

  TripsNotifier get _trips => ref.read(tripsProvider.notifier);

  Future<void> _editActivity(Trip trip, {Activity? existing}) async {
    final result = await showModalBottomSheet<Activity>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _ActivitySheet(existing: existing),
    );
    if (result == null || !mounted) return;
    if (existing == null) {
      await runApi(context, _trips.addActivity(trip.id, _day, result));
    } else {
      await runApi(context, _trips.editActivity(trip.id, _day, result));
    }
  }

  Future<void> _deleteTrip(Trip trip) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete trip?'),
        content: Text('"${trip.title}" and its itinerary will be removed.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete', style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
    if (ok == true && mounted) {
      final removal = runApi(context, _trips.remove(trip.id));
      context.pop();
      await removal;
    }
  }

  @override
  Widget build(BuildContext context) {
    final trip = ref.watch(tripByIdProvider(widget.tripId));
    if (trip == null) {
      return Scaffold(
        appBar: AppBar(),
        body: const EmptyState(icon: Icons.luggage_rounded, title: 'Trip not found', message: 'It may have been deleted.'),
      );
    }
    final day = _day.clamp(0, trip.days.length - 1);
    final activities = trip.days.isEmpty ? <Activity>[] : trip.days[day].activities;
    final planned = trip.days.expand((d) => d.activities).fold(0, (s, a) => s + a.cost);
    final dest = ref.watch(destinationByIdProvider(trip.destinationId));

    return Scaffold(
      body: NestedScrollView(
        headerSliverBuilder: (_, _) => [
          SliverAppBar(
            expandedHeight: 240,
            pinned: true,
            backgroundColor: AppColors.navy,
            foregroundColor: Colors.white,
            leading: Padding(
              padding: const EdgeInsets.all(6),
              child: CircleIconButton(icon: Icons.arrow_back_rounded, onPressed: () => context.pop()),
            ),
            actions: [
              PopupMenuButton<String>(
                iconColor: Colors.white,
                onSelected: (v) {
                  switch (v) {
                    case 'dest':
                      context.push('/destination/${trip.destinationId}');
                    case 'hotels':
                      context.push('/hotels?dest=${trip.destinationId}');
                    case 'pack':
                      context.push('/packing?dest=${trip.destinationId}&days=${trip.dayCount}');
                    case 'delete':
                      _deleteTrip(trip);
                  }
                },
                itemBuilder: (_) => const [
                  PopupMenuItem(value: 'dest', child: Text('View destination')),
                  PopupMenuItem(value: 'hotels', child: Text('Find hotels')),
                  PopupMenuItem(value: 'pack', child: Text('Packing checklist')),
                  PopupMenuItem(value: 'delete', child: Text('Delete trip', style: TextStyle(color: AppColors.danger))),
                ],
              ),
            ],
            flexibleSpace: FlexibleSpaceBar(
              titlePadding: const EdgeInsetsDirectional.only(start: 60, bottom: 16, end: 60),
              title: Text(trip.title,
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 18),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis),
              background: Stack(
                fit: StackFit.expand,
                children: [
                  NetImage(trip.imageUrl),
                  const DecoratedBox(decoration: BoxDecoration(gradient: AppColors.heroGradient)),
                ],
              ),
            ),
          ),
        ],
        body: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 20, 4),
              child: Row(
                children: [
                  _Stat(icon: Icons.calendar_today_rounded, label: Fmt.range(trip.startDate, trip.endDate)),
                  const SizedBox(width: 8),
                  _Stat(icon: Icons.group_rounded, label: '${trip.travelers}'),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _Stat(
                      icon: Icons.account_balance_wallet_rounded,
                      label: '${Fmt.compact(planned)} / ${Fmt.compact(trip.budget)}',
                      color: planned > trip.budget ? AppColors.danger : AppColors.success,
                    ),
                  ),
                ],
              ),
            ),
            SizedBox(
              height: 74,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
                itemCount: trip.days.length,
                separatorBuilder: (_, _) => const SizedBox(width: 8),
                itemBuilder: (_, i) {
                  final selected = i == day;
                  final date = trip.startDate.add(Duration(days: i));
                  return GestureDetector(
                    onTap: () => setState(() => _day = i),
                    child: AnimatedContainer(
                      duration: const Duration(milliseconds: 200),
                      width: 64,
                      decoration: BoxDecoration(
                        color: selected ? AppColors.navy : AppColors.surface,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: selected ? AppColors.navy : AppColors.border),
                      ),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text('Day ${i + 1}',
                              style: TextStyle(
                                  fontSize: 12, fontWeight: FontWeight.w700, color: selected ? Colors.white : AppColors.textPrimary)),
                          Text(Fmt.shortDate(date),
                              style: TextStyle(fontSize: 11, color: selected ? Colors.white70 : AppColors.textSecondary)),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
            if (dest != null && day == 0 && activities.isEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
                child: Text('Tip: popular in ${dest.name} — ${dest.attractions.map((a) => a.name).take(3).join(', ')}',
                    style: Theme.of(context).textTheme.bodySmall),
              ),
            Expanded(
              child: activities.isEmpty
                  ? EmptyState(
                      icon: Icons.event_note_rounded,
                      title: 'Nothing planned for Day ${day + 1}',
                      message: 'Add sightseeing, meals, transport and more.',
                      action: 'Add activity',
                      onAction: () => _editActivity(trip),
                    )
                  : ReorderableListView.builder(
                      padding: const EdgeInsets.fromLTRB(20, 8, 20, 100),
                      itemCount: activities.length,
                      buildDefaultDragHandles: false,
                      onReorderItem: (o, n) => runApi(context, _trips.reorderActivity(trip.id, day, o, n)),
                      itemBuilder: (_, i) {
                        final a = activities[i];
                        return Dismissible(
                          key: ValueKey(a.id),
                          direction: DismissDirection.endToStart,
                          background: Container(
                            alignment: Alignment.centerRight,
                            padding: const EdgeInsets.only(right: 24),
                            margin: const EdgeInsets.only(bottom: 10),
                            decoration: BoxDecoration(color: AppColors.danger, borderRadius: BorderRadius.circular(20)),
                            child: const Icon(Icons.delete_rounded, color: Colors.white),
                          ),
                          onDismissed: (_) => runApi(context, _trips.removeActivity(trip.id, day, a.id)),
                          child: Padding(
                            padding: const EdgeInsets.only(bottom: 10),
                            child: _ActivityTile(
                              activity: a,
                              index: i,
                              onTap: () {
                                _day = day;
                                _editActivity(trip, existing: a);
                              },
                            ),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'add-activity',
        backgroundColor: AppColors.coral,
        foregroundColor: Colors.white,
        onPressed: () {
          _day = day;
          _editActivity(trip);
        },
        icon: const Icon(Icons.add_rounded),
        label: const Text('Activity', style: TextStyle(fontWeight: FontWeight.w700)),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.icon, required this.label, this.color = AppColors.ocean});
  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.08), borderRadius: BorderRadius.circular(12)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 15, color: color),
          const SizedBox(width: 6),
          Flexible(
            child: Text(label,
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: color), overflow: TextOverflow.ellipsis),
          ),
        ],
      ),
    );
  }
}

class _ActivityTile extends StatelessWidget {
  const _ActivityTile({required this.activity, required this.index, required this.onTap});
  final Activity activity;
  final int index;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final a = activity;
    final color = activityColor(a.type);
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 12, 4, 12),
          child: Row(
            children: [
              SizedBox(
                width: 48,
                child: Text(a.time, style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.navy)),
              ),
              IconBubble(icon: activityIcon(a.type), color: color),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(a.title, style: const TextStyle(fontWeight: FontWeight.w700)),
                    const SizedBox(height: 2),
                    Text(
                      [a.type.label, if (a.cost > 0) Fmt.bdt(a.cost), if (a.note.isNotEmpty) a.note].join(' · '),
                      style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              ReorderableDragStartListener(
                index: index,
                child: const Padding(
                  padding: EdgeInsets.all(8),
                  child: Icon(Icons.drag_indicator_rounded, color: AppColors.textSecondary),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ActivitySheet extends StatefulWidget {
  const _ActivitySheet({this.existing});
  final Activity? existing;

  @override
  State<_ActivitySheet> createState() => _ActivitySheetState();
}

class _ActivitySheetState extends State<_ActivitySheet> {
  late final _title = TextEditingController(text: widget.existing?.title);
  late final _note = TextEditingController(text: widget.existing?.note);
  late final _cost = TextEditingController(text: (widget.existing?.cost ?? 0) == 0 ? '' : '${widget.existing!.cost}');
  late ActivityType _type = widget.existing?.type ?? ActivityType.sightseeing;
  late TimeOfDay _time = _parse(widget.existing?.time) ?? const TimeOfDay(hour: 10, minute: 0);

  static TimeOfDay? _parse(String? s) {
    if (s == null) return null;
    final p = s.split(':');
    if (p.length != 2) return null;
    return TimeOfDay(hour: int.tryParse(p[0]) ?? 10, minute: int.tryParse(p[1]) ?? 0);
  }

  String get _timeStr => '${_time.hour.toString().padLeft(2, '0')}:${_time.minute.toString().padLeft(2, '0')}';

  @override
  void dispose() {
    _title.dispose();
    _note.dispose();
    _cost.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(24, 0, 24, 24 + MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(widget.existing == null ? 'Add activity' : 'Edit activity', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            TextField(
              controller: _title,
              autofocus: widget.existing == null,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(hintText: 'What are you doing?'),
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final t in ActivityType.values)
                  ChoiceChip(
                    avatar: Icon(activityIcon(t), size: 18, color: _type == t ? Colors.white : activityColor(t)),
                    label: Text(t.label),
                    selected: _type == t,
                    onSelected: (_) => setState(() => _type = t),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: InkWell(
                    borderRadius: BorderRadius.circular(16),
                    onTap: () async {
                      final t = await showTimePicker(context: context, initialTime: _time);
                      if (t != null) setState(() => _time = t);
                    },
                    child: InputDecorator(
                      decoration: const InputDecoration(prefixIcon: Icon(Icons.schedule_rounded)),
                      child: Text(_timeStr, style: const TextStyle(fontWeight: FontWeight.w600)),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _cost,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(hintText: 'Cost ৳', prefixIcon: Icon(Icons.payments_rounded)),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextField(controller: _note, decoration: const InputDecoration(hintText: 'Notes (optional)')),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: () {
                if (_title.text.trim().isEmpty) return;
                final a = Activity(
                  id: widget.existing?.id ?? uuid.v4(),
                  title: _title.text.trim(),
                  time: _timeStr,
                  type: _type,
                  note: _note.text.trim(),
                  cost: int.tryParse(_cost.text.replaceAll(',', '')) ?? 0,
                );
                Navigator.pop(context, a);
              },
              child: Text(widget.existing == null ? 'Add to itinerary' : 'Save changes'),
            ),
          ],
        ),
      ),
    );
  }
}
