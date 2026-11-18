import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/hotel.dart';
import '../providers/bookings_provider.dart';

IconData amenityIcon(String a) {
  final s = a.toLowerCase();
  if (s.contains('wi-fi') || s.contains('wifi')) return Icons.wifi_rounded;
  if (s.contains('pool')) return Icons.pool_rounded;
  if (s.contains('spa') || s.contains('onsen')) return Icons.spa_rounded;
  if (s.contains('breakfast') || s.contains('meal') || s.contains('dinner') || s.contains('restaurant') || s.contains('café')) {
    return Icons.free_breakfast_rounded;
  }
  if (s.contains('view') || s.contains('balcony')) return Icons.landscape_rounded;
  if (s.contains('gym')) return Icons.fitness_center_rounded;
  if (s.contains('shuttle') || s.contains('transfer')) return Icons.airport_shuttle_rounded;
  if (s.contains('air')) return Icons.ac_unit_rounded;
  return Icons.check_circle_outline_rounded;
}

class HotelsScreen extends ConsumerStatefulWidget {
  const HotelsScreen({super.key, this.destinationId});
  final String? destinationId;

  @override
  ConsumerState<HotelsScreen> createState() => _HotelsScreenState();
}

class _HotelsScreenState extends ConsumerState<HotelsScreen> {
  bool _cheapFirst = false;

  @override
  Widget build(BuildContext context) {
    final hotels = ref.watch(hotelsProvider(widget.destinationId));
    final dest = widget.destinationId == null ? null : ref.watch(destinationByIdProvider(widget.destinationId!));

    return Scaffold(
      appBar: AppBar(
        title: Text(dest == null ? 'Hotels' : 'Hotels in ${dest.name}'),
        actions: [
          PopupMenuButton<bool>(
            icon: const Icon(Icons.sort_rounded),
            initialValue: _cheapFirst,
            onSelected: (v) => setState(() => _cheapFirst = v),
            itemBuilder: (_) => const [
              PopupMenuItem(value: false, child: Text('Top rated')),
              PopupMenuItem(value: true, child: Text('Lowest price')),
            ],
          ),
        ],
      ),
      body: hotels.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => EmptyState(icon: Icons.error_outline, title: 'Could not load hotels', message: '$e'),
        data: (list) {
          if (list.isEmpty) {
            return EmptyState(
              icon: Icons.hotel_rounded,
              title: 'No hotels listed yet',
              message: 'We are adding stays here soon. Browse all hotels meanwhile.',
              action: 'All hotels',
              onAction: () => context.pushReplacement('/hotels'),
            );
          }
          final sorted = [...list]
            ..sort((a, b) => _cheapFirst ? a.pricePerNight.compareTo(b.pricePerNight) : b.rating.compareTo(a.rating));
          return ListView.separated(
            padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
            itemCount: sorted.length,
            separatorBuilder: (_, _) => const SizedBox(height: 16),
            itemBuilder: (_, i) => _HotelCard(sorted[i]),
          );
        },
      ),
    );
  }
}

class _HotelCard extends StatelessWidget {
  const _HotelCard(this.h);
  final Hotel h;

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.push('/hotel/${h.id}'),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Stack(
              children: [
                NetImage(h.imageUrls.first, height: 170, width: double.infinity),
                Positioned(top: 12, left: 12, child: RatingBadge(h.rating, dark: true, count: h.reviewCount)),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(child: Text(h.name, style: Theme.of(context).textTheme.titleMedium)),
                      Text('★' * h.stars, style: const TextStyle(color: AppColors.star, fontSize: 12)),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(h.address, style: Theme.of(context).textTheme.bodySmall),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      for (final a in h.amenities.take(4))
                        Padding(
                          padding: const EdgeInsets.only(right: 10),
                          child: Tooltip(message: a, child: Icon(amenityIcon(a), size: 18, color: AppColors.textSecondary)),
                        ),
                      const Spacer(),
                      Text(Fmt.bdt(h.pricePerNight),
                          style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17, color: AppColors.navy)),
                      const Text(' /night', style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
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

class HotelDetailScreen extends ConsumerStatefulWidget {
  const HotelDetailScreen({super.key, required this.id});
  final String id;

  @override
  ConsumerState<HotelDetailScreen> createState() => _HotelDetailScreenState();
}

class _HotelDetailScreenState extends ConsumerState<HotelDetailScreen> {
  late DateTimeRange _dates;
  int _guests = 2;
  int _rooms = 1;
  bool _booking = false;

  @override
  void initState() {
    super.initState();
    final today = DateUtils.dateOnly(DateTime.now());
    _dates = DateTimeRange(start: today.add(const Duration(days: 7)), end: today.add(const Duration(days: 9)));
  }

  @override
  Widget build(BuildContext context) {
    final loading = ref.watch(hotelsProvider(null)).isLoading;
    final h = ref.watch(hotelByIdProvider(widget.id));
    if (h == null) {
      return Scaffold(
        appBar: AppBar(),
        body: loading
            ? const Center(child: CircularProgressIndicator())
            : const EmptyState(icon: Icons.hotel, title: 'Hotel not found', message: ''),
      );
    }
    final nights = _dates.end.difference(_dates.start).inDays;
    final subtotal = h.pricePerNight * nights * _rooms;
    final taxes = (subtotal * 0.1).round();
    final text = Theme.of(context).textTheme;

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 300,
            pinned: true,
            backgroundColor: AppColors.navy,
            foregroundColor: Colors.white,
            leading: Padding(
              padding: const EdgeInsets.all(6),
              child: CircleIconButton(icon: Icons.arrow_back_rounded, onPressed: () => context.pop()),
            ),
            flexibleSpace: FlexibleSpaceBar(
              background: PageView(children: [for (final u in h.imageUrls) NetImage(u)]),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.all(20),
            sliver: SliverList.list(
              children: [
                Row(
                  children: [
                    Expanded(child: Text(h.name, style: text.headlineSmall)),
                    RatingBadge(h.rating, count: h.reviewCount),
                  ],
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    const Icon(Icons.location_on_rounded, size: 16, color: AppColors.coral),
                    const SizedBox(width: 4),
                    Expanded(child: Text(h.address, style: text.bodyMedium)),
                  ],
                ),
                const SizedBox(height: 16),
                Text(h.description, style: text.bodyMedium?.copyWith(fontSize: 15)),
                const SizedBox(height: 20),
                Text('Amenities', style: text.titleMedium),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final a in h.amenities) Chip(avatar: Icon(amenityIcon(a), size: 18, color: AppColors.ocean), label: Text(a)),
                  ],
                ),
                const SizedBox(height: 20),
                Text('Guest ratings', style: text.titleMedium),
                const SizedBox(height: 10),
                for (final (label, score) in [
                  ('Cleanliness', (h.rating + 0.1).clamp(0, 5)),
                  ('Location', h.rating),
                  ('Service', (h.rating - 0.1).clamp(0, 5)),
                  ('Value', (h.rating - 0.3).clamp(0, 5)),
                ])
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      children: [
                        SizedBox(width: 100, child: Text(label)),
                        Expanded(
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(6),
                            child: LinearProgressIndicator(
                              value: score / 5,
                              minHeight: 8,
                              color: AppColors.ocean,
                              backgroundColor: AppColors.surfaceMuted,
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Text(score.toStringAsFixed(1), style: const TextStyle(fontWeight: FontWeight.w700)),
                      ],
                    ),
                  ),
                const SizedBox(height: 20),
                Text('Your stay', style: text.titleMedium),
                const SizedBox(height: 10),
                Card(
                  child: Column(
                    children: [
                      ListTile(
                        leading: const Icon(Icons.date_range_rounded, color: AppColors.ocean),
                        title: Text(Fmt.range(_dates.start, _dates.end), style: const TextStyle(fontWeight: FontWeight.w600)),
                        subtitle: Text('$nights ${nights == 1 ? 'night' : 'nights'}'),
                        trailing: const Icon(Icons.edit_rounded, size: 18),
                        onTap: () async {
                          final now = DateUtils.dateOnly(DateTime.now());
                          final r = await showDateRangePicker(
                            context: context,
                            firstDate: now,
                            lastDate: now.add(const Duration(days: 365)),
                            initialDateRange: _dates,
                          );
                          if (r != null && r.end.isAfter(r.start)) setState(() => _dates = r);
                        },
                      ),
                      const Divider(height: 1),
                      _Counter(label: 'Guests', icon: Icons.person_rounded, value: _guests, min: 1, max: 10, onChanged: (v) => setState(() => _guests = v)),
                      const Divider(height: 1),
                      _Counter(label: 'Rooms', icon: Icons.bed_rounded, value: _rooms, min: 1, max: 5, onChanged: (v) => setState(() => _rooms = v)),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                _PriceRow('${Fmt.bdt(h.pricePerNight)} × $nights nights × $_rooms room', subtotal),
                _PriceRow('Taxes & fees (10%)', taxes),
                const Divider(height: 24),
                _PriceRow('Total', subtotal + taxes, bold: true),
                const SizedBox(height: 100),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
          child: FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.coral),
            onPressed: _booking
                ? null
                : () async {
                    setState(() => _booking = true);
                    final booking = await runApi(
                      context,
                      ref.read(bookingsProvider.notifier).book(
                            hotel: h,
                            checkIn: _dates.start,
                            checkOut: _dates.end,
                            guests: _guests,
                            rooms: _rooms,
                          ),
                    );
                    if (!context.mounted) return;
                    setState(() => _booking = false);
                    if (booking != null) context.pushReplacement('/booking-confirmed/${booking.id}');
                  },
            child: _booking
                ? const SizedBox(
                    width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white))
                : Text('Book now · ${Fmt.bdt(subtotal + taxes)}'),
          ),
        ),
      ),
    );
  }
}

class _Counter extends StatelessWidget {
  const _Counter({required this.label, required this.icon, required this.value, required this.min, required this.max, required this.onChanged});
  final String label;
  final IconData icon;
  final int value;
  final int min;
  final int max;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: AppColors.ocean),
      title: Text(label, style: const TextStyle(fontWeight: FontWeight.w600)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(onPressed: value > min ? () => onChanged(value - 1) : null, icon: const Icon(Icons.remove_circle_outline)),
          Text('$value', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
          IconButton(onPressed: value < max ? () => onChanged(value + 1) : null, icon: const Icon(Icons.add_circle_outline)),
        ],
      ),
    );
  }
}

class _PriceRow extends StatelessWidget {
  const _PriceRow(this.label, this.amount, {this.bold = false});
  final String label;
  final int amount;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    final style = TextStyle(fontWeight: bold ? FontWeight.w800 : FontWeight.w500, fontSize: bold ? 18 : 14);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(label, style: style)),
          Text(Fmt.bdt(amount), style: style.copyWith(color: bold ? AppColors.coral : null)),
        ],
      ),
    );
  }
}

class BookingConfirmedScreen extends ConsumerWidget {
  const BookingConfirmedScreen({super.key, required this.bookingId});
  final String bookingId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final b = ref.watch(bookingsProvider).where((x) => x.id == bookingId).firstOrNull;
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: b == null
              ? const EmptyState(icon: Icons.error_outline, title: 'Booking not found', message: '')
              : Column(
                  children: [
                    const Spacer(),
                    TweenAnimationBuilder<double>(
                      tween: Tween(begin: 0, end: 1),
                      duration: const Duration(milliseconds: 600),
                      curve: Curves.elasticOut,
                      builder: (_, v, child) => Transform.scale(scale: v, child: child),
                      child: Container(
                        padding: const EdgeInsets.all(24),
                        decoration: BoxDecoration(color: AppColors.success.withValues(alpha: 0.12), shape: BoxShape.circle),
                        child: const Icon(Icons.check_circle_rounded, color: AppColors.success, size: 72),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text('Booking confirmed!', style: Theme.of(context).textTheme.headlineMedium),
                    const SizedBox(height: 8),
                    Text('Confirmation #${b.id}', style: Theme.of(context).textTheme.bodyMedium),
                    const SizedBox(height: 28),
                    Card(
                      clipBehavior: Clip.antiAlias,
                      child: Column(
                        children: [
                          NetImage(b.imageUrl, height: 140, width: double.infinity),
                          ListTile(
                            title: Text(b.hotelName, style: const TextStyle(fontWeight: FontWeight.w700)),
                            subtitle: Text('${Fmt.range(b.checkIn, b.checkOut)} · ${b.nights} nights\n'
                                '${b.guests} guests · ${b.rooms} ${b.rooms == 1 ? 'room' : 'rooms'}'),
                            isThreeLine: true,
                            trailing: Text(Fmt.bdt(b.total), style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.coral)),
                          ),
                        ],
                      ),
                    ),
                    const Spacer(),
                    FilledButton(onPressed: () => context.go('/trips'), child: const Text('Go to My Trips')),
                    const SizedBox(height: 12),
                    OutlinedButton(onPressed: () => context.go('/home'), child: const Text('Back to Home')),
                  ],
                ),
        ),
      ),
    );
  }
}

class BookingHistoryScreen extends ConsumerWidget {
  const BookingHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bookings = ref.watch(bookingsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Booking history')),
      body: bookings.isEmpty
          ? EmptyState(
              icon: Icons.receipt_long_rounded,
              title: 'No bookings yet',
              message: 'Hotels you book will show up here.',
              action: 'Browse hotels',
              onAction: () => context.push('/hotels'),
            )
          : ListView.separated(
              padding: const EdgeInsets.all(20),
              itemCount: bookings.length,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (_, i) {
                final b = bookings[i];
                return Card(
                  child: ListTile(
                    contentPadding: const EdgeInsets.all(10),
                    leading: NetImage(b.imageUrl, width: 64, height: 64, borderRadius: BorderRadius.circular(12)),
                    title: Text(b.hotelName, style: const TextStyle(fontWeight: FontWeight.w700)),
                    subtitle: Text('${Fmt.range(b.checkIn, b.checkOut)}\n#${b.id}'),
                    isThreeLine: true,
                    trailing: Text(Fmt.bdt(b.total), style: const TextStyle(fontWeight: FontWeight.w800)),
                  ),
                );
              },
            ),
    );
  }
}
