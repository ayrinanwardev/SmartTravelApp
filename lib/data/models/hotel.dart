class Hotel {
  const Hotel({
    required this.id,
    required this.destinationId,
    required this.name,
    required this.address,
    required this.imageUrls,
    required this.pricePerNight,
    required this.rating,
    required this.reviewCount,
    required this.amenities,
    required this.description,
    this.stars = 4,
  });

  final String id;
  final String destinationId;
  final String name;
  final String address;
  final List<String> imageUrls;
  final int pricePerNight;
  final double rating;
  final int reviewCount;
  final List<String> amenities;
  final String description;
  final int stars;

  factory Hotel.fromJson(Map<String, dynamic> j) => Hotel(
        id: j['id'] as String,
        destinationId: j['destinationId'] as String,
        name: j['name'] as String,
        address: (j['address'] as String?) ?? '',
        imageUrls: ((j['imageUrls'] as List?) ?? const []).cast<String>(),
        pricePerNight: j['pricePerNight'] as int,
        rating: (j['rating'] as num).toDouble(),
        reviewCount: (j['reviewCount'] as int?) ?? 0,
        amenities: ((j['amenities'] as List?) ?? const []).cast<String>(),
        description: (j['description'] as String?) ?? '',
        stars: (j['stars'] as int?) ?? 3,
      );
}

class Booking {
  const Booking({
    required this.id,
    required this.hotelId,
    required this.hotelName,
    required this.imageUrl,
    required this.checkIn,
    required this.checkOut,
    required this.guests,
    required this.rooms,
    required this.total,
    required this.createdAt,
  });

  final String id;
  final String hotelId;
  final String hotelName;
  final String imageUrl;
  final DateTime checkIn;
  final DateTime checkOut;
  final int guests;
  final int rooms;
  final int total;
  final DateTime createdAt;

  int get nights => checkOut.difference(checkIn).inDays;

  Map<String, dynamic> toJson() => {
        'id': id,
        'hotelId': hotelId,
        'hotelName': hotelName,
        'imageUrl': imageUrl,
        'checkIn': checkIn.toIso8601String(),
        'checkOut': checkOut.toIso8601String(),
        'guests': guests,
        'rooms': rooms,
        'total': total,
        'createdAt': createdAt.toIso8601String(),
      };

  factory Booking.fromJson(Map<String, dynamic> j) => Booking(
        id: j['id'] as String,
        hotelId: j['hotelId'] as String,
        hotelName: j['hotelName'] as String,
        imageUrl: (j['imageUrl'] as String?) ?? '',
        checkIn: DateTime.parse(j['checkIn'] as String),
        checkOut: DateTime.parse(j['checkOut'] as String),
        guests: j['guests'] as int,
        rooms: j['rooms'] as int,
        total: j['total'] as int,
        createdAt: DateTime.parse(j['createdAt'] as String),
      );
}
