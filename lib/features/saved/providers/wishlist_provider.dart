import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/remote_sync.dart';
import '../../../core/providers.dart';
import '../../../data/local/local_store.dart';

class WishlistNotifier extends Notifier<Set<String>> with RemoteSync<Set<String>> {
  @override
  Set<String> build() {
    final remote = watchApi();
    if (remote != null) return loadRemote(const {});
    return ref.watch(localStoreProvider).getStringList(LocalStore.wishlistKey).toSet();
  }

  @override
  Future<void> refresh() => safeRefresh((a) => a.wishlistIds());

  bool contains(String id) => state.contains(id);

  /// Optimistic toggle. Returns true when the destination is now saved; the
  /// change is rolled back (and rethrown) if the server rejects it.
  Future<bool> toggle(String id) async {
    final previous = state;
    final next = {...state};
    final added = next.add(id);
    if (!added) next.remove(id);
    state = next;

    final client = api;
    if (client == null) {
      await ref.read(localStoreProvider).setStringList(LocalStore.wishlistKey, next.toList());
      return added;
    }
    try {
      added ? await client.saveDestination(id) : await client.unsaveDestination(id);
    } catch (_) {
      state = previous;
      rethrow;
    }
    return added;
  }
}

final wishlistProvider = NotifierProvider<WishlistNotifier, Set<String>>(WishlistNotifier.new);
