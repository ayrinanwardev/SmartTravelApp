import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/wanderly_api.dart';
import '../../../core/providers.dart';
import '../../../data/local/local_store.dart';

class UserProfile {
  const UserProfile({required this.name, required this.email});
  final String name;
  final String email;

  String get firstName => name.split(' ').first;
  String get initials {
    final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
    if (parts.isEmpty) return '?';
    return parts.take(2).map((p) => p[0].toUpperCase()).join();
  }
}

/// Signed-in user. With the API on, sign-in is a real JWT login against
/// `/api/auth/*` and the token is kept in encrypted storage ([TokenStore]); in offline demo mode
/// the profile is only stored on the device.
class AuthNotifier extends Notifier<UserProfile?> {
  LocalStore get _store => ref.read(localStoreProvider);
  WanderlyApi? get _api => ref.read(apiProvider);

  @override
  UserProfile? build() {
    final store = ref.watch(localStoreProvider);
    final name = store.getString(LocalStore.userNameKey);
    final email = store.getString(LocalStore.userEmailKey);
    if (name == null || email == null) return null;
    if (ref.watch(apiProvider) != null && !ref.watch(tokenStoreProvider).hasToken) return null;
    return UserProfile(name: name, email: email);
  }

  Future<void> login({required String email, required String password}) async {
    final api = _api;
    if (api == null) return _saveLocal(_nameFromEmail(email), email);
    await _saveSession(await api.login(email.trim(), password));
  }

  Future<void> register({required String name, required String email, required String password}) async {
    final api = _api;
    if (api == null) return _saveLocal(name, email);
    await _saveSession(await api.register(name.trim(), email.trim(), password));
  }

  Future<void> updateName(String name) async {
    final current = state;
    if (current == null) return;
    await _api?.updateProfile(name: name.trim());
    await _store.setString(LocalStore.userNameKey, name.trim());
    state = UserProfile(name: name.trim(), email: current.email);
  }

  Future<void> signOut() async {
    if (_api != null) {
      // Server is the source of truth: drop the token and every cached copy of user data.
      await ref.read(tokenStoreProvider).clear();
      await _store.clearUserData();
    } else {
      await _store.remove(LocalStore.userNameKey);
      await _store.remove(LocalStore.userEmailKey);
    }
    state = null;
  }

  Future<void> _saveSession(AuthResult r) async {
    await ref.read(tokenStoreProvider).save(r.token);
    await _store.setInt(LocalStore.budgetKey, r.budgetTotal);
    await _store.setBool(LocalStore.notificationsKey, r.notificationsEnabled);
    await _saveLocal(r.name, r.email);
  }

  Future<void> _saveLocal(String name, String email) async {
    await _store.setString(LocalStore.userNameKey, name.trim());
    await _store.setString(LocalStore.userEmailKey, email.trim());
    state = UserProfile(name: name.trim(), email: email.trim());
  }

  static String _nameFromEmail(String email) {
    final local = email.split('@').first.replaceAll(RegExp(r'[._\d]+'), ' ').trim();
    if (local.isEmpty) return 'Traveller';
    return local.split(' ').where((w) => w.isNotEmpty).map((w) => w[0].toUpperCase() + w.substring(1)).join(' ');
  }
}

final authProvider = NotifierProvider<AuthNotifier, UserProfile?>(AuthNotifier.new);

class OnboardingNotifier extends Notifier<bool> {
  @override
  bool build() => ref.watch(localStoreProvider).getBool(LocalStore.onboardingKey);

  Future<void> complete() async {
    await ref.read(localStoreProvider).setBool(LocalStore.onboardingKey, true);
    state = true;
  }
}

final onboardingDoneProvider = NotifierProvider<OnboardingNotifier, bool>(OnboardingNotifier.new);
