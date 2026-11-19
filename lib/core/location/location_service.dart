import 'dart:math' as math;

import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

/// Why we couldn't get a location, phrased for the user.
class LocationUnavailable implements Exception {
  const LocationUnavailable(this.message, {this.canOpenSettings = false});

  final String message;
  final bool canOpenSettings;

  @override
  String toString() => message;
}

/// Thin wrapper over geolocator: permission flow, one-off fix and a live stream.
abstract final class LocationService {
  /// Asks for permission if needed; throws [LocationUnavailable] with a readable reason.
  static Future<void> ensurePermission() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      throw const LocationUnavailable('Location is turned off. Turn on GPS to see where you are.', canOpenSettings: true);
    }
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied) {
      throw const LocationUnavailable('Location permission was denied.');
    }
    if (permission == LocationPermission.deniedForever) {
      throw const LocationUnavailable('Location permission is blocked. Allow it in Settings.', canOpenSettings: true);
    }
  }

  static Future<LatLng> current() async {
    await ensurePermission();
    final p = await Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, timeLimit: Duration(seconds: 15)),
    );
    return LatLng(p.latitude, p.longitude);
  }

  /// Positions while moving; [distanceFilterMeters] avoids flooding the server when standing still.
  static Stream<Position> watch({int distanceFilterMeters = 10}) => Geolocator.getPositionStream(
        locationSettings: LocationSettings(accuracy: LocationAccuracy.high, distanceFilter: distanceFilterMeters),
      );

  static Future<void> openSettings() => Geolocator.openLocationSettings();

  /// Great-circle distance in kilometres.
  static double distanceKm(LatLng a, LatLng b) {
    const r = 6371.0;
    double rad(double d) => d * math.pi / 180;
    final dLat = rad(b.latitude - a.latitude);
    final dLng = rad(b.longitude - a.longitude);
    final h = math.pow(math.sin(dLat / 2), 2) +
        math.cos(rad(a.latitude)) * math.cos(rad(b.latitude)) * math.pow(math.sin(dLng / 2), 2);
    return 2 * r * math.asin(math.sqrt(h));
  }

  static String formatDistance(double km) => km < 1 ? '${(km * 1000).round()} m' : '${km.toStringAsFixed(km < 10 ? 1 : 0)} km';
}
