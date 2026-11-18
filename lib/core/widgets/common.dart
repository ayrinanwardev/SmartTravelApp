import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../data/models/trip.dart';
import '../network/api_client.dart';
import '../../features/saved/providers/wishlist_provider.dart';
import '../theme/app_colors.dart';

/// Awaits [future]; on failure shows the error (e.g. "Can't reach the server") in a
/// snackbar and returns null, so callers can simply `if (result == null) return;`.
Future<T?> runApi<T>(BuildContext context, Future<T> future) async {
  final messenger = ScaffoldMessenger.maybeOf(context);
  try {
    return await future;
  } catch (e) {
    messenger
      ?..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(ApiException.from(e).message), backgroundColor: AppColors.danger));
    return null;
  }
}

/// Network or local-file image with a branded gradient placeholder/fallback.
class NetImage extends StatelessWidget {
  const NetImage(this.url, {super.key, this.fit = BoxFit.cover, this.borderRadius, this.height, this.width});

  final String url;
  final BoxFit fit;
  final BorderRadius? borderRadius;
  final double? height;
  final double? width;

  @override
  Widget build(BuildContext context) {
    Widget fallback({bool loading = false}) => Container(
          height: height,
          width: width,
          decoration: const BoxDecoration(gradient: AppColors.brandGradient),
          alignment: Alignment.center,
          child: loading
              ? null
              : Icon(Icons.landscape_rounded, color: Colors.white.withValues(alpha: 0.5), size: 36),
        );

    final Widget image;
    if (url.startsWith('http')) {
      image = CachedNetworkImage(
        imageUrl: url,
        fit: fit,
        height: height,
        width: width,
        fadeInDuration: const Duration(milliseconds: 250),
        placeholder: (_, _) => fallback(loading: true),
        errorWidget: (_, _, _) => fallback(),
      );
    } else {
      image = Image.file(
        File(url),
        fit: fit,
        height: height,
        width: width,
        errorBuilder: (_, _, _) => fallback(),
      );
    }

    if (borderRadius == null) return image;
    return ClipRRect(borderRadius: borderRadius!, child: image);
  }
}

class SectionHeader extends StatelessWidget {
  const SectionHeader(this.title, {super.key, this.action, this.onAction, this.padding});

  final String title;
  final String? action;
  final VoidCallback? onAction;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: padding ?? const EdgeInsets.fromLTRB(20, 24, 12, 12),
      child: Row(
        children: [
          Expanded(child: Text(title, style: Theme.of(context).textTheme.titleLarge)),
          if (action != null)
            TextButton(
              onPressed: onAction,
              style: TextButton.styleFrom(foregroundColor: AppColors.coral),
              child: Text(action!, style: const TextStyle(fontWeight: FontWeight.w700)),
            ),
        ],
      ),
    );
  }
}

class RatingBadge extends StatelessWidget {
  const RatingBadge(this.rating, {super.key, this.dark = false, this.count});

  final double rating;
  final bool dark;
  final int? count;

  @override
  Widget build(BuildContext context) {
    final color = dark ? Colors.white : AppColors.textPrimary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: dark ? Colors.black.withValues(alpha: 0.35) : AppColors.surfaceMuted,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.star_rounded, size: 16, color: AppColors.star),
          const SizedBox(width: 3),
          Text(rating.toStringAsFixed(1), style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: color)),
          if (count != null)
            Text(' ($count)', style: TextStyle(fontSize: 12, color: color.withValues(alpha: 0.7))),
        ],
      ),
    );
  }
}

/// Heart toggle wired straight to the wishlist.
class SaveButton extends ConsumerWidget {
  const SaveButton(this.destinationId, {super.key, this.size = 40});

  final String destinationId;
  final double size;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final saved = ref.watch(wishlistProvider.select((s) => s.contains(destinationId)));
    return Material(
      color: Colors.white,
      shape: const CircleBorder(),
      elevation: 2,
      shadowColor: Colors.black26,
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: () async {
          final added = await runApi(context, ref.read(wishlistProvider.notifier).toggle(destinationId));
          if (added == null || !context.mounted) return;
          ScaffoldMessenger.of(context)
            ..hideCurrentSnackBar()
            ..showSnackBar(SnackBar(
              content: Text(added ? 'Saved to your wishlist' : 'Removed from wishlist'),
              duration: const Duration(seconds: 1),
            ));
        },
        child: SizedBox(
          width: size,
          height: size,
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 200),
            transitionBuilder: (c, a) => ScaleTransition(scale: a, child: c),
            child: Icon(
              saved ? Icons.favorite_rounded : Icons.favorite_border_rounded,
              key: ValueKey(saved),
              color: saved ? AppColors.coral : AppColors.textPrimary,
              size: size * 0.5,
            ),
          ),
        ),
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.icon, required this.title, required this.message, this.action, this.onAction});

  final IconData icon;
  final String title;
  final String message;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(22),
              decoration: BoxDecoration(color: AppColors.coral.withValues(alpha: 0.1), shape: BoxShape.circle),
              child: Icon(icon, size: 40, color: AppColors.coral),
            ),
            const SizedBox(height: 20),
            Text(title, style: Theme.of(context).textTheme.titleLarge, textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Text(message, style: Theme.of(context).textTheme.bodyMedium, textAlign: TextAlign.center),
            if (action != null) ...[
              const SizedBox(height: 24),
              SizedBox(width: 220, child: FilledButton(onPressed: onAction, child: Text(action!))),
            ],
          ],
        ),
      ),
    );
  }
}

class IconBubble extends StatelessWidget {
  const IconBubble({super.key, required this.icon, this.color = AppColors.ocean, this.size = 44});

  final IconData icon;
  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(size * 0.32)),
      child: Icon(icon, color: color, size: size * 0.5),
    );
  }
}

class CircleIconButton extends StatelessWidget {
  const CircleIconButton({super.key, required this.icon, required this.onPressed, this.tooltip});

  final IconData icon;
  final VoidCallback onPressed;
  final String? tooltip;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      shape: const CircleBorder(),
      elevation: 2,
      shadowColor: Colors.black26,
      child: IconButton(icon: Icon(icon, color: AppColors.textPrimary), onPressed: onPressed, tooltip: tooltip),
    );
  }
}

IconData activityIcon(ActivityType t) => switch (t) {
      ActivityType.sightseeing => Icons.place_rounded,
      ActivityType.food => Icons.restaurant_rounded,
      ActivityType.transport => Icons.directions_bus_rounded,
      ActivityType.stay => Icons.hotel_rounded,
      ActivityType.adventure => Icons.hiking_rounded,
      ActivityType.shopping => Icons.shopping_bag_rounded,
      ActivityType.relax => Icons.spa_rounded,
    };

Color activityColor(ActivityType t) => switch (t) {
      ActivityType.sightseeing => AppColors.ocean,
      ActivityType.food => AppColors.coral,
      ActivityType.transport => const Color(0xFF64748B),
      ActivityType.stay => const Color(0xFF8B5CF6),
      ActivityType.adventure => AppColors.success,
      ActivityType.shopping => AppColors.warning,
      ActivityType.relax => const Color(0xFF0EA5E9),
    };
