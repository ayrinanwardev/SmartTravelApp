import 'package:material_ui/material_ui.dart';

import '../../../core/theme/app_colors.dart';
import '../../../data/mock/mock_data.dart';

/// BDT ↔ foreign currency converter. Rates are static demo values in
/// [MockData.bdtRates]; replace with a live exchange-rate API via Dio.
class CurrencyScreen extends StatefulWidget {
  const CurrencyScreen({super.key});

  @override
  State<CurrencyScreen> createState() => _CurrencyScreenState();
}

class _CurrencyScreenState extends State<CurrencyScreen> {
  final _amount = TextEditingController(text: '10000');
  String _target = 'USD';
  bool _fromBdt = true;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  double get _value => double.tryParse(_amount.text.replaceAll(',', '')) ?? 0;

  double _convert(String code) {
    final rate = MockData.bdtRates[code]!;
    return _fromBdt ? _value / rate : _value * rate;
  }

  @override
  Widget build(BuildContext context) {
    final rate = MockData.bdtRates[_target]!;
    final from = _fromBdt ? 'BDT' : _target;
    final to = _fromBdt ? _target : 'BDT';
    return Scaffold(
      appBar: AppBar(title: const Text('Currency Converter')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 32),
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(gradient: AppColors.brandGradient, borderRadius: BorderRadius.circular(24)),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('From $from', style: const TextStyle(color: Colors.white70)),
                TextField(
                  controller: _amount,
                  onChanged: (_) => setState(() {}),
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  style: const TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.w800),
                  decoration: InputDecoration(
                    filled: false,
                    border: InputBorder.none,
                    enabledBorder: InputBorder.none,
                    focusedBorder: InputBorder.none,
                    contentPadding: EdgeInsets.zero,
                    prefixText: _fromBdt ? '৳ ' : '$_target ',
                    prefixStyle: const TextStyle(color: Colors.white70, fontSize: 22),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    const Expanded(child: Divider(color: Colors.white24)),
                    IconButton.filled(
                      style: IconButton.styleFrom(backgroundColor: AppColors.coral),
                      onPressed: () => setState(() => _fromBdt = !_fromBdt),
                      icon: const Icon(Icons.swap_vert_rounded),
                    ),
                    const Expanded(child: Divider(color: Colors.white24)),
                  ],
                ),
                const SizedBox(height: 8),
                Text('To $to', style: const TextStyle(color: Colors.white70)),
                const SizedBox(height: 4),
                Text(
                  '${_fromBdt ? _target : '৳'} ${_convert(_target).toStringAsFixed(2)}',
                  style: const TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 10),
                Text('1 $_target = ৳${rate.toStringAsFixed(2)}  ·  demo rates',
                    style: const TextStyle(color: Colors.white60, fontSize: 12)),
              ],
            ),
          ),
          const SizedBox(height: 20),
          Text('Currencies', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 10),
          for (final code in MockData.bdtRates.keys)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Card(
                color: code == _target ? AppColors.coral.withValues(alpha: 0.08) : null,
                child: ListTile(
                  onTap: () => setState(() => _target = code),
                  leading: CircleAvatar(
                    backgroundColor: AppColors.navy,
                    child: Text(code.substring(0, 2), style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w700)),
                  ),
                  title: Text(code, style: const TextStyle(fontWeight: FontWeight.w700)),
                  subtitle: Text('1 $code = ৳${MockData.bdtRates[code]!.toStringAsFixed(2)}'),
                  trailing: Text(
                    _fromBdt ? _convert(code).toStringAsFixed(2) : '৳${_convert(code).toStringAsFixed(0)}',
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
