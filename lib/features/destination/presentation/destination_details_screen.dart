import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/destination.dart';

class DestinationDetailsScreen extends ConsumerStatefulWidget {
  const DestinationDetailsScreen({super.key, required this.id});
  final String id;

  @override
  ConsumerState<DestinationDetailsScreen> createState() =>
      _DestinationDetailsScreenState();
}

class _DestinationDetailsScreenState
    extends ConsumerState<DestinationDetailsScreen> {
  int _photo = 0;

  @override
  Widget build(BuildContext context) {
    final loading = ref.watch(destinationsProvider).isLoading;
    final d = ref.watch(destinationByIdProvider(widget.id));
    if (d == null) {
      return Scaffold(
        appBar: AppBar(),
        body: loading
            ? const Center(child: CircularProgressIndicator())
            : const EmptyState(
                icon: Icons.travel_explore,
                title: 'Destination not found',
                message: 'It may have been removed.',
              ),
      );
    }
    final text = Theme.of(context).textTheme;

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 420,
            pinned: true,
            stretch: true,
            backgroundColor: AppColors.navy,
            foregroundColor: Colors.white,
            leading: Padding(
              padding: const EdgeInsets.all(6),
              child: CircleIconButton(
                icon: Icons.arrow_back_rounded,
                onPressed: () => context.pop(),
              ),
            ),
            actions: [
              Padding(
                padding: const EdgeInsets.only(right: 12),
                child: SaveButton(d.id, size: 44),
              ),
            ],
            flexibleSpace: FlexibleSpaceBar(
              stretchModes: const [StretchMode.zoomBackground],
              background: Stack(
                fit: StackFit.expand,
                children: [
                  PageView.builder(
                    itemCount: d.gallery.length,
                    onPageChanged: (i) => setState(() => _photo = i),
                    itemBuilder: (_, i) => NetImage(d.gallery[i]),
                  ),
                  const IgnorePointer(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: AppColors.heroGradient,
                      ),
                    ),
                  ),
                  Positioned(
                    left: 20,
                    right: 20,
                    bottom: 24,
                    child: IgnorePointer(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              for (var i = 0; i < d.gallery.length; i++)
                                AnimatedContainer(
                                  duration: const Duration(milliseconds: 200),
                                  margin: const EdgeInsets.only(right: 5),
                                  height: 6,
                                  width: i == _photo ? 22 : 6,
                                  decoration: BoxDecoration(
                                    color: i == _photo
                                        ? Colors.white
                                        : Colors.white54,
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                ),
                            ],
                          ),
                          const SizedBox(height: 14),
                          Text(
                            d.name,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 34,
                              fontWeight: FontWeight.w800,
                              letterSpacing: -1,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Row(
                            children: [
                              const Icon(
                                Icons.location_on_rounded,
                                color: Colors.white70,
                                size: 18,
                              ),
                              const SizedBox(width: 4),
                              Text(
                                d.country,
                                style: const TextStyle(
                                  color: Colors.white70,
                                  fontSize: 15,
                                ),
                              ),
                              const SizedBox(width: 12),
                              RatingBadge(
                                d.rating,
                                dark: true,
                                count: d.reviewCount,
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
            sliver: SliverList.list(
              children: [
                Text(
                  d.tagline,
                  style: text.titleMedium?.copyWith(color: AppColors.ocean),
                ),
                const SizedBox(height: 16),
                IntrinsicHeight(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Expanded(
                        child: _InfoTile(
                          icon: Icons.calendar_month_rounded,
                          label: 'Best time',
                          value: d.bestTime,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: _InfoTile(
                          icon: Icons.thermostat_rounded,
                          label: 'Avg temp',
                          value: '${d.avgTempC}°C',
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: _InfoTile(
                          icon: Icons.category_rounded,
                          label: 'Type',
                          value: d.category.label,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
                Text('About', style: text.titleLarge),
                const SizedBox(height: 8),
                Text(
                  d.description,
                  style: text.bodyMedium?.copyWith(fontSize: 15),
                ),
                const SizedBox(height: 20),
                Text('Highlights', style: text.titleLarge),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final h in d.highlights)
                      Chip(
                        avatar: const Icon(
                          Icons.check_circle_rounded,
                          color: AppColors.success,
                          size: 18,
                        ),
                        label: Text(h),
                      ),
                  ],
                ),
                const SizedBox(height: 20),
                _WeatherCard(destination: d),
                const SizedBox(height: 20),
                _CostCard(destination: d),
              ],
            ),
          ),
          const SliverToBoxAdapter(child: SectionHeader('Popular attractions')),
          SliverToBoxAdapter(
            child: SizedBox(
              height: 210,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 20),
                itemCount: d.attractions.length,
                separatorBuilder: (_, _) => const SizedBox(width: 12),
                itemBuilder: (_, i) => _AttractionCard(d.attractions[i]),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: SectionHeader(
              'Where to stay',
              action: 'View hotels',
              onAction: () => context.push('/hotels?dest=${d.id}'),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Card(
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 6,
                  ),
                  leading: const IconBubble(
                    icon: Icons.hotel_rounded,
                    color: Color(0xFF8B5CF6),
                  ),
                  title: Text(
                    'Hotels in ${d.name}',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  subtitle: Text(
                    'From ${Fmt.bdt(d.cost.hotelPerNight)} / night',
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => context.push('/hotels?dest=${d.id}'),
                ),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: SectionHeader('Reviews (${d.reviewCount})'),
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            sliver: SliverList.separated(
              itemCount: d.reviews.length,
              separatorBuilder: (_, _) => const SizedBox(height: 10),
              itemBuilder: (_, i) => _ReviewCard(d.reviews[i]),
            ),
          ),
          const SliverToBoxAdapter(child: SizedBox(height: 120)),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
          decoration: const BoxDecoration(
            color: AppColors.surface,
            boxShadow: [
              BoxShadow(
                color: Color(0x14000000),
                blurRadius: 20,
                offset: Offset(0, -4),
              ),
            ],
          ),
          child: Row(
            children: [
              Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Est. 3 days',
                    style: text.bodySmall?.copyWith(
                      color: AppColors.textSecondary,
                    ),
                  ),
                  Text(
                    Fmt.bdt(d.typicalTripCost),
                    style: text.titleLarge?.copyWith(color: AppColors.navy),
                  ),
                ],
              ),
              const SizedBox(width: 20),
              Expanded(
                child: FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.coral,
                  ),
                  onPressed: () => context.push('/plan?dest=${d.id}'),
                  icon: const Icon(Icons.edit_calendar_rounded),
                  label: const Text('Plan Your Trip'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _InfoTile extends StatelessWidget {
  const _InfoTile({
    required this.icon,
    required this.label,
    required this.value,
  });
  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: AppColors.coral, size: 22),
          const SizedBox(height: 8),
          Text(
            label,
            style: const TextStyle(
              fontSize: 11,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            value,
            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}

/// "Smart weather" — simulated forecast with activity suggestions.
class _WeatherCard extends StatelessWidget {
  const _WeatherCard({required this.destination});
  final Destination destination;

  @override
  Widget build(BuildContext context) {
    final d = destination;
    final seed = d.id.codeUnits.fold(0, (a, b) => a + b);
    final now = DateTime.now();
    final forecast = List.generate(5, (i) {
      final temp = d.avgTempC + ((seed + i * 5) % 7) - 3;
      final rainy = (seed + i * 3) % 5 == 0;
      final cloudy = !rainy && (seed + i) % 3 == 0;
      return (
        now.add(Duration(days: i)),
        temp,
        rainy
            ? Icons.umbrella_rounded
            : cloudy
            ? Icons.cloud_rounded
            : Icons.wb_sunny_rounded,
        rainy,
      );
    });
    final rainyDays = forecast.where((f) => f.$4).length;
    final suggestion = rainyDays >= 2
        ? 'Rain expected on $rainyDays days — plan museums, cafés and spa time for wet afternoons.'
        : d.avgTempC >= 26
        ? 'Hot & sunny — do outdoor sightseeing before 11am and save sunsets for the beach.'
        : d.avgTempC <= 15
        ? 'Cool weather — perfect for hiking and city walks. Pack warm layers.'
        : 'Pleasant conditions — great for outdoor activities all day.';

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: AppColors.brandGradient,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(
                Icons.auto_awesome_rounded,
                color: AppColors.sunset,
                size: 18,
              ),
              SizedBox(width: 6),
              Text(
                'Smart weather',
                style: TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                  fontSize: 16,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              for (final f in forecast)
                Column(
                  children: [
                    Text(
                      f.$1.day == now.day
                          ? 'Today'
                          : Fmt.weekday(f.$1).split(',').first,
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 12,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Icon(
                      f.$3,
                      color: f.$4 ? const Color(0xFF7DD3FC) : AppColors.sunset,
                    ),
                    const SizedBox(height: 6),
                    Text(
                      '${f.$2}°',
                      style: const TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
            ],
          ),
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Text(
              suggestion,
              style: const TextStyle(color: Colors.white, height: 1.4),
            ),
          ),
        ],
      ),
    );
  }
}

class _CostCard extends StatelessWidget {
  const _CostCard({required this.destination});
  final Destination destination;

  @override
  Widget build(BuildContext context) {
    final c = destination.cost;
    final rows = [
      (Icons.flight_rounded, 'Return transport', c.transport),
      (Icons.hotel_rounded, 'Hotel × 2 nights', c.hotelPerNight * 2),
      (Icons.restaurant_rounded, 'Food × 3 days', c.foodPerDay * 3),
      (
        Icons.local_activity_rounded,
        'Activities × 3 days',
        c.activitiesPerDay * 3,
      ),
    ];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Estimated travel costs',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            Text(
              'Per person, 3-day trip from Dhaka',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 12),
            for (final r in rows)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Row(
                  children: [
                    Icon(r.$1, size: 20, color: AppColors.ocean),
                    const SizedBox(width: 10),
                    Expanded(child: Text(r.$2)),
                    Text(
                      Fmt.bdt(r.$3),
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                  ],
                ),
              ),
            const Divider(height: 20),
            Row(
              children: [
                const Expanded(
                  child: Text(
                    'Total',
                    style: TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
                  ),
                ),
                Text(
                  Fmt.bdt(destination.typicalTripCost),
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 16,
                    color: AppColors.coral,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _AttractionCard extends StatelessWidget {
  const _AttractionCard(this.a);
  final Attraction a;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 170,
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            NetImage(a.imageUrl, height: 120, width: double.infinity),
            Padding(
              padding: const EdgeInsets.all(10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    a.name,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(
                        Icons.star_rounded,
                        color: AppColors.star,
                        size: 15,
                      ),
                      Text(
                        ' ${a.rating}',
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const Text(
                        '  ·  ',
                        style: TextStyle(color: AppColors.textSecondary),
                      ),
                      Expanded(
                        child: Text(
                          a.distanceKm > 0 ? '${a.distanceKm} km' : a.type,
                          style: const TextStyle(
                            fontSize: 12,
                            color: AppColors.textSecondary,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ReviewCard extends StatelessWidget {
  const _ReviewCard(this.r);
  final Review r;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 18,
                  backgroundColor: AppColors.ocean.withValues(alpha: 0.12),
                  child: Text(
                    r.author[0],
                    style: const TextStyle(
                      color: AppColors.ocean,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        r.author,
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                      Text(
                        Fmt.date(r.date),
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                RatingBadge(r.rating),
              ],
            ),
            const SizedBox(height: 10),
            Text(r.comment),
          ],
        ),
      ),
    );
  }
}
