import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/remote_sync.dart';
import '../../../core/providers.dart';
import '../../../data/local/local_store.dart';
import '../../../data/mock/images.dart';
import '../../../data/models/trip.dart';

/// Trips and their day-by-day itineraries.
///
/// API mode: creates wait for the server (it assigns the ids); edits that the
/// UI must reflect instantly (delete, reorder) update locally first and
/// re-sync from the server if the call fails.
class TripsNotifier extends Notifier<List<Trip>> with RemoteSync<List<Trip>> {
  @override
  List<Trip> build() {
    if (watchApi() != null) return loadRemote(const []);
    final stored = ref.watch(localStoreProvider).readList(LocalStore.tripsKey, Trip.fromJson);
    return stored ?? _seed();
  }

  @override
  Future<void> refresh() => safeRefresh((a) => a.trips());

  void _set(List<Trip> trips) {
    state = trips;
    if (api == null) ref.read(localStoreProvider).writeList(LocalStore.tripsKey, trips, (t) => t.toJson());
  }

  /// Runs a server call after an optimistic change; on failure reloads the truth and rethrows.
  Future<void> _sync(Future<void> Function() call) async {
    try {
      await call();
    } catch (_) {
      await refresh();
      rethrow;
    }
  }

  Trip? byId(String id) {
    for (final t in state) {
      if (t.id == id) return t;
    }
    return null;
  }

  void _upsert(Trip trip) {
    final exists = state.any((t) => t.id == trip.id);
    _set(exists
        ? [for (final t in state) t.id == trip.id ? trip : t]
        : ([...state, trip]..sort((a, b) => a.startDate.compareTo(b.startDate))));
  }

  /// Saves a new trip and returns it as stored (with the server's id in API mode).
  Future<Trip> add(Trip trip) async {
    final client = api;
    final saved = client == null
        ? trip
        : await client.createTrip(
            destinationId: trip.destinationId,
            title: trip.title,
            startDate: trip.startDate,
            endDate: trip.endDate,
            travelers: trip.travelers,
            budget: trip.budget,
            days: trip.activityCount > 0 ? trip.days : null,
          );
    _upsert(saved);
    return saved;
  }

  Future<void> remove(String id) async {
    _set(state.where((t) => t.id != id).toList());
    final client = api;
    if (client != null) await _sync(() => client.deleteTrip(id));
  }

  void _updateDay(String tripId, int dayIndex, List<Activity> Function(List<Activity>) change) {
    final trip = byId(tripId);
    if (trip == null || dayIndex >= trip.days.length) return;
    final days = [...trip.days];
    days[dayIndex] = days[dayIndex].copyWith(activities: change([...days[dayIndex].activities]));
    _upsert(trip.copyWith(days: days));
  }

  Future<void> addActivity(String tripId, int dayIndex, Activity a) async {
    final client = api;
    final saved = client == null ? a : await client.addActivity(tripId, dayIndex + 1, a);
    _updateDay(tripId, dayIndex, (list) => list..add(saved));
  }

  Future<void> editActivity(String tripId, int dayIndex, Activity a) async {
    final client = api;
    final saved = client == null ? a : await client.updateActivity(tripId, dayIndex + 1, a);
    _updateDay(tripId, dayIndex, (list) => [for (final x in list) x.id == saved.id ? saved : x]);
  }

  /// Removed locally at once (a swiped Dismissible must leave the tree immediately).
  Future<void> removeActivity(String tripId, int dayIndex, String activityId) async {
    _updateDay(tripId, dayIndex, (list) => list..removeWhere((x) => x.id == activityId));
    final client = api;
    if (client != null) await _sync(() => client.deleteActivity(tripId, dayIndex + 1, activityId));
  }

  /// [newIndex] is the final position after removal (ReorderableListView.onReorderItem semantics).
  Future<void> reorderActivity(String tripId, int dayIndex, int oldIndex, int newIndex) async {
    _updateDay(tripId, dayIndex, (list) {
      final item = list.removeAt(oldIndex);
      list.insert(newIndex.clamp(0, list.length), item);
      return list;
    });
    final client = api;
    final trip = byId(tripId);
    if (client == null || trip == null) return;
    final ids = trip.days[dayIndex].activities.map((a) => a.id).toList();
    await _sync(() => client.reorderActivities(tripId, dayIndex + 1, ids));
  }

  static List<ItineraryDay> emptyDays(int count) =>
      List.generate(count, (i) => ItineraryDay(dayNumber: i + 1, activities: const []));

  List<Trip> _seed() {
    final start = DateTime.now().add(const Duration(days: 21));
    final s = DateTime(start.year, start.month, start.day);
    return [
      Trip(
        id: 'seed-coxs',
        destinationId: 'coxs-bazar',
        title: "Cox's Bazar Getaway",
        imageUrl: Img.beach,
        startDate: s,
        endDate: s.add(const Duration(days: 3)),
        travelers: 2,
        budget: 40000,
        days: const [
          ItineraryDay(dayNumber: 1, activities: [
            Activity(id: 'a1', title: 'Flight Dhaka → Cox\'s Bazar', time: '08:00', type: ActivityType.transport, cost: 9000),
            Activity(id: 'a2', title: 'Check in at Sea Pearl Resort', time: '12:00', type: ActivityType.stay),
            Activity(id: 'a3', title: 'Sunset at Laboni Beach', time: '17:30', type: ActivityType.sightseeing),
          ]),
          ItineraryDay(dayNumber: 2, activities: [
            Activity(id: 'a4', title: 'Marine Drive to Himchari', time: '09:00', type: ActivityType.adventure, cost: 2500),
            Activity(id: 'a5', title: 'Seafood lunch at Inani', time: '13:30', type: ActivityType.food, cost: 1800),
          ]),
          ItineraryDay(dayNumber: 3, activities: [
            Activity(id: 'a6', title: 'Burmese Market shopping', time: '11:00', type: ActivityType.shopping, cost: 3000),
          ]),
          ItineraryDay(dayNumber: 4, activities: [
            Activity(id: 'a7', title: 'Flight back to Dhaka', time: '15:00', type: ActivityType.transport),
          ]),
        ],
      ),
    ];
  }
}

final tripsProvider = NotifierProvider<TripsNotifier, List<Trip>>(TripsNotifier.new);

final tripByIdProvider = Provider.family<Trip?, String>((ref, id) {
  for (final t in ref.watch(tripsProvider)) {
    if (t.id == id) return t;
  }
  return null;
});
