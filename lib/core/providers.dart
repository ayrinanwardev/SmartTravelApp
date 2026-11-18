import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

import '../data/local/local_store.dart';
import '../data/models/destination.dart';
import '../data/models/hotel.dart';
import '../data/repositories/travel_repository.dart';
import '../features/auth/providers/auth_provider.dart';
import 'network/api_client.dart';
import 'network/token_store.dart';
import 'network/wanderly_api.dart';

/// Overridden in `main()` with the real instance once it has loaded.
final sharedPreferencesProvider = Provider<SharedPreferences>(
  (ref) => throw UnimplementedError('sharedPreferencesProvider must be overridden'),
);

final localStoreProvider = Provider<LocalStore>((ref) => LocalStore(ref.watch(sharedPreferencesProvider)));

/// JWT holder backed by encrypted storage. `main()` overrides it with the token
/// loaded at startup; the in-memory default keeps tests simple.
final tokenStoreProvider = Provider<TokenStore>((ref) => TokenStore.memory());

/// The REST client, or `null` in offline demo mode (`--dart-define=USE_API=false`).
/// Notifiers check this to decide between the server and local storage.
final apiProvider = Provider<WanderlyApi?>((ref) {
  if (!ApiConfig.useApi) return null;
  final tokens = ref.watch(tokenStoreProvider);
  final dio = createDio(
    token: () => tokens.token,
    // Expired / invalid token → sign out; the router guard then shows the login screen.
    onUnauthorized: () => ref.read(authProvider.notifier).signOut(),
  );
  return WanderlyApi(dio);
});

final travelRepositoryProvider = Provider<TravelRepository>((ref) {
  final api = ref.watch(apiProvider);
  return api == null ? const MockTravelRepository() : ApiTravelRepository(api);
});

const uuid = Uuid();

final destinationsProvider = FutureProvider<List<Destination>>(
  (ref) => ref.watch(travelRepositoryProvider).getDestinations(),
);

/// Synchronous lookup once destinations have loaded (null while loading / not found).
final destinationByIdProvider = Provider.family<Destination?, String>((ref, id) {
  final list = ref.watch(destinationsProvider).value ?? const [];
  for (final d in list) {
    if (d.id == id) return d;
  }
  return null;
});

final hotelsProvider = FutureProvider.family<List<Hotel>, String?>(
  (ref, destinationId) => ref.watch(travelRepositoryProvider).getHotels(destinationId: destinationId),
);

final hotelByIdProvider = Provider.family<Hotel?, String>((ref, id) {
  final list = ref.watch(hotelsProvider(null)).value ?? const [];
  for (final h in list) {
    if (h.id == id) return h;
  }
  return null;
});
