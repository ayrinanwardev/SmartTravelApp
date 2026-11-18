import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/trip.dart';
import '../providers/trips_provider.dart';

class MyTripsScreen extends ConsumerWidget {
  const MyTripsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trips = ref.watch(tripsProvider);
    final upcoming = trips.where((t) => t.isUpcoming).toList();
    final past = trips.where((t) => !t.isUpcoming).toList().reversed.toList();

    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('My Trips'),
          toolbarHeight: 64,
          actions: [
            IconButton(
              tooltip: 'Travel journal',
              onPressed: () => context.push('/journal'),
              icon: const Icon(Icons.auto_stories_rounded),
            ),
            const SizedBox(width: 8),
          ],
          bottom: TabBar(
            labelColor: AppColors.navy,
            indicatorColor: AppColors.coral,
            labelStyle: const TextStyle(fontWeight: FontWeight.w700),
            tabs: [Tab(text: 'Upcoming (${upcoming.length})'), Tab(text: 'Past (${past.length})')],
          ),
        ),
        floatingActionButton: FloatingActionButton.extended(
          heroTag: 'new-trip',
          backgroundColor: AppColors.navy,
          foregroundColor: Colors.white,
          onPressed: () => context.push('/plan'),
          icon: const Icon(Icons.add_rounded),
          label: const Text('New trip', style: TextStyle(fontWeight: FontWeight.w700)),
        ),
        body: TabBarView(
          children: [
            _TripList(
              trips: upcoming,
              empty: EmptyState(
                icon: Icons.luggage_rounded,
                title: 'No upcoming trips',
                message: 'Plan your next adventure or let the AI assistant draft one for you.',
                action: 'Ask AI to plan',
                onAction: () => context.push('/assistant'),
              ),
            ),
            _TripList(
              trips: past,
              empty: const EmptyState(
                icon: Icons.history_rounded,
                title: 'No past trips yet',
                message: 'Completed trips will appear here.',
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TripList extends StatelessWidget {
  const _TripList({required this.trips, required this.empty});
  final List<Trip> trips;
  final Widget empty;

  @override
  Widget build(BuildContext context) {
    if (trips.isEmpty) return empty;
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 100),
      itemCount: trips.length,
      separatorBuilder: (_, _) => const SizedBox(height: 16),
      itemBuilder: (_, i) => TripCard(trips[i]),
    );
  }
}

class TripCard extends StatelessWidget {
  const TripCard(this.trip, {super.key});
  final Trip trip;

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.push('/trip/${trip.id}'),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              children: [
                NetImage(trip.imageUrl, height: 150, width: double.infinity),
                Positioned(
                  top: 12,
                  left: 12,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                    decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(20)),
                    child: Text('${trip.dayCount} days · ${trip.travelers} ${trip.travelers == 1 ? 'traveller' : 'travellers'}',
                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                  ),
                ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(trip.title, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      const Icon(Icons.calendar_today_rounded, size: 15, color: AppColors.textSecondary),
                      const SizedBox(width: 6),
                      Text(Fmt.range(trip.startDate, trip.endDate), style: const TextStyle(color: AppColors.textSecondary)),
                      const Spacer(),
                      const Icon(Icons.checklist_rounded, size: 16, color: AppColors.coral),
                      const SizedBox(width: 4),
                      Text('${trip.activityCount} activities', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
