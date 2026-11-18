import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/destination.dart';
import '../../../data/models/trip.dart';
import '../providers/trips_provider.dart';

/// Create a new trip: destination, dates, travellers and budget.
class TripPlannerScreen extends ConsumerStatefulWidget {
  const TripPlannerScreen({super.key, this.destinationId});
  final String? destinationId;

  @override
  ConsumerState<TripPlannerScreen> createState() => _TripPlannerScreenState();
}

class _TripPlannerScreenState extends ConsumerState<TripPlannerScreen> {
  String? _destId;
  DateTimeRange? _dates;
  int _travelers = 2;
  bool _saving = false;
  late final _title = TextEditingController();
  final _budget = TextEditingController();

  @override
  void initState() {
    super.initState();
    _destId = widget.destinationId;
    final today = DateUtils.dateOnly(DateTime.now());
    _dates = DateTimeRange(start: today.add(const Duration(days: 14)), end: today.add(const Duration(days: 17)));
  }

  @override
  void dispose() {
    _title.dispose();
    _budget.dispose();
    super.dispose();
  }

  Future<void> _pickDates() async {
    final now = DateUtils.dateOnly(DateTime.now());
    final picked = await showDateRangePicker(
      context: context,
      firstDate: now,
      lastDate: now.add(const Duration(days: 730)),
      initialDateRange: _dates,
      helpText: 'Select trip dates',
    );
    if (picked != null) setState(() => _dates = picked);
  }

  Future<void> _create(Destination d) async {
    final dates = _dates!;
    final dayCount = dates.end.difference(dates.start).inDays + 1;
    final suggested = d.cost.forDays(dayCount, travelers: _travelers);
    final trip = Trip(
      id: uuid.v4(),
      destinationId: d.id,
      title: _title.text.trim().isEmpty ? '${d.name} Trip' : _title.text.trim(),
      imageUrl: d.imageUrl,
      startDate: dates.start,
      endDate: dates.end,
      travelers: _travelers,
      budget: int.tryParse(_budget.text.replaceAll(',', '')) ?? suggested,
      days: TripsNotifier.emptyDays(dayCount),
    );
    setState(() => _saving = true);
    final saved = await runApi(context, ref.read(tripsProvider.notifier).add(trip));
    if (!mounted) return;
    setState(() => _saving = false);
    if (saved == null) return;
    context.pushReplacement('/trip/${saved.id}');
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Trip created — now add some activities!')));
  }

  @override
  Widget build(BuildContext context) {
    final destinations = ref.watch(destinationsProvider).value ?? const <Destination>[];
    final dest = destinations.where((d) => d.id == _destId).firstOrNull;
    final dayCount = _dates == null ? 0 : _dates!.end.difference(_dates!.start).inDays + 1;
    final text = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Plan a trip')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          if (dest != null)
            ClipRRect(
              borderRadius: BorderRadius.circular(24),
              child: Stack(
                children: [
                  NetImage(dest.imageUrl, height: 160, width: double.infinity),
                  const Positioned.fill(child: DecoratedBox(decoration: BoxDecoration(gradient: AppColors.heroGradient))),
                  Positioned(
                    left: 16,
                    bottom: 14,
                    child: Text(dest.location,
                        style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800)),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 20),
          Text('Destination', style: text.titleSmall),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            initialValue: _destId,
            isExpanded: true,
            hint: const Text('Choose a destination'),
            decoration: const InputDecoration(prefixIcon: Icon(Icons.place_rounded)),
            items: [for (final d in destinations) DropdownMenuItem(value: d.id, child: Text(d.location))],
            onChanged: (v) => setState(() => _destId = v),
          ),
          const SizedBox(height: 18),
          Text('Trip name (optional)', style: text.titleSmall),
          const SizedBox(height: 8),
          TextField(
            controller: _title,
            textCapitalization: TextCapitalization.words,
            decoration: InputDecoration(
              hintText: dest == null ? 'e.g. Summer escape' : '${dest.name} Trip',
              prefixIcon: const Icon(Icons.edit_rounded),
            ),
          ),
          const SizedBox(height: 18),
          Text('Dates', style: text.titleSmall),
          const SizedBox(height: 8),
          InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: _pickDates,
            child: InputDecorator(
              decoration: const InputDecoration(prefixIcon: Icon(Icons.date_range_rounded)),
              child: Text(_dates == null ? 'Select dates' : '${Fmt.range(_dates!.start, _dates!.end)}  ·  $dayCount days',
                  style: const TextStyle(fontWeight: FontWeight.w600)),
            ),
          ),
          const SizedBox(height: 18),
          Text('Travellers', style: text.titleSmall),
          const SizedBox(height: 8),
          Card(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              child: Row(
                children: [
                  const Icon(Icons.group_rounded, color: AppColors.textSecondary),
                  const SizedBox(width: 12),
                  Expanded(child: Text('$_travelers ${_travelers == 1 ? 'person' : 'people'}', style: const TextStyle(fontWeight: FontWeight.w600))),
                  IconButton.outlined(
                    onPressed: _travelers > 1 ? () => setState(() => _travelers--) : null,
                    icon: const Icon(Icons.remove_rounded),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    style: IconButton.styleFrom(backgroundColor: AppColors.navy),
                    onPressed: _travelers < 20 ? () => setState(() => _travelers++) : null,
                    icon: const Icon(Icons.add_rounded),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 18),
          Text('Budget (৳)', style: text.titleSmall),
          const SizedBox(height: 8),
          TextField(
            controller: _budget,
            keyboardType: TextInputType.number,
            decoration: InputDecoration(
              prefixIcon: const Icon(Icons.account_balance_wallet_rounded),
              hintText: dest == null ? 'e.g. 30000' : 'Suggested: ${Fmt.bdt(dest.cost.forDays(dayCount, travelers: _travelers))}',
            ),
          ),
          if (dest != null) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(color: AppColors.coral.withValues(alpha: 0.08), borderRadius: BorderRadius.circular(16)),
              child: Row(
                children: [
                  const Icon(Icons.lightbulb_rounded, color: AppColors.coral),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Typical cost for $_travelers × $dayCount days in ${dest.name}: '
                      '${Fmt.bdt(dest.cost.forDays(dayCount, travelers: _travelers))}. Best time: ${dest.bestTime}.',
                      style: const TextStyle(height: 1.4),
                    ),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 28),
          FilledButton.icon(
            onPressed: dest == null || _dates == null || _saving ? null : () => _create(dest),
            icon: const Icon(Icons.check_rounded),
            label: const Text('Create trip'),
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () => context.push('/assistant'),
            icon: const Icon(Icons.auto_awesome_rounded, color: AppColors.coral),
            label: const Text('Let AI draft the itinerary'),
          ),
        ],
      ),
    );
  }
}
