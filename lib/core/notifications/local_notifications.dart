import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// System notifications (status bar / lock screen) for events that arrive while the app
/// is open or in the background: payment confirmed, a companion joined, budget alerts…
/// Push notifications for a *closed* app come from FCM (see push_service.dart).
class LocalNotifications {
  LocalNotifications._();

  static final instance = LocalNotifications._();

  /// Must match the backend's FCM channel (PushService.ANDROID_CHANNEL).
  static const channelId = 'wanderly_updates';

  final _plugin = FlutterLocalNotificationsPlugin();
  bool _ready = false;
  int _nextId = 1;

  Future<void> init() async {
    if (_ready || kIsWeb) return;
    try {
      await _plugin.initialize(
        settings: const InitializationSettings(
          android: AndroidInitializationSettings('@mipmap/ic_launcher'),
          iOS: DarwinInitializationSettings(),
        ),
      );
      final android = _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
      await android?.createNotificationChannel(const AndroidNotificationChannel(
        channelId,
        'Trip & booking updates',
        description: 'Payments, companions joining, itinerary changes and budget alerts',
        importance: Importance.high,
      ));
      _ready = true;
    } catch (e) {
      debugPrint('[notifications] init failed: $e');
    }
  }

  /// Asks for the Android 13+ / iOS notification permission. Safe to call repeatedly.
  Future<void> requestPermission() async {
    if (!_ready) return;
    await _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
        ?.requestNotificationsPermission();
    await _plugin.resolvePlatformSpecificImplementation<IOSFlutterLocalNotificationsPlugin>()
        ?.requestPermissions(alert: true, badge: true, sound: true);
  }

  Future<void> show(String title, String body) async {
    if (!_ready) return;
    try {
      await _plugin.show(
        id: _nextId++,
        title: title,
        body: body,
        notificationDetails: const NotificationDetails(
          android: AndroidNotificationDetails(
            channelId,
            'Trip & booking updates',
            importance: Importance.high,
            priority: Priority.high,
          ),
          iOS: DarwinNotificationDetails(),
        ),
      );
    } catch (e) {
      debugPrint('[notifications] show failed: $e');
    }
  }
}
