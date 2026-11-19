import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/app_keys.dart';
import '../../../core/network/realtime_client.dart';
import '../../../core/network/remote_sync.dart';
import '../../../core/notifications/local_notifications.dart';
import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../data/models/hotel.dart';
import '../../../data/models/notification.dart';
import '../../auth/providers/auth_provider.dart';
import '../../hotels/providers/bookings_provider.dart';
import '../../trips/providers/trips_provider.dart';

/// The user's notifications (server-backed; empty in offline demo mode).
class NotificationsNotifier extends Notifier<List<AppNotification>> with RemoteSync<List<AppNotification>> {
  @override
  List<AppNotification> build() {
    if (watchApi() != null) return loadRemote(const []);
    return const [];
  }

  @override
  Future<void> refresh() => safeRefresh((a) => a.notifications());

  /// A notification pushed live over the WebSocket.
  void add(AppNotification n) {
    if (state.any((x) => x.id == n.id)) return;
    state = [n, ...state];
  }

  Future<void> markRead(int id) async {
    state = [for (final n in state) n.id == id ? n.markRead() : n];
    await api?.markNotificationRead(id);
  }

  Future<void> markAllRead() async {
    state = [for (final n in state) n.markRead()];
    await api?.markAllNotificationsRead();
  }
}

final notificationsProvider =
    NotifierProvider<NotificationsNotifier, List<AppNotification>>(NotificationsNotifier.new);

final unreadNotificationsProvider = Provider<int>((ref) => ref.watch(notificationsProvider).where((n) => !n.read).length);

/// The live WebSocket, open while a user is signed in (null in offline mode or signed out).
final realtimeProvider = Provider<RealtimeClient?>((ref) {
  final api = ref.watch(apiProvider);
  final user = ref.watch(authProvider);
  if (api == null || user == null) return null;
  final tokens = ref.watch(tokenStoreProvider);
  // Signed in: a good moment to ask for the Android 13+/iOS notification permission.
  LocalNotifications.instance.requestPermission();
  final client = RealtimeClient(baseUrl: api.baseUrl, token: () => tokens.token ?? '')..start();
  ref.onDispose(client.dispose);
  return client;
});

/// Applies server events to app state. Watched once by the app root.
final realtimeSyncProvider = Provider<void>((ref) {
  final client = ref.watch(realtimeProvider);
  if (client == null) return;
  final sub = client.events.listen((e) {
    switch (e.type) {
      case 'notification.created':
        final n = AppNotification.fromJson(e.data);
        ref.read(notificationsProvider.notifier).add(n);
        _announce(n);
      case 'trip.updated' || 'trip.deleted' || 'trip.member_joined':
        // A companion (or this user on another device) changed a shared trip.
        ref.read(tripsProvider.notifier).refresh();
      case 'booking.updated':
        ref.read(bookingsProvider.notifier).upsert(Booking.fromJson(e.data));
    }
  });
  ref.onDispose(sub.cancel);
});

/// In the foreground: a snackbar. In the background: a system notification.
void _announce(AppNotification n) {
  final foreground = WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
  if (!foreground) {
    LocalNotifications.instance.show(n.title, n.message);
    return;
  }
  rootMessengerKey.currentState
    ?..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: Row(
        children: [
          Icon(_iconFor(n.type), color: Colors.white, size: 20),
          const SizedBox(width: 10),
          Expanded(child: Text(n.title, maxLines: 2, overflow: TextOverflow.ellipsis)),
        ],
      ),
      backgroundColor: AppColors.navy,
      duration: const Duration(seconds: 4),
    ));
}

IconData _iconFor(String type) => switch (type) {
      'booking' => Icons.hotel_rounded,
      'trip' => Icons.group_rounded,
      'budget' => Icons.account_balance_wallet_rounded,
      _ => Icons.notifications_rounded,
    };

IconData notificationIcon(String type) => _iconFor(type);
