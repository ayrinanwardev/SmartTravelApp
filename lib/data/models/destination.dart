enum DestinationCategory {
  beach('Beach', '🏖️'),
  mountain('Mountain', '🏔️'),
  city('City', '🌆'),
  adventure('Adventure', '🧗'),
  culture('Culture', '🏛️');

  const DestinationCategory(this.label, this.emoji);
  final String label;
  final String emoji;
}

enum Season {
  winter('Winter'),
  spring('Spring'),
  summer('Summer'),
  monsoon('Monsoon'),
  autumn('Autumn');

  const Season(this.label);
  final String label;
}

class Attraction {
  const Attraction({
    required this.name,
    required this.type,
    required this.imageUrl,
    required this.rating,
    this.distanceKm = 0,
    this.latitude,
    this.longitude,
  });

  final String name;
  final String type;
  final String imageUrl;
  final double rating;
  final double distanceKm;
  final double? latitude;
  final double? longitude;

  bool get hasLocation => latitude != null && longitude != null;

  factory Attraction.fromJson(Map<String, dynamic> j) => Attraction(
        name: j['name'] as String,
        type: (j['type'] as String?) ?? '',
        imageUrl: (j['imageUrl'] as String?) ?? '',
        rating: (j['rating'] as num?)?.toDouble() ?? 0,
        distanceKm: (j['distanceKm'] as num?)?.toDouble() ?? 0,
        latitude: (j['latitude'] as num?)?.toDouble(),
        longitude: (j['longitude'] as num?)?.toDouble(),
      );
}

class Review {
  const Review({
    required this.author,
    required this.rating,
    required this.comment,
    required this.date,
  });

  final String author;
  final double rating;
  final String comment;
  final DateTime date;

  factory Review.fromJson(Map<String, dynamic> j) => Review(
        author: j['author'] as String,
        rating: (j['rating'] as num).toDouble(),
        comment: (j['comment'] as String?) ?? '',
        date: DateTime.parse(j['date'] as String),
      );
}

class CostEstimate {
  const CostEstimate({
    required this.transport,
    required this.hotelPerNight,
    required this.foodPerDay,
    required this.activitiesPerDay,
  });

  final int transport;
  final int hotelPerNight;
  final int foodPerDay;
  final int activitiesPerDay;

  factory CostEstimate.fromJson(Map<String, dynamic> j) => CostEstimate(
        transport: j['transport'] as int,
        hotelPerNight: j['hotelPerNight'] as int,
        foodPerDay: j['foodPerDay'] as int,
        activitiesPerDay: j['activitiesPerDay'] as int,
      );

  int forDays(int days, {int travelers = 1}) =>
      (transport * travelers) + (hotelPerNight * (days - 1).clamp(1, 365)) +
      ((foodPerDay + activitiesPerDay) * days * travelers);
}

class Destination {
  const Destination({
    required this.id,
    required this.name,
    required this.country,
    required this.tagline,
    required this.description,
    required this.imageUrl,
    required this.gallery,
    required this.category,
    required this.bestSeasons,
    required this.bestTime,
    required this.rating,
    required this.reviewCount,
    required this.cost,
    required this.highlights,
    required this.attractions,
    required this.reviews,
    required this.avgTempC,
    this.isTrending = false,
    this.isPopular = false,
    this.latitude,
    this.longitude,
  });

  final String id;
  final String name;
  final String country;
  final String tagline;
  final String description;
  final String imageUrl;
  final List<String> gallery;
  final DestinationCategory category;
  final List<Season> bestSeasons;
  final String bestTime;
  final double rating;
  final int reviewCount;
  final CostEstimate cost;
  final List<String> highlights;
  final List<Attraction> attractions;
  final List<Review> reviews;
  final int avgTempC;
  final bool isTrending;
  final bool isPopular;
  final double? latitude;
  final double? longitude;

  bool get hasLocation => latitude != null && longitude != null;

  /// Parses the API's `DestinationDetail` JSON (enums arrive in lower case, like Dart's).
  factory Destination.fromJson(Map<String, dynamic> j) {
    List<T> list<T>(String key, T Function(dynamic) f) => ((j[key] as List?) ?? const []).map(f).toList();
    final image = j['imageUrl'] as String? ?? '';
    final gallery = list('gallery', (e) => e as String);
    return Destination(
      id: j['id'] as String,
      name: j['name'] as String,
      country: j['country'] as String,
      tagline: (j['tagline'] as String?) ?? '',
      description: (j['description'] as String?) ?? '',
      imageUrl: image,
      gallery: gallery.isEmpty ? [image] : gallery,
      category: DestinationCategory.values.byName(j['category'] as String),
      bestSeasons: list('bestSeasons', (e) => Season.values.byName(e as String)),
      bestTime: (j['bestTime'] as String?) ?? '',
      rating: (j['rating'] as num).toDouble(),
      reviewCount: j['reviewCount'] as int,
      cost: CostEstimate.fromJson(j['cost'] as Map<String, dynamic>),
      highlights: list('highlights', (e) => e as String),
      attractions: list('attractions', (e) => Attraction.fromJson(e as Map<String, dynamic>)),
      reviews: list('reviews', (e) => Review.fromJson(e as Map<String, dynamic>)),
      avgTempC: (j['avgTempC'] as int?) ?? 20,
      isTrending: (j['isTrending'] as bool?) ?? false,
      isPopular: (j['isPopular'] as bool?) ?? false,
      latitude: (j['latitude'] as num?)?.toDouble(),
      longitude: (j['longitude'] as num?)?.toDouble(),
    );
  }

  String get location => '$name, $country';

  /// Rough per-person cost for a typical 3-day trip, used for budget filters.
  int get typicalTripCost => cost.forDays(3);
}
