import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../data/models/destination.dart';
import '../theme/app_colors.dart';
import '../utils/formatters.dart';
import 'common.dart';

/// Tall photo card used in horizontal carousels.
class DestinationHeroCard extends StatelessWidget {
  const DestinationHeroCard(this.destination, {super.key, this.width = 250, this.height = 320});

  final Destination destination;
  final double width;
  final double height;

  @override
  Widget build(BuildContext context) {
    final d = destination;
    return GestureDetector(
      onTap: () => context.push('/destination/${d.id}'),
      child: SizedBox(
        width: width,
        height: height,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(28),
          child: Stack(
            fit: StackFit.expand,
            children: [
              NetImage(d.imageUrl),
              const DecoratedBox(decoration: BoxDecoration(gradient: AppColors.heroGradient)),
              Positioned(top: 14, right: 14, child: SaveButton(d.id, size: 38)),
              Positioned(top: 14, left: 14, child: RatingBadge(d.rating, dark: true)),
              Positioned(
                left: 18,
                right: 18,
                bottom: 18,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      d.name,
                      style: const TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.w800, letterSpacing: -0.4),
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        const Icon(Icons.location_on_rounded, color: Colors.white70, size: 16),
                        const SizedBox(width: 4),
                        Text(d.country, style: const TextStyle(color: Colors.white70, fontWeight: FontWeight.w500)),
                        const Spacer(),
                        Text(
                          'from ${Fmt.bdt(d.cost.hotelPerNight)}',
                          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 13),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Horizontal list-row card used in explore / saved / search results.
class DestinationTile extends StatelessWidget {
  const DestinationTile(this.destination, {super.key, this.trailing});

  final Destination destination;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final d = destination;
    final text = Theme.of(context).textTheme;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.push('/destination/${d.id}'),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Row(
            children: [
              NetImage(d.imageUrl, width: 104, height: 104, borderRadius: BorderRadius.circular(16)),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${d.category.emoji}  ${d.category.label}'.toUpperCase(),
                        style: const TextStyle(fontSize: 11, letterSpacing: 0.8, fontWeight: FontWeight.w700, color: AppColors.coral)),
                    const SizedBox(height: 4),
                    Text(d.name, style: text.titleMedium, maxLines: 1, overflow: TextOverflow.ellipsis),
                    Text(d.country, style: text.bodySmall?.copyWith(color: AppColors.textSecondary)),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        const Icon(Icons.star_rounded, size: 16, color: AppColors.star),
                        const SizedBox(width: 2),
                        Text(d.rating.toStringAsFixed(1), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
                        const SizedBox(width: 10),
                        Flexible(
                          child: Text(
                            '~${Fmt.bdt(d.typicalTripCost)} / 3 days',
                            style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              trailing ?? SaveButton(d.id, size: 36),
            ],
          ),
        ),
      ),
    );
  }
}
