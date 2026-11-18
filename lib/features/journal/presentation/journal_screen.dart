import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/journal_entry.dart';
import '../providers/journal_provider.dart';

class JournalScreen extends ConsumerStatefulWidget {
  const JournalScreen({super.key});

  @override
  ConsumerState<JournalScreen> createState() => _JournalScreenState();
}

class _JournalScreenState extends ConsumerState<JournalScreen> {
  String? _album;

  @override
  Widget build(BuildContext context) {
    final albums = ref.watch(journalAlbumsProvider);
    final all = ref.watch(journalProvider);
    final entries = _album == null ? all : (albums[_album] ?? const []);

    return Scaffold(
      appBar: AppBar(title: const Text('Travel Journal')),
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'new-memory',
        backgroundColor: AppColors.coral,
        foregroundColor: Colors.white,
        onPressed: () => context.push('/journal/new'),
        icon: const Icon(Icons.add_a_photo_rounded),
        label: const Text('Memory', style: TextStyle(fontWeight: FontWeight.w700)),
      ),
      body: all.isEmpty
          ? EmptyState(
              icon: Icons.auto_stories_rounded,
              title: 'Your journal is empty',
              message: 'Capture photos and stories from your travels.',
              action: 'Write first memory',
              onAction: () => context.push('/journal/new'),
            )
          : ListView(
              padding: const EdgeInsets.only(bottom: 100),
              children: [
                SizedBox(
                  height: 44,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: ChoiceChip(
                          label: Text('All (${all.length})'),
                          selected: _album == null,
                          onSelected: (_) => setState(() => _album = null),
                        ),
                      ),
                      for (final a in albums.keys)
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: ChoiceChip(
                            avatar: const Icon(Icons.photo_album_rounded, size: 16),
                            label: Text('$a (${albums[a]!.length})'),
                            selected: _album == a,
                            onSelected: (_) => setState(() => _album = a),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 12),
                for (final e in entries)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
                    child: _JournalCard(entry: e),
                  ),
              ],
            ),
    );
  }
}

String? _cover(JournalEntry e) => e.photoPaths.isNotEmpty ? e.photoPaths.first : e.coverUrl;

class _JournalCard extends StatelessWidget {
  const _JournalCard({required this.entry});
  final JournalEntry entry;

  @override
  Widget build(BuildContext context) {
    final cover = _cover(entry);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.push('/journal/${entry.id}'),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (cover != null)
              Stack(
                children: [
                  NetImage(cover, height: 180, width: double.infinity),
                  if (entry.photoPaths.length > 1)
                    Positioned(
                      right: 12,
                      top: 12,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(12)),
                        child: Text('+${entry.photoPaths.length - 1}', style: const TextStyle(color: Colors.white)),
                      ),
                    ),
                ],
              ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.location_on_rounded, size: 15, color: AppColors.coral),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Text(entry.location,
                            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: AppColors.coral)),
                      ),
                      Text(Fmt.date(entry.date), style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(entry.title, style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 4),
                  Text(entry.body, maxLines: 2, overflow: TextOverflow.ellipsis),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class JournalEntryScreen extends ConsumerWidget {
  const JournalEntryScreen({super.key, required this.id});
  final String id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final entry = ref.watch(journalProvider).where((e) => e.id == id).firstOrNull;
    if (entry == null) {
      return Scaffold(appBar: AppBar(), body: const EmptyState(icon: Icons.auto_stories, title: 'Memory not found', message: ''));
    }
    final photos = entry.photoPaths.isNotEmpty ? entry.photoPaths : [if (entry.coverUrl != null) entry.coverUrl!];
    return Scaffold(
      appBar: AppBar(
        title: Text(entry.album),
        actions: [
          IconButton(
            tooltip: 'Share',
            icon: const Icon(Icons.ios_share_rounded),
            onPressed: () {
              Clipboard.setData(ClipboardData(
                  text: '${entry.title}\n📍 ${entry.location} · ${Fmt.date(entry.date)}\n\n${entry.body}\n\n— shared from Wanderly'));
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Memory copied — paste it anywhere to share')));
            },
          ),
          IconButton(
            tooltip: 'Delete',
            icon: const Icon(Icons.delete_outline_rounded),
            onPressed: () async {
              final removal = runApi(context, ref.read(journalProvider.notifier).remove(entry.id));
              context.pop();
              await removal;
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 32),
        children: [
          if (photos.isNotEmpty)
            SizedBox(
              height: 260,
              child: PageView(
                children: [
                  for (final p in photos)
                    Padding(
                      padding: const EdgeInsets.only(right: 6),
                      child: NetImage(p, borderRadius: BorderRadius.circular(24)),
                    ),
                ],
              ),
            ),
          const SizedBox(height: 20),
          Text(entry.title, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 8),
          Text('📍 ${entry.location}  ·  ${Fmt.date(entry.date)}', style: const TextStyle(color: AppColors.coral, fontWeight: FontWeight.w600)),
          const SizedBox(height: 16),
          Text(entry.body, style: const TextStyle(fontSize: 16, height: 1.6)),
        ],
      ),
    );
  }
}

class NewJournalEntryScreen extends ConsumerStatefulWidget {
  const NewJournalEntryScreen({super.key});

  @override
  ConsumerState<NewJournalEntryScreen> createState() => _NewJournalEntryScreenState();
}

class _NewJournalEntryScreenState extends ConsumerState<NewJournalEntryScreen> {
  final _title = TextEditingController();
  final _body = TextEditingController();
  final _location = TextEditingController();
  final _album = TextEditingController();
  DateTime _date = DateTime.now();
  final List<String> _photos = [];
  bool _saving = false;

  @override
  void dispose() {
    for (final c in [_title, _body, _location, _album]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _pick(ImageSource source) async {
    final picker = ImagePicker();
    try {
      if (source == ImageSource.gallery) {
        final files = await picker.pickMultiImage(imageQuality: 80);
        setState(() => _photos.addAll(files.map((f) => f.path)));
      } else {
        final f = await picker.pickImage(source: source, imageQuality: 80);
        if (f != null) setState(() => _photos.add(f.path));
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Could not open photos: $e')));
    }
  }

  Future<void> _save() async {
    if (_saving) return;
    if (_title.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Give your memory a title')));
      return;
    }
    setState(() => _saving = true);
    final ok = await runApi(
      context,
      ref
          .read(journalProvider.notifier)
          .add(JournalEntry(
            id: uuid.v4(),
            title: _title.text.trim(),
            body: _body.text.trim(),
            location: _location.text.trim().isEmpty ? 'Somewhere wonderful' : _location.text.trim(),
            date: _date,
            album: _album.text.trim().isEmpty ? 'My Travels' : _album.text.trim(),
            photoPaths: _photos,
          ))
          .then((_) => true),
    );
    if (!mounted) return;
    setState(() => _saving = false);
    if (ok == true) context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final albums = ref.watch(journalAlbumsProvider).keys.toList();
    return Scaffold(
      appBar: AppBar(
        title: const Text('New memory'),
        actions: [TextButton(onPressed: _save, child: const Text('Save', style: TextStyle(fontWeight: FontWeight.w700)))],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          SizedBox(
            height: 110,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: [
                _PickTile(icon: Icons.photo_library_rounded, label: 'Gallery', onTap: () => _pick(ImageSource.gallery)),
                const SizedBox(width: 10),
                _PickTile(icon: Icons.photo_camera_rounded, label: 'Camera', onTap: () => _pick(ImageSource.camera)),
                for (final p in _photos)
                  Padding(
                    padding: const EdgeInsets.only(left: 10),
                    child: Stack(
                      children: [
                        NetImage(p, width: 110, height: 110, borderRadius: BorderRadius.circular(16)),
                        Positioned(
                          top: 4,
                          right: 4,
                          child: GestureDetector(
                            onTap: () => setState(() => _photos.remove(p)),
                            child: const CircleAvatar(radius: 12, backgroundColor: Colors.black54, child: Icon(Icons.close, size: 14, color: Colors.white)),
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          TextField(
            controller: _title,
            textCapitalization: TextCapitalization.sentences,
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
            decoration: const InputDecoration(hintText: 'Title'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _location,
            decoration: const InputDecoration(hintText: 'Location', prefixIcon: Icon(Icons.location_on_rounded)),
          ),
          const SizedBox(height: 12),
          InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: () async {
              final d = await showDatePicker(
                context: context,
                firstDate: DateTime(2000),
                lastDate: DateTime.now().add(const Duration(days: 365)),
                initialDate: _date,
              );
              if (d != null) setState(() => _date = d);
            },
            child: InputDecorator(
              decoration: const InputDecoration(prefixIcon: Icon(Icons.event_rounded)),
              child: Text(Fmt.date(_date)),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _album,
            decoration: const InputDecoration(hintText: 'Album (e.g. Bali 2026)', prefixIcon: Icon(Icons.photo_album_rounded)),
          ),
          if (albums.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 6,
              children: [
                for (final a in albums) ActionChip(label: Text(a), onPressed: () => setState(() => _album.text = a)),
              ],
            ),
          ],
          const SizedBox(height: 12),
          TextField(
            controller: _body,
            minLines: 6,
            maxLines: 14,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(hintText: 'Write about your day…'),
          ),
          const SizedBox(height: 20),
          FilledButton(onPressed: _save, child: const Text('Save memory')),
        ],
      ),
    );
  }
}

class _PickTile extends StatelessWidget {
  const _PickTile({required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        width: 110,
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.border, width: 1.5),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: AppColors.ocean, size: 30),
            const SizedBox(height: 6),
            Text(label, style: const TextStyle(fontWeight: FontWeight.w600)),
          ],
        ),
      ),
    );
  }
}
