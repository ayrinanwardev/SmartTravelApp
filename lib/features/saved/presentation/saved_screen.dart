import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/destination_cards.dart';
import '../providers/wishlist_provider.dart';

class SavedScreen extends ConsumerWidget {
  const SavedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ids = ref.watch(wishlistProvider);
    final all = ref.watch(destinationsProvider).value ?? const [];
    final saved = all.where((d) => ids.contains(d.id)).toList();

    return Scaffold(
      appBar: AppBar(title: const Text('Saved'), toolbarHeight: 64),
      body: saved.isEmpty
          ? EmptyState(
              icon: Icons.favorite_rounded,
              title: 'No saved places yet',
              message: 'Tap the heart on any destination to build your wishlist.',
              action: 'Explore destinations',
              onAction: () => context.go('/explore'),
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
              itemCount: saved.length,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (_, i) => DestinationTile(saved[i]),
            ),
    );
  }
}
