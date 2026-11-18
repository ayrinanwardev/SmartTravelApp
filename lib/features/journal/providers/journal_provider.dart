import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/remote_sync.dart';
import '../../../core/providers.dart';
import '../../../data/local/local_store.dart';
import '../../../data/mock/images.dart';
import '../../../data/models/journal_entry.dart';

class JournalNotifier extends Notifier<List<JournalEntry>> with RemoteSync<List<JournalEntry>> {
  @override
  List<JournalEntry> build() {
    if (watchApi() != null) return loadRemote(const []);
    return ref.watch(localStoreProvider).readList(LocalStore.journalKey, JournalEntry.fromJson) ?? _seed();
  }

  @override
  Future<void> refresh() => safeRefresh((a) => a.journal());

  void _set(List<JournalEntry> list) {
    state = list;
    if (api == null) ref.read(localStoreProvider).writeList(LocalStore.journalKey, list, (e) => e.toJson());
  }

  /// In API mode, uploads each local photo first, then creates the entry with the photo URLs.
  Future<void> add(JournalEntry e) async {
    final client = api;
    var saved = e;
    if (client != null) {
      final urls = <String>[];
      for (final path in e.photoPaths) {
        urls.add(path.startsWith('http') ? path : await client.uploadPhoto(path));
      }
      saved = await client.createJournalEntry(e, urls);
    }
    _set([saved, ...state]..sort((a, b) => b.date.compareTo(a.date)));
  }

  Future<void> remove(String id) async {
    final previous = state;
    _set(state.where((e) => e.id != id).toList());
    final client = api;
    if (client == null) return;
    try {
      await client.deleteJournalEntry(id);
    } catch (_) {
      state = previous;
      rethrow;
    }
  }

  List<JournalEntry> _seed() => [
        JournalEntry(
          id: 'j1',
          title: 'Clouds below our feet',
          body: 'Woke up at 5am in Sajek and the whole valley was a white ocean. Bamboo chicken for dinner, '
              'bonfire with new friends from Chattogram. Already planning to come back in monsoon.',
          location: 'Sajek Valley, Bangladesh',
          date: DateTime(2025, 10, 14),
          album: 'Hill Tracts 2025',
          coverUrl: Img.cloudsMountain,
        ),
        JournalEntry(
          id: 'j2',
          title: 'Boat ride through Ratargul',
          body: 'Green water, trees growing straight out of the river and total silence apart from the oars.',
          location: 'Sylhet, Bangladesh',
          date: DateTime(2025, 7, 22),
          album: 'Sylhet Monsoon',
          coverUrl: Img.forest,
        ),
      ];
}

final journalProvider = NotifierProvider<JournalNotifier, List<JournalEntry>>(JournalNotifier.new);

final journalAlbumsProvider = Provider<Map<String, List<JournalEntry>>>((ref) {
  final map = <String, List<JournalEntry>>{};
  for (final e in ref.watch(journalProvider)) {
    (map[e.album] ??= []).add(e);
  }
  return map;
});
