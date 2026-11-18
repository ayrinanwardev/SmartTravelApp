enum ActivityType {
  sightseeing('Sightseeing'),
  food('Food'),
  transport('Transport'),
  stay('Stay'),
  adventure('Adventure'),
  shopping('Shopping'),
  relax('Relax');

  const ActivityType(this.label);
  final String label;
}

class Activity {
  const Activity({
    required this.id,
    required this.title,
    required this.time,
    required this.type,
    this.note = '',
    this.cost = 0,
  });

  final String id;
  final String title;
  final String time; // "09:30"
  final ActivityType type;
  final String note;
  final int cost;

  Activity copyWith({String? title, String? time, ActivityType? type, String? note, int? cost}) => Activity(
        id: id,
        title: title ?? this.title,
        time: time ?? this.time,
        type: type ?? this.type,
        note: note ?? this.note,
        cost: cost ?? this.cost,
      );

  Map<String, dynamic> toJson() =>
      {'id': id, 'title': title, 'time': time, 'type': type.name, 'note': note, 'cost': cost};

  factory Activity.fromJson(Map<String, dynamic> j) => Activity(
        id: j['id'] as String,
        title: j['title'] as String,
        time: j['time'] as String,
        type: ActivityType.values.byName(j['type'] as String),
        note: (j['note'] as String?) ?? '',
        cost: (j['cost'] as int?) ?? 0,
      );
}

class ItineraryDay {
  const ItineraryDay({required this.dayNumber, required this.activities});

  final int dayNumber;
  final List<Activity> activities;

  ItineraryDay copyWith({List<Activity>? activities}) =>
      ItineraryDay(dayNumber: dayNumber, activities: activities ?? this.activities);

  Map<String, dynamic> toJson() =>
      {'dayNumber': dayNumber, 'activities': activities.map((a) => a.toJson()).toList()};

  factory ItineraryDay.fromJson(Map<String, dynamic> j) => ItineraryDay(
        dayNumber: j['dayNumber'] as int,
        activities: (j['activities'] as List).map((e) => Activity.fromJson(e as Map<String, dynamic>)).toList(),
      );
}

class Trip {
  const Trip({
    required this.id,
    required this.destinationId,
    required this.title,
    required this.imageUrl,
    required this.startDate,
    required this.endDate,
    required this.travelers,
    required this.budget,
    required this.days,
  });

  final String id;
  final String destinationId;
  final String title;
  final String imageUrl;
  final DateTime startDate;
  final DateTime endDate;
  final int travelers;
  final int budget;
  final List<ItineraryDay> days;

  int get dayCount => endDate.difference(startDate).inDays + 1;
  bool get isUpcoming => !endDate.isBefore(DateTime.now());
  int get activityCount => days.fold(0, (sum, d) => sum + d.activities.length);

  Trip copyWith({
    String? title,
    DateTime? startDate,
    DateTime? endDate,
    int? travelers,
    int? budget,
    List<ItineraryDay>? days,
  }) =>
      Trip(
        id: id,
        destinationId: destinationId,
        title: title ?? this.title,
        imageUrl: imageUrl,
        startDate: startDate ?? this.startDate,
        endDate: endDate ?? this.endDate,
        travelers: travelers ?? this.travelers,
        budget: budget ?? this.budget,
        days: days ?? this.days,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'destinationId': destinationId,
        'title': title,
        'imageUrl': imageUrl,
        'startDate': startDate.toIso8601String(),
        'endDate': endDate.toIso8601String(),
        'travelers': travelers,
        'budget': budget,
        'days': days.map((d) => d.toJson()).toList(),
      };

  factory Trip.fromJson(Map<String, dynamic> j) => Trip(
        id: j['id'] as String,
        destinationId: j['destinationId'] as String,
        title: j['title'] as String,
        imageUrl: j['imageUrl'] as String,
        startDate: DateTime.parse(j['startDate'] as String),
        endDate: DateTime.parse(j['endDate'] as String),
        travelers: j['travelers'] as int,
        budget: j['budget'] as int,
        days: (j['days'] as List).map((e) => ItineraryDay.fromJson(e as Map<String, dynamic>)).toList(),
      );
}
