import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:smart_travel_app/core/network/token_store.dart';
import 'package:smart_travel_app/core/network/wanderly_api.dart';
import 'package:smart_travel_app/core/providers.dart';
import 'package:smart_travel_app/data/local/local_store.dart';
import 'package:smart_travel_app/features/auth/providers/auth_provider.dart';

void main() {
  Future<(ProviderContainer, TokenStore)> signedInContainer({String? token}) async {
    SharedPreferences.setMockInitialValues({
      LocalStore.userNameKey: 'Demo Traveller',
      LocalStore.userEmailKey: 'demo@wanderly.app',
    });
    final prefs = await SharedPreferences.getInstance();
    final tokens = TokenStore.memory(token);
    final c = ProviderContainer(overrides: [
      sharedPreferencesProvider.overrideWithValue(prefs),
      tokenStoreProvider.overrideWithValue(tokens),
      // A client that is never called; it only switches the notifiers into API mode.
      apiProvider.overrideWithValue(WanderlyApi(Dio())),
    ]);
    addTearDown(c.dispose);
    return (c, tokens);
  }

  test('user counts as signed in only when a token is stored', () async {
    final (withToken, _) = await signedInContainer(token: 'jwt-abc');
    expect(withToken.read(authProvider)?.email, 'demo@wanderly.app');

    final (withoutToken, _) = await signedInContainer();
    expect(withoutToken.read(authProvider), isNull);
  });

  test('sign-out wipes the token and cached profile', () async {
    final (c, tokens) = await signedInContainer(token: 'jwt-abc');
    await c.read(authProvider.notifier).signOut();

    expect(tokens.token, isNull);
    expect(c.read(authProvider), isNull);
    expect(c.read(localStoreProvider).getString(LocalStore.userEmailKey), isNull);
  });
}
