import 'dart:math';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/wanderly_api.dart';
import '../../../core/providers.dart';
import '../../../data/models/destination.dart';
import '../../../data/models/trip.dart';

/// A generated plan returned by the assistant.
class AiTripPlan {
  const AiTripPlan({
    required this.destination,
    required this.days,
    required this.travelers,
    required this.requestedBudget,
    required this.estimate,
    required this.breakdown,
    required this.itinerary,
    required this.tips,
  });

  final Destination destination;
  final int days;
  final int travelers;
  final int? requestedBudget;
  final int estimate;
  final Map<String, int> breakdown;
  final List<ItineraryDay> itinerary;
  final List<String> tips;

  bool get fitsBudget => requestedBudget == null || estimate <= requestedBudget!;
}

class AiReply {
  const AiReply({required this.text, this.plan});
  final String text;
  final AiTripPlan? plan;
}

/// Interface so a real LLM endpoint (e.g. a Spring Boot `/api/ai/plan` route
/// that calls Claude) can replace the on-device planner without UI changes.
abstract interface class AiTravelService {
  Future<AiReply> ask(String prompt);
}

/// Deterministic on-device planner: parses destination, length, travellers and
/// budget out of free text and builds an itinerary from destination data.
class LocalAiTravelService implements AiTravelService {
  LocalAiTravelService(this._destinations);

  final List<Destination> _destinations;

  @override
  Future<AiReply> ask(String prompt) async {
    await Future<void>.delayed(const Duration(milliseconds: 900));
    final p = prompt.toLowerCase();

    final dest = _matchDestination(p);
    if (dest == null) {
      final suggestions = _suggest(p);
      return AiReply(
        text: "I couldn't spot a destination in that. Try something like "
            "\"Plan a 4-day trip to Cox's Bazar under ৳20,000\".\n\n"
            'Some ideas for you: ${suggestions.map((d) => d.name).join(', ')}.',
      );
    }

    final days = _parseDays(p) ?? 3;
    final travelers = _parseTravelers(p) ?? 1;
    final budget = _parseBudget(p);
    final plan = _buildPlan(dest, days.clamp(1, 14), travelers.clamp(1, 20), budget);

    final b = StringBuffer()
      ..write('Here is a ${plan.days}-day plan for ${dest.name}')
      ..write(travelers > 1 ? ' for $travelers travellers' : '')
      ..write('. Estimated cost is ৳${_fmt(plan.estimate)}');
    if (budget != null) {
      b.write(plan.fitsBudget
          ? ' — comfortably within your ৳${_fmt(budget)} budget. 🎉'
          : '. That is ৳${_fmt(plan.estimate - budget)} over your ৳${_fmt(budget)} budget, so I '
              'picked budget stays and you could trim a day or travel by bus.');
    } else {
      b.write('.');
    }
    return AiReply(text: b.toString(), plan: plan);
  }

  Destination? _matchDestination(String p) {
    for (final d in _destinations) {
      final name = d.name.toLowerCase();
      final simple = name.replaceAll("'", '').replaceAll('’', '');
      if (p.contains(name) || p.contains(simple) || p.contains(d.id.replaceAll('-', ' '))) return d;
    }
    // Aliases
    const aliases = {'coxs': 'coxs-bazar', 'cox': 'coxs-bazar', 'sreemangal': 'sylhet', 'nepal': 'pokhara', 'japan': 'kyoto', 'switzerland': 'interlaken', 'greece': 'santorini'};
    for (final e in aliases.entries) {
      if (p.contains(e.key)) return _destinations.where((d) => d.id == e.value).firstOrNull;
    }
    return null;
  }

  List<Destination> _suggest(String p) {
    for (final c in DestinationCategory.values) {
      if (p.contains(c.label.toLowerCase())) {
        return _destinations.where((d) => d.category == c).take(3).toList();
      }
    }
    final sorted = [..._destinations]..sort((a, b) => b.rating.compareTo(a.rating));
    return sorted.take(3).toList();
  }

  int? _parseDays(String p) {
    final m = RegExp(r'(\d+)\s*[- ]?\s*(day|days|night|nights)').firstMatch(p);
    if (m != null) {
      final n = int.parse(m.group(1)!);
      return m.group(2)!.startsWith('night') ? n + 1 : n;
    }
    if (p.contains('weekend')) return 2;
    if (p.contains('week')) return 7;
    return null;
  }

  int? _parseTravelers(String p) {
    final m = RegExp(r'(\d+)\s*(people|persons|travellers|travelers|friends|adults|of us)').firstMatch(p);
    if (m != null) return int.parse(m.group(1)!);
    if (p.contains('couple') || p.contains('honeymoon') || p.contains('with my wife') || p.contains('with my husband')) return 2;
    if (p.contains('family')) return 4;
    return null;
  }

  int? _parseBudget(String p) {
    final m = RegExp(r'(?:৳|tk\.?|bdt|taka|under|budget|within|below)\s*([\d,]+(?:\.\d+)?)\s*(k|lakh|lac)?').firstMatch(p) ??
        RegExp(r'([\d,]+(?:\.\d+)?)\s*(k|lakh|lac)?\s*(?:৳|tk|bdt|taka)').firstMatch(p);
    if (m == null) return null;
    var value = double.tryParse(m.group(1)!.replaceAll(',', ''));
    if (value == null) return null;
    switch (m.group(2)) {
      case 'k':
        value *= 1000;
      case 'lakh' || 'lac':
        value *= 100000;
    }
    return value < 500 ? null : value.round();
  }

  AiTripPlan _buildPlan(Destination d, int days, int travelers, int? budget) {
    final cost = d.cost;
    final nights = max(days - 1, 1);
    final rooms = (travelers / 2).ceil();

    int hotelRate = cost.hotelPerNight;
    int estimateFor(int rate) =>
        cost.transport * travelers + rate * nights * rooms + (cost.foodPerDay + cost.activitiesPerDay) * days * travelers;

    // Downgrade accommodation if we're over budget.
    if (budget != null && estimateFor(hotelRate) > budget) {
      hotelRate = (hotelRate * 0.55).round();
    }

    final breakdown = <String, int>{
      'Transport': cost.transport * travelers,
      'Stay': hotelRate * nights * rooms,
      'Food': cost.foodPerDay * days * travelers,
      'Activities': cost.activitiesPerDay * days * travelers,
    };
    final estimate = breakdown.values.fold(0, (a, b) => a + b);

    final attractions = [...d.attractions];
    final itinerary = <ItineraryDay>[];
    for (var i = 0; i < days; i++) {
      final acts = <Activity>[];
      String id(int n) => 'ai-$i-$n-${DateTime.now().microsecondsSinceEpoch}';
      if (i == 0) {
        acts.add(Activity(id: id(0), title: 'Arrive in ${d.name} & check in', time: '11:00', type: ActivityType.stay));
      } else {
        acts.add(Activity(id: id(0), title: 'Breakfast near your stay', time: '08:30', type: ActivityType.food));
      }
      if (attractions.isNotEmpty) {
        final a = attractions[i % attractions.length];
        acts.add(Activity(
          id: id(1),
          title: i < attractions.length ? 'Explore ${a.name}' : 'Revisit ${a.name} at golden hour',
          time: i == 0 ? '15:00' : '10:00',
          type: a.type.toLowerCase().contains('adventure') ? ActivityType.adventure : ActivityType.sightseeing,
          note: a.type,
          cost: (cost.activitiesPerDay * 0.6).round(),
        ));
      }
      if (i > 0 && i - 1 < d.highlights.length && i != days - 1) {
        acts.add(Activity(id: id(2), title: d.highlights[i - 1], time: '14:00', type: ActivityType.adventure));
      }
      acts.add(Activity(
        id: id(3),
        title: i == days - 1 ? 'Souvenir shopping & departure' : 'Dinner — try local specialities',
        time: i == days - 1 ? '16:00' : '19:30',
        type: i == days - 1 ? ActivityType.shopping : ActivityType.food,
        cost: cost.foodPerDay ~/ 2,
      ));
      itinerary.add(ItineraryDay(dayNumber: i + 1, activities: acts));
    }

    final tips = <String>[
      'Best time to visit: ${d.bestTime}.',
      if (d.avgTempC >= 26) 'Pack light cotton, sunscreen and sunglasses — it averages ${d.avgTempC}°C.',
      if (d.avgTempC < 18) 'Bring warm layers — average temperature is ${d.avgTempC}°C.',
      if (budget != null && estimate > budget) 'Travel mid-week and book stays 3+ weeks ahead to save 15–25%.',
      if (travelers > 2) 'Split a private car between $travelers people — often cheaper than individual tickets.',
    ];

    return AiTripPlan(
      destination: d,
      days: days,
      travelers: travelers,
      requestedBudget: budget,
      estimate: estimate,
      breakdown: breakdown,
      itinerary: itinerary,
      tips: tips,
    );
  }

  static String _fmt(int v) {
    final s = v.toString();
    final buf = StringBuffer();
    for (var i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) buf.write(',');
      buf.write(s[i]);
    }
    return buf.toString();
  }
}

/// Calls the backend planner (`POST /api/ai/plan`). Swapping the server's
/// planner for an LLM later needs no change here.
class ApiAiTravelService implements AiTravelService {
  ApiAiTravelService(this._api, this._destinations);

  final WanderlyApi _api;
  final List<Destination> _destinations;

  @override
  Future<AiReply> ask(String prompt) async {
    final json = await _api.aiPlan(prompt);
    final p = json['plan'] as Map<String, dynamic>?;
    if (p == null) return AiReply(text: json['reply'] as String);

    final destId = (p['destination'] as Map<String, dynamic>)['id'] as String;
    final destination = _destinations.where((d) => d.id == destId).firstOrNull;
    if (destination == null) return AiReply(text: json['reply'] as String);

    var n = 0;
    final itinerary = [
      for (final d in (p['itinerary'] as List).cast<Map<String, dynamic>>())
        ItineraryDay(
          dayNumber: d['dayNumber'] as int,
          activities: [
            for (final a in (d['activities'] as List).cast<Map<String, dynamic>>())
              Activity.fromJson({...a, 'id': 'ai-${n++}'}),
          ],
        ),
    ];
    return AiReply(
      text: json['reply'] as String,
      plan: AiTripPlan(
        destination: destination,
        days: p['days'] as int,
        travelers: p['travelers'] as int,
        requestedBudget: p['requestedBudget'] as int?,
        estimate: p['estimate'] as int,
        breakdown: (p['breakdown'] as Map<String, dynamic>).map((k, v) => MapEntry(k, v as int)),
        itinerary: itinerary,
        tips: (p['tips'] as List).cast<String>(),
      ),
    );
  }
}

final aiTravelServiceProvider = Provider<AiTravelService>((ref) {
  final destinations = ref.watch(destinationsProvider).value ?? const <Destination>[];
  final api = ref.watch(apiProvider);
  return api == null ? LocalAiTravelService(destinations) : ApiAiTravelService(api, destinations);
});
