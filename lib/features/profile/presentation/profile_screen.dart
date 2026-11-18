import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/widgets/common.dart';
import '../../../data/local/local_store.dart';
import '../../auth/providers/auth_provider.dart';
import '../../hotels/providers/bookings_provider.dart';
import '../../journal/providers/journal_provider.dart';
import '../../saved/providers/wishlist_provider.dart';
import '../../trips/providers/trips_provider.dart';

class _NotificationsNotifier extends Notifier<bool> {
  @override
  bool build() => ref
      .watch(localStoreProvider)
      .getBool(LocalStore.notificationsKey, fallback: true);

  Future<void> set(bool v) async {
    final previous = state;
    state = v;
    await ref.read(localStoreProvider).setBool(LocalStore.notificationsKey, v);
    try {
      await ref.read(apiProvider)?.updateProfile(notificationsEnabled: v);
    } catch (_) {
      state = previous;
      await ref
          .read(localStoreProvider)
          .setBool(LocalStore.notificationsKey, previous);
      rethrow;
    }
  }
}

final _notificationsProvider = NotifierProvider<_NotificationsNotifier, bool>(
  _NotificationsNotifier.new,
);

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  Future<void> _editName(
    BuildContext context,
    WidgetRef ref,
    String current,
  ) async {
    final c = TextEditingController(text: current);
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Your name'),
        content: TextField(
          controller: c,
          autofocus: true,
          textCapitalization: TextCapitalization.words,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, c.text),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    c.dispose();
    if (name != null && name.trim().length >= 2 && context.mounted) {
      await runApi(context, ref.read(authProvider.notifier).updateName(name));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authProvider);
    final trips = ref.watch(tripsProvider).length;
    final saved = ref.watch(wishlistProvider).length;
    final memories = ref.watch(journalProvider).length;
    final bookings = ref.watch(bookingsProvider).length;
    final notifications = ref.watch(_notificationsProvider);

    // The header is navy, so the status bar icons must be light to stay readable.
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light,
      child: Scaffold(
        body: ListView(
          padding: EdgeInsets.zero,
          children: [
            Container(
              padding: EdgeInsets.fromLTRB(
                20,
                MediaQuery.paddingOf(context).top + 24,
                20,
                28,
              ),
              decoration: const BoxDecoration(
                gradient: AppColors.brandGradient,
                borderRadius: BorderRadius.vertical(
                  bottom: Radius.circular(32),
                ),
              ),
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 42,
                    backgroundColor: AppColors.coral,
                    child: Text(
                      user?.initials ?? 'W',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 28,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        user?.name ?? 'Traveller',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      IconButton(
                        onPressed: () =>
                            _editName(context, ref, user?.name ?? ''),
                        icon: const Icon(
                          Icons.edit_rounded,
                          color: Colors.white70,
                          size: 18,
                        ),
                      ),
                    ],
                  ),
                  Text(
                    user?.email ?? '',
                    style: const TextStyle(color: Colors.white70),
                  ),
                  const SizedBox(height: 20),
                  Row(
                    children: [
                      _Stat(value: trips, label: 'Trips'),
                      _Stat(value: saved, label: 'Saved'),
                      _Stat(value: memories, label: 'Memories'),
                      _Stat(value: bookings, label: 'Bookings'),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            _Group(
              title: 'My travel',
              children: [
                _Item(
                  icon: Icons.luggage_rounded,
                  label: 'My trips',
                  onTap: () => context.go('/trips'),
                ),
                _Item(
                  icon: Icons.favorite_rounded,
                  label: 'Saved destinations',
                  onTap: () => context.go('/saved'),
                ),
                _Item(
                  icon: Icons.receipt_long_rounded,
                  label: 'Booking history',
                  onTap: () => context.push('/bookings'),
                ),
                _Item(
                  icon: Icons.auto_stories_rounded,
                  label: 'Travel journal',
                  onTap: () => context.push('/journal'),
                ),
                _Item(
                  icon: Icons.account_balance_wallet_rounded,
                  label: 'Travel budget',
                  onTap: () => context.push('/budget'),
                ),
              ],
            ),
            _Group(
              title: 'Tools',
              children: [
                _Item(
                  icon: Icons.auto_awesome_rounded,
                  label: 'AI travel assistant',
                  onTap: () => context.push('/assistant'),
                ),
                _Item(
                  icon: Icons.checklist_rounded,
                  label: 'Packing checklist',
                  onTap: () => context.push('/packing'),
                ),
                _Item(
                  icon: Icons.currency_exchange_rounded,
                  label: 'Currency converter',
                  onTap: () => context.push('/currency'),
                ),
              ],
            ),
            _Group(
              title: 'Settings',
              children: [
                SwitchListTile(
                  secondary: const Icon(
                    Icons.notifications_rounded,
                    color: AppColors.ocean,
                  ),
                  title: const Text(
                    'Trip reminders & deals',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  value: notifications,
                  activeThumbColor: AppColors.coral,
                  onChanged: (v) => runApi(
                    context,
                    ref.read(_notificationsProvider.notifier).set(v),
                  ),
                ),
                _Item(
                  icon: Icons.info_outline_rounded,
                  label: 'About Wanderly',
                  onTap: () => showAboutDialog(
                    context: context,
                    applicationName: 'Wanderly',
                    applicationVersion: '1.0.0',
                    applicationLegalese: 'Explore Beyond.\nBuilt with Flutter, Riverpod & GoRouter.',
                  ),
                ),
                _Item(
                  icon: Icons.logout_rounded,
                  label: 'Log out',
                  color: AppColors.danger,
                  onTap: () async {
                    await ref.read(authProvider.notifier).signOut();
                    if (context.mounted) context.go('/login');
                  },
                ),
              ],
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat({required this.value, required this.label});
  final int value;
  final String label;

  @override
  Widget build(BuildContext context) => Expanded(
    child: Column(
      children: [
        Text(
          '$value',
          style: const TextStyle(
            color: Colors.white,
            fontSize: 20,
            fontWeight: FontWeight.w800,
          ),
        ),
        Text(
          label,
          style: const TextStyle(color: Colors.white70, fontSize: 12),
        ),
      ],
    ),
  );
}

class _Group extends StatelessWidget {
  const _Group({required this.title, required this.children});
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 4, bottom: 8),
            child: Text(
              title.toUpperCase(),
              style: const TextStyle(
                fontSize: 12,
                letterSpacing: 1.2,
                fontWeight: FontWeight.w700,
                color: AppColors.textSecondary,
              ),
            ),
          ),
          Card(child: Column(children: children)),
        ],
      ),
    );
  }
}

class _Item extends StatelessWidget {
  const _Item({
    required this.icon,
    required this.label,
    required this.onTap,
    this.color = AppColors.ocean,
  });
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Color color;

  @override
  Widget build(BuildContext context) => ListTile(
    leading: Icon(icon, color: color),
    title: Text(
      label,
      style: TextStyle(
        fontWeight: FontWeight.w600,
        color: color == AppColors.danger ? color : null,
      ),
    ),
    trailing: const Icon(
      Icons.chevron_right_rounded,
      color: AppColors.textSecondary,
    ),
    onTap: onTap,
  );
}
