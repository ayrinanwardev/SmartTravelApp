/// An in-app notification (also delivered live over the WebSocket and, when
/// Firebase is configured, as a push notification).
class AppNotification {
  const AppNotification({
    required this.id,
    required this.title,
    required this.message,
    required this.type,
    required this.read,
    required this.createdAt,
  });

  final int id;
  final String title;
  final String message;

  /// "trip" | "booking" | "budget" | "system"
  final String type;
  final bool read;
  final DateTime createdAt;

  AppNotification markRead() =>
      AppNotification(id: id, title: title, message: message, type: type, read: true, createdAt: createdAt);

  factory AppNotification.fromJson(Map<String, dynamic> j) => AppNotification(
        id: (j['id'] as num).toInt(),
        title: j['title'] as String,
        message: (j['message'] as String?) ?? '',
        type: (j['type'] as String?) ?? 'system',
        read: (j['read'] as bool?) ?? false,
        createdAt: DateTime.parse(j['createdAt'] as String),
      );
}

/// A person on a group trip.
class TripMember {
  const TripMember({required this.userId, required this.name, required this.role, required this.online});

  final int userId;
  final String name;
  final String role;
  final bool online;

  factory TripMember.fromJson(Map<String, dynamic> j) => TripMember(
        userId: (j['userId'] as num).toInt(),
        name: j['name'] as String,
        role: (j['role'] as String?) ?? 'member',
        online: (j['online'] as bool?) ?? false,
      );
}

/// A companion's live position on the shared trip map.
class MemberLocation {
  const MemberLocation({
    required this.userId,
    required this.name,
    required this.lat,
    required this.lng,
    required this.at,
    this.accuracy,
  });

  final int userId;
  final String name;
  final double lat;
  final double lng;
  final double? accuracy;
  final DateTime at;

  factory MemberLocation.fromJson(Map<String, dynamic> j) => MemberLocation(
        userId: (j['userId'] as num).toInt(),
        name: (j['name'] as String?) ?? 'Traveller',
        lat: (j['lat'] as num).toDouble(),
        lng: (j['lng'] as num).toDouble(),
        accuracy: (j['accuracy'] as num?)?.toDouble(),
        at: DateTime.tryParse((j['at'] as String?) ?? '') ?? DateTime.now(),
      );
}
