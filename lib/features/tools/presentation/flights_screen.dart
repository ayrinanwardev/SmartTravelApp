import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../data/models/destination.dart';

class _Flight {
  const _Flight(this.airline, this.depart, this.arrive, this.duration, this.stops, this.price);
  final String airline;
  final String depart;
  final String arrive;
  final String duration;
  final int stops;
  final int price;
}

/// Flight search explorer with simulated fares (no live GDS integration).
class FlightsScreen extends ConsumerStatefulWidget {
  const FlightsScreen({super.key});

  @override
  ConsumerState<FlightsScreen> createState() => _FlightsScreenState();
}

class _FlightsScreenState extends ConsumerState<FlightsScreen> {
  String? _to;
  DateTime _date = DateUtils.dateOnly(DateTime.now()).add(const Duration(days: 10));
  int _pax = 1;
  List<_Flight>? _results;

  void _search(Destination d) {
    final domestic = d.country == 'Bangladesh';
    final base = d.cost.transport ~/ 2;
    final airlines = domestic
        ? ['Biman Bangladesh', 'US-Bangla Airlines', 'NovoAir', 'Air Astra']
        : ['Biman Bangladesh', 'Emirates', 'Singapore Airlines', 'Qatar Airways', 'Thai Airways'];
    final seed = d.id.hashCode.abs() + _date.day;
    setState(() {
      _results = List.generate(airlines.length, (i) {
        final h = 6 + (seed + i * 5) % 14;
        final mins = domestic ? 50 + (i * 10) : 180 + ((seed + i * 37) % 420);
        final arr = DateTime(2000, 1, 1, h).add(Duration(minutes: mins));
        final stops = domestic ? 0 : (i + seed) % 3 == 0 ? 0 : 1;
        final price = (base * (0.85 + ((seed + i * 13) % 40) / 100) * (stops == 0 ? 1.1 : 1)).round();
        return _Flight(
          airlines[i],
          '${h.toString().padLeft(2, '0')}:${((seed * (i + 1)) % 4 * 15).toString().padLeft(2, '0')}',
          '${arr.hour.toString().padLeft(2, '0')}:${arr.minute.toString().padLeft(2, '0')}',
          '${mins ~/ 60}h ${mins % 60}m',
          stops,
          price,
        );
      })
        ..sort((a, b) => a.price.compareTo(b.price));
    });
  }

  @override
  Widget build(BuildContext context) {
    final destinations = ref.watch(destinationsProvider).value ?? const <Destination>[];
    final dest = destinations.where((d) => d.id == _to).firstOrNull;
    return Scaffold(
      appBar: AppBar(title: const Text('Flights')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 32),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  const ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.flight_takeoff_rounded, color: AppColors.ocean),
                    title: Text('Dhaka (DAC)', style: TextStyle(fontWeight: FontWeight.w700)),
                    subtitle: Text('Hazrat Shahjalal International'),
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: _to,
                    isExpanded: true,
                    hint: const Text('Flying to…'),
                    decoration: const InputDecoration(prefixIcon: Icon(Icons.flight_land_rounded)),
                    items: [for (final d in destinations) DropdownMenuItem(value: d.id, child: Text(d.location))],
                    onChanged: (v) => setState(() {
                      _to = v;
                      _results = null;
                    }),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: InkWell(
                          borderRadius: BorderRadius.circular(16),
                          onTap: () async {
                            final d = await showDatePicker(
                              context: context,
                              firstDate: DateUtils.dateOnly(DateTime.now()),
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
                      ),
                      const SizedBox(width: 10),
                      Container(
                        decoration: BoxDecoration(
                          border: Border.all(color: AppColors.border),
                          borderRadius: BorderRadius.circular(16),
                          color: AppColors.surface,
                        ),
                        child: Row(
                          children: [
                            IconButton(onPressed: _pax > 1 ? () => setState(() => _pax--) : null, icon: const Icon(Icons.remove)),
                            Text('$_pax', style: const TextStyle(fontWeight: FontWeight.w700)),
                            IconButton(onPressed: _pax < 9 ? () => setState(() => _pax++) : null, icon: const Icon(Icons.person_add_alt_1_rounded)),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  FilledButton.icon(
                    onPressed: dest == null ? null : () => _search(dest),
                    icon: const Icon(Icons.search_rounded),
                    label: const Text('Search flights'),
                  ),
                ],
              ),
            ),
          ),
          if (_results != null && dest != null) ...[
            const SizedBox(height: 20),
            Text('${_results!.length} flights to ${dest.name}', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text('Simulated fares for demo purposes', style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 10),
            for (final (i, f) in _results!.indexed)
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      children: [
                        Row(
                          children: [
                            Text(f.airline, style: const TextStyle(fontWeight: FontWeight.w700)),
                            const Spacer(),
                            if (i == 0)
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                decoration: BoxDecoration(color: AppColors.success.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(8)),
                                child: const Text('Cheapest', style: TextStyle(color: AppColors.success, fontSize: 11, fontWeight: FontWeight.w700)),
                              ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Text(f.depart, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
                            Expanded(
                              child: Column(
                                children: [
                                  Text(f.duration, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
                                  const Row(
                                    children: [
                                      Expanded(child: Divider(indent: 12, endIndent: 4)),
                                      Icon(Icons.flight_rounded, size: 16, color: AppColors.coral),
                                      Expanded(child: Divider(indent: 4, endIndent: 12)),
                                    ],
                                  ),
                                  Text(f.stops == 0 ? 'Non-stop' : '${f.stops} stop',
                                      style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
                                ],
                              ),
                            ),
                            Text(f.arrive, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
                          ],
                        ),
                        const Divider(height: 24),
                        Row(
                          children: [
                            Text('$_pax ${_pax == 1 ? 'traveller' : 'travellers'}', style: const TextStyle(color: AppColors.textSecondary)),
                            const Spacer(),
                            Text(Fmt.bdt(f.price * _pax),
                                style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: AppColors.navy)),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ],
      ),
    );
  }
}
