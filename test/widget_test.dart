import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:smart_travel_app/core/providers.dart';
import 'package:smart_travel_app/data/mock/mock_data.dart';
import 'package:smart_travel_app/features/assistant/providers/ai_travel_service.dart';
import 'package:smart_travel_app/features/budget/providers/budget_provider.dart';
import 'package:smart_travel_app/features/saved/providers/wishlist_provider.dart';
import 'package:smart_travel_app/features/trips/providers/trips_provider.dart';

Future<ProviderContainer> _container() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  // Offline mode: these tests exercise local state only, no HTTP.
  final c = ProviderContainer(overrides: [
    sharedPreferencesProvider.overrideWithValue(prefs),
    apiProvider.overrideWithValue(null),
  ]);
  addTearDown(c.dispose);
  return c;
}

void main() {
  group('LocalAiTravelService', () {
    final ai = LocalAiTravelService(MockData.destinations);

    test("parses destination, days and budget from the Cox's Bazar prompt", () async {
      final reply = await ai.ask("Plan a 4-day trip to Cox's Bazar under ৳20,000");
      final plan = reply.plan!;
      expect(plan.destination.id, 'coxs-bazar');
      expect(plan.days, 4);
      expect(plan.requestedBudget, 20000);
      expect(plan.itinerary, hasLength(4));
      expect(plan.estimate, plan.breakdown.values.reduce((a, b) => a + b));
    });

    test('understands lakh budgets and couples', () async {
      final plan = (await ai.ask('5 days in Bali for a couple, budget 2 lakh')).plan!;
      expect(plan.destination.id, 'bali');
      expect(plan.travelers, 2);
      expect(plan.requestedBudget, 200000);
    });

    test('returns suggestions when no destination is found', () async {
      final reply = await ai.ask('somewhere nice please');
      expect(reply.plan, isNull);
      expect(reply.text, contains('ideas'));
    });
  });

  group('State', () {
    test('wishlist toggles and persists', () async {
      final c = await _container();
      expect(await c.read(wishlistProvider.notifier).toggle('bali'), isTrue);
      expect(c.read(wishlistProvider), contains('bali'));
      expect(await c.read(wishlistProvider.notifier).toggle('bali'), isFalse);
      expect(c.read(wishlistProvider), isEmpty);
    });

    test('activities can be reordered', () async {
      final c = await _container();
      final trip = c.read(tripsProvider).first;
      final first = trip.days.first.activities.first.id;
      await c.read(tripsProvider.notifier).reorderActivity(trip.id, 0, 0, 2);
      final after = c.read(tripsProvider).first.days.first.activities;
      expect(after.last.id, first);
    });

    test('budget summary flags overspending', () async {
      final c = await _container();
      await c.read(budgetTotalProvider.notifier).set(1000);
      final s = c.read(budgetSummaryProvider);
      expect(s.isOver, isTrue);
      expect(s.remaining, lessThan(0));
    });
  });
}
