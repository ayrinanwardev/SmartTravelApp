import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/providers/auth_provider.dart';
import '../providers.dart';
import 'wanderly_api.dart';

/// Shared plumbing for notifiers whose data lives on the server when the API
/// is enabled. Usage inside `build()`:
///
/// ```dart
/// final api = watchApi();          // null → offline demo mode
/// if (api != null) return loadRemote(const []);
/// ```
mixin RemoteSync<T> on Notifier<T> {
  /// Watches the API client and the signed-in user, so the notifier rebuilds
  /// (and re-fetches) on sign-in / sign-out.
  WanderlyApi? watchApi() {
    final api = ref.watch(apiProvider);
    if (api != null) ref.watch(authProvider);
    return api;
  }

  WanderlyApi? get api => ref.read(apiProvider);

  bool get signedIn => ref.read(authProvider) != null;

  /// Returns [initial] now and replaces it with the server's data once it arrives.
  T loadRemote(T initial) {
    if (ref.read(authProvider) == null) return initial;
    Future.microtask(() => refresh());
    return initial;
  }

  /// Fetches fresh data; implemented by each notifier. Errors are logged, not thrown,
  /// because nothing on screen is waiting for a background refresh.
  Future<void> refresh();

  Future<void> safeRefresh(Future<T> Function(WanderlyApi api) fetch) async {
    final client = api;
    if (client == null || !signedIn) return;
    try {
      final value = await fetch(client);
      if (ref.mounted) state = value;
    } catch (e) {
      debugPrint('[sync] ${runtimeType.toString()} refresh failed: $e');
    }
  }
}
