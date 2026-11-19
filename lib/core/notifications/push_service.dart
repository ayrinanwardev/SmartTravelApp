import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/providers/auth_provider.dart';
import '../providers.dart';

/// Firebase Cloud Messaging: delivers notifications when the app is closed.
///
/// Works once `android/app/google-services.json` (from your Firebase project) is added and
/// the backend has `FIREBASE_CREDENTIALS_BASE64`. Without them the app runs normally and
/// real-time updates still arrive over the WebSocket while it is open.
abstract final class PushService {
  static bool _available = false;

  static bool get available => _available;

  static Future<void> init() async {
    if (kIsWeb) return;
    try {
      await Firebase.initializeApp();
      _available = true;
      debugPrint('[push] Firebase ready');
    } catch (e) {
      debugPrint('[push] disabled — add google-services.json to enable FCM ($e)');
    }
  }
}

/// Registers this device's FCM token with the backend while a user is signed in.
final pushRegistrationProvider = Provider<void>((ref) {
  final api = ref.watch(apiProvider);
  final user = ref.watch(authProvider);
  if (!PushService.available || api == null || user == null) return;

  final messaging = FirebaseMessaging.instance;
  final platform = defaultTargetPlatform == TargetPlatform.iOS ? 'ios' : 'android';

  Future<void> register(String? token) async {
    if (token == null) return;
    try {
      await api.registerDevice(token, platform);
    } catch (e) {
      debugPrint('[push] could not register token: $e');
    }
  }

  () async {
    try {
      await messaging.requestPermission();
      // Registration tokens (not the newer installation IDs from register()/onRegistered) are what the
      // backend's Firebase Admin SDK multicast send expects. Revisit when migrating the server side.
      // ignore: deprecated_member_use
      await register(await messaging.getToken());
    } catch (e) {
      debugPrint('[push] token unavailable: $e');
    }
  }();
  // ignore: deprecated_member_use
  final sub = messaging.onTokenRefresh.listen(register);
  ref.onDispose(sub.cancel);
  // Foreground messages are not shown here: the same event already arrives over the
  // WebSocket (notification.created), which avoids duplicate alerts.
});
