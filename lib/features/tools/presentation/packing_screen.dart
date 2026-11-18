import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../data/models/destination.dart';

/// Generates a packing list from destination type, climate and trip length.
class PackingScreen extends ConsumerStatefulWidget {
  const PackingScreen({super.key, this.destinationId, this.days});
  final String? destinationId;
  final int? days;

  @override
  ConsumerState<PackingScreen> createState() => _PackingScreenState();
}

class _PackingScreenState extends ConsumerState<PackingScreen> {
  String? _destId;
  late int _days = widget.days ?? 4;
  final Set<String> _checked = {};

  @override
  void initState() {
    super.initState();
    _destId = widget.destinationId;
  }

  static Map<String, List<String>> generate(Destination d, int days) {
    final outfits = (days * 0.8).ceil().clamp(2, 10);
    final hot = d.avgTempC >= 25;
    final cold = d.avgTempC <= 16;
    final international = d.country != 'Bangladesh';
    final rainy = d.bestSeasons.contains(Season.monsoon);
    return {
      'Documents': [
        if (international) ...['Passport (6+ months validity)', 'Visa / e-visa printout', 'Travel insurance'] else 'National ID card',
        'Booking confirmations',
        'Emergency contacts',
      ],
      'Clothing': [
        '$outfits × tops',
        '${(outfits / 2).ceil()} × bottoms',
        '$days × underwear & socks',
        'Sleepwear',
        if (hot) ...['Light cotton shirts', 'Sun hat', 'Sunglasses'],
        if (cold) ...['Warm jacket', 'Thermal layer', 'Beanie & gloves'],
        if (rainy) 'Rain jacket / poncho',
        if (d.category == DestinationCategory.beach) ...['Swimwear', 'Flip-flops', 'Beach towel'],
        if (d.category == DestinationCategory.mountain || d.category == DestinationCategory.adventure) ...['Hiking shoes', 'Quick-dry trousers'],
        if (d.category == DestinationCategory.city || d.category == DestinationCategory.culture) ...['Comfortable walking shoes', 'One smart outfit'],
      ],
      'Toiletries': [
        'Toothbrush & toothpaste',
        'Shampoo & soap',
        if (hot || d.category == DestinationCategory.beach) 'Sunscreen SPF 50',
        'Insect repellent',
        'Personal medication',
        'Basic first-aid kit',
      ],
      'Electronics': [
        'Phone & charger',
        'Power bank',
        if (international) 'Universal travel adapter',
        if (d.category == DestinationCategory.beach) 'Waterproof phone pouch',
        'Earphones',
      ],
      'Extras': [
        'Reusable water bottle',
        'Day backpack',
        if (international) 'Some local cash (${d.country})' else 'Cash for local markets',
        'Snacks for the journey',
      ],
    };
  }

  @override
  Widget build(BuildContext context) {
    final destinations = ref.watch(destinationsProvider).value ?? const <Destination>[];
    final dest = destinations.where((d) => d.id == _destId).firstOrNull;
    final list = dest == null ? null : generate(dest, _days);
    final total = list?.values.fold<int>(0, (s, l) => s + l.length) ?? 0;
    final done = list == null ? 0 : list.entries.expand((e) => e.value.map((i) => '${e.key}/$i')).where(_checked.contains).length;

    return Scaffold(
      appBar: AppBar(title: const Text('Smart Packing List')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 32),
        children: [
          DropdownButtonFormField<String>(
            initialValue: _destId,
            isExpanded: true,
            hint: const Text('Where are you going?'),
            decoration: const InputDecoration(prefixIcon: Icon(Icons.place_rounded)),
            items: [for (final d in destinations) DropdownMenuItem(value: d.id, child: Text(d.location))],
            onChanged: (v) => setState(() {
              _destId = v;
              _checked.clear();
            }),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              const Icon(Icons.today_rounded, color: AppColors.textSecondary),
              const SizedBox(width: 8),
              Text('$_days days', style: const TextStyle(fontWeight: FontWeight.w700)),
              Expanded(
                child: Slider(
                  value: _days.toDouble(),
                  min: 1,
                  max: 14,
                  divisions: 13,
                  activeColor: AppColors.coral,
                  onChanged: (v) => setState(() => _days = v.round()),
                ),
              ),
            ],
          ),
          if (list == null)
            const Padding(
              padding: EdgeInsets.all(40),
              child: Text('Pick a destination to generate a list based on its weather, activities and trip length.',
                  textAlign: TextAlign.center),
            )
          else ...[
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(gradient: AppColors.brandGradient, borderRadius: BorderRadius.circular(20)),
              child: Row(
                children: [
                  SizedBox(
                    width: 52,
                    height: 52,
                    child: CircularProgressIndicator(
                      value: total == 0 ? 0 : done / total,
                      strokeWidth: 6,
                      color: AppColors.sunset,
                      backgroundColor: Colors.white24,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('$done of $total packed',
                            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 17)),
                        Text('${dest!.name} · ~${dest.avgTempC}°C · ${dest.category.label}',
                            style: const TextStyle(color: Colors.white70)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            for (final section in list.entries) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(4, 20, 4, 6),
                child: Text(section.key, style: Theme.of(context).textTheme.titleMedium),
              ),
              Card(
                child: Column(
                  children: [
                    for (final item in section.value)
                      CheckboxListTile(
                        value: _checked.contains('${section.key}/$item'),
                        activeColor: AppColors.coral,
                        controlAffinity: ListTileControlAffinity.leading,
                        title: Text(
                          item,
                          style: TextStyle(
                            decoration: _checked.contains('${section.key}/$item') ? TextDecoration.lineThrough : null,
                            color: _checked.contains('${section.key}/$item') ? AppColors.textSecondary : null,
                          ),
                        ),
                        onChanged: (v) => setState(() {
                          final k = '${section.key}/$item';
                          v == true ? _checked.add(k) : _checked.remove(k);
                        }),
                      ),
                  ],
                ),
              ),
            ],
          ],
        ],
      ),
    );
  }
}
