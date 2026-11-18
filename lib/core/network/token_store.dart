import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Holds the JWT access token.
///
/// The token is persisted with [FlutterSecureStorage] (Android Keystore / iOS Keychain),
/// never in plain-text SharedPreferences. It is read once at startup and cached in
/// memory, because Dio's interceptor needs it synchronously on every request.
class TokenStore {
  TokenStore._(this._secure, this._token);

  /// In-memory only — for tests and offline demo mode.
  TokenStore.memory([this._token]) : _secure = null;

  static const _key = 'wanderly_jwt';

  /// Where versions before 1.1 kept the token (plain text) — migrated on first launch.
  static const legacyPrefsKey = 'auth_token';

  final FlutterSecureStorage? _secure;
  String? _token;

  String? get token => _token;
  bool get hasToken => _token != null;

  static Future<TokenStore> load(SharedPreferences prefs) async {
    const secure = FlutterSecureStorage();
    String? token;
    try {
      token = await secure.read(key: _key);
      final legacy = prefs.getString(legacyPrefsKey);
      if (legacy != null) {
        token ??= legacy;
        await secure.write(key: _key, value: token);
        await prefs.remove(legacyPrefsKey);
      }
    } catch (e) {
      // A corrupted keystore (e.g. after a device restore) must not crash the app:
      // treat it as signed out.
      debugPrint('[auth] secure storage unavailable: $e');
      token = null;
    }
    return TokenStore._(secure, token);
  }

  Future<void> save(String token) async {
    _token = token;
    await _secure?.write(key: _key, value: token);
  }

  Future<void> clear() async {
    _token = null;
    try {
      await _secure?.delete(key: _key);
    } catch (e) {
      debugPrint('[auth] could not delete token: $e');
    }
  }
}
