import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Thin JSON persistence layer on top of SharedPreferences.
class LocalStore {
  LocalStore(this._prefs);

  final SharedPreferences _prefs;

  static const onboardingKey = 'onboarding_done';
  static const userNameKey = 'user_name';
  static const userEmailKey = 'user_email';
  static const wishlistKey = 'wishlist';
  static const tripsKey = 'trips';
  static const expensesKey = 'expenses';
  static const budgetKey = 'budget_total';
  static const journalKey = 'journal';
  static const bookingsKey = 'bookings';
  static const notificationsKey = 'notifications_on';
  /// Legacy plain-text token key (pre-1.1). Tokens now live in TokenStore; kept so clearUserData() wipes old copies.
  static const tokenKey = 'auth_token';

  bool getBool(String key, {bool fallback = false}) => _prefs.getBool(key) ?? fallback;
  Future<void> setBool(String key, bool value) => _prefs.setBool(key, value);

  String? getString(String key) => _prefs.getString(key);
  Future<void> setString(String key, String value) => _prefs.setString(key, value);
  Future<void> remove(String key) => _prefs.remove(key);

  int? getInt(String key) => _prefs.getInt(key);
  Future<void> setInt(String key, int value) => _prefs.setInt(key, value);

  List<String> getStringList(String key) => _prefs.getStringList(key) ?? const [];
  Future<void> setStringList(String key, List<String> value) => _prefs.setStringList(key, value);

  List<T>? readList<T>(String key, T Function(Map<String, dynamic>) fromJson) {
    final raw = _prefs.getString(key);
    if (raw == null) return null;
    try {
      return (jsonDecode(raw) as List).map((e) => fromJson(e as Map<String, dynamic>)).toList();
    } catch (_) {
      return null;
    }
  }

  Future<void> writeList<T>(String key, List<T> items, Map<String, dynamic> Function(T) toJson) =>
      _prefs.setString(key, jsonEncode(items.map(toJson).toList()));

  Future<void> clearUserData() async {
    for (final k in [tokenKey, userNameKey, userEmailKey, wishlistKey, tripsKey, expensesKey, budgetKey, journalKey, bookingsKey]) {
      await _prefs.remove(k);
    }
  }
}
