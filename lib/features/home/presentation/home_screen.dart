import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/destination_cards.dart';
import '../../../data/models/destination.dart';
import '../../auth/providers/auth_provider.dart';
import '../../saved/providers/wishlist_provider.dart';
import '../../notifications/presentation/notifications_screen.dart';
import '../../trips/providers/trips_provider.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  String _greeting() {
    final h = DateTime.now().hour;
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authProvider);
    final destinations = ref.watch(destinationsProvider);
    final text = Theme.of(context).textTheme;

    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: RefreshIndicator(
          onRefresh: () => ref.refresh(destinationsProvider.future),
          child: CustomScrollView(
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
                  child: Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('${_greeting()}, ${user?.firstName ?? 'traveller'} 👋', style: text.bodyMedium),
                            const SizedBox(height: 4),
                            Text('Where to next?', style: text.headlineMedium),
                          ],
                        ),
                      ),
                      const NotificationBell(),
                      const SizedBox(width: 10),
                      GestureDetector(
                        onTap: () => context.go('/profile'),
                        child: CircleAvatar(
                          radius: 24,
                          backgroundColor: AppColors.navy,
                          child: Text(user?.initials ?? 'W',
                              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700)),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
                  child: GestureDetector(
                    onTap: () => context.go('/explore?focus=1'),
                    child: AbsorbPointer(
                      child: TextField(
                        decoration: InputDecoration(
                          hintText: 'Search destinations, cities, countries',
                          prefixIcon: const Icon(Icons.search_rounded),
                          suffixIcon: Container(
                            margin: const EdgeInsets.all(6),
                            decoration: BoxDecoration(color: AppColors.navy, borderRadius: BorderRadius.circular(12)),
                            child: const Icon(Icons.tune_rounded, color: Colors.white, size: 20),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              const SliverToBoxAdapter(child: _QuickActions()),
              SliverToBoxAdapter(
                child: SizedBox(
                  height: 44,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 20),
                    children: [
                      for (final c in DestinationCategory.values)
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: ActionChip(
                            label: Text('${c.emoji}  ${c.label}'),
                            onPressed: () => context.go('/explore?category=${c.name}'),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
              ...destinations.when(
                loading: () => [
                  const SliverToBoxAdapter(
                    child: Padding(padding: EdgeInsets.all(60), child: Center(child: CircularProgressIndicator())),
                  ),
                ],
                error: (e, _) => [
                  SliverToBoxAdapter(
                    child: EmptyState(
                      icon: Icons.cloud_off_rounded,
                      title: 'Could not load destinations',
                      message: '$e',
                      action: 'Retry',
                      onAction: () => ref.invalidate(destinationsProvider),
                    ),
                  ),
                ],
                data: (list) => _content(context, ref, list),
              ),
              const SliverToBoxAdapter(child: SizedBox(height: 100)),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _content(BuildContext context, WidgetRef ref, List<Destination> all) {
    final popular = all.where((d) => d.isPopular).toList();
    final trending = all.where((d) => d.isTrending).toList();
    final saved = ref.watch(wishlistProvider);
    final savedCats = all.where((d) => saved.contains(d.id)).map((d) => d.category).toSet();
    final recommended = (savedCats.isEmpty
            ? ([...all]..sort((a, b) => b.rating.compareTo(a.rating)))
            : all.where((d) => savedCats.contains(d.category) && !saved.contains(d.id)).toList())
        .take(4)
        .toList();
    final upcoming = ref.watch(tripsProvider).where((t) => t.isUpcoming).toList();

    return [
      const SliverToBoxAdapter(child: SectionHeader('Popular destinations', padding: EdgeInsets.fromLTRB(20, 24, 12, 4))),
      SliverToBoxAdapter(
        child: SizedBox(
          height: 340,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 10),
            itemCount: popular.length,
            separatorBuilder: (_, _) => const SizedBox(width: 16),
            itemBuilder: (_, i) => DestinationHeroCard(popular[i]),
          ),
        ),
      ),
      if (upcoming.isNotEmpty) ...[
        SectionHeader('Your next adventure', action: 'All trips', onAction: () => context.go('/trips')).sliver,
        SliverToBoxAdapter(child: _UpcomingTripCard(tripId: upcoming.first.id)),
      ],
      SectionHeader('Trending now 🔥', action: 'See all', onAction: () => context.go('/explore')).sliver,
      SliverToBoxAdapter(
        child: SizedBox(
          height: 200,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 20),
            itemCount: trending.length,
            separatorBuilder: (_, _) => const SizedBox(width: 14),
            itemBuilder: (_, i) => DestinationHeroCard(trending[i], width: 160, height: 200),
          ),
        ),
      ),
      SectionHeader(savedCats.isEmpty ? 'Recommended for you' : 'Because you saved similar places').sliver,
      SliverList.separated(
        itemCount: recommended.length,
        separatorBuilder: (_, _) => const SizedBox(height: 12),
        itemBuilder: (_, i) =>
            Padding(padding: const EdgeInsets.symmetric(horizontal: 20), child: DestinationTile(recommended[i])),
      ),
    ];
  }
}

extension on Widget {
  Widget get sliver => SliverToBoxAdapter(child: this);
}

class _QuickActions extends StatelessWidget {
  const _QuickActions();

  @override
  Widget build(BuildContext context) {
    final items = [
      (Icons.flight_takeoff_rounded, 'Flights', AppColors.ocean, '/flights'),
      (Icons.hotel_rounded, 'Hotels', const Color(0xFF8B5CF6), '/hotels'),
      (Icons.auto_awesome_rounded, 'AI Planner', AppColors.coral, '/assistant'),
      (Icons.account_balance_wallet_rounded, 'Budget', AppColors.success, '/budget'),
      (Icons.currency_exchange_rounded, 'Currency', AppColors.warning, '/currency'),
      (Icons.map_rounded, 'Map', AppColors.ocean, '/map'),
      (Icons.checklist_rounded, 'Packing', const Color(0xFF0EA5E9), '/packing'),
    ];
    return SizedBox(
      height: 110,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
        itemCount: items.length,
        separatorBuilder: (_, _) => const SizedBox(width: 18),
        itemBuilder: (_, i) {
          final (icon, label, color, route) = items[i];
          return InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: () => context.push(route),
            child: Column(
              children: [
                IconBubble(icon: icon, color: color, size: 52),
                const SizedBox(height: 6),
                Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _UpcomingTripCard extends ConsumerWidget {
  const _UpcomingTripCard({required this.tripId});
  final String tripId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trip = ref.watch(tripByIdProvider(tripId));
    if (trip == null) return const SizedBox.shrink();
    final daysLeft = trip.startDate.difference(DateTime.now()).inDays;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20),
      child: InkWell(
        borderRadius: BorderRadius.circular(24),
        onTap: () => context.push('/trip/${trip.id}'),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(gradient: AppColors.brandGradient, borderRadius: BorderRadius.circular(24)),
          child: Row(
            children: [
              NetImage(trip.imageUrl, width: 84, height: 84, borderRadius: BorderRadius.circular(18)),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(trip.title,
                        style: const TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.w700),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 4),
                    Text(Fmt.range(trip.startDate, trip.endDate), style: const TextStyle(color: Colors.white70, fontSize: 13)),
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(color: AppColors.coral, borderRadius: BorderRadius.circular(20)),
                      child: Text(
                        daysLeft <= 0 ? 'Happening now' : 'In $daysLeft days',
                        style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w700),
                      ),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right_rounded, color: Colors.white),
            ],
          ),
        ),
      ),
    );
  }
}
