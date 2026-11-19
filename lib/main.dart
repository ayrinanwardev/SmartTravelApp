import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/app_keys.dart';
import 'core/network/token_store.dart';
import 'core/notifications/local_notifications.dart';
import 'core/notifications/push_service.dart';
import 'core/providers.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';
import 'features/notifications/providers/notifications_provider.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(statusBarColor: Colors.transparent));
  final prefs = await SharedPreferences.getInstance();
  // Reads the JWT from encrypted storage (and migrates any old plain-text copy).
  final tokens = await TokenStore.load(prefs);
  await LocalNotifications.instance.init();
  await PushService.init();
  runApp(
    ProviderScope(
      overrides: [
        sharedPreferencesProvider.overrideWithValue(prefs),
        tokenStoreProvider.overrideWithValue(tokens),
      ],
      child: const WanderlyApp(),
    ),
  );
}

class WanderlyApp extends ConsumerWidget {
  const WanderlyApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Live WebSocket events → app state, and FCM token registration (both no-ops when signed out).
    ref.watch(realtimeSyncProvider);
    ref.watch(pushRegistrationProvider);
    return MaterialApp.router(
      title: 'Wanderly',
      debugShowCheckedModeBanner: false,
      scaffoldMessengerKey: rootMessengerKey,
      theme: AppTheme.light,
      routerConfig: ref.watch(routerProvider),
    );
  }
}
