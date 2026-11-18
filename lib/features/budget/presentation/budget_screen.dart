import 'package:fl_chart/fl_chart.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/expense.dart';
import '../providers/budget_provider.dart';

IconData expenseIcon(ExpenseCategory c) => switch (c) {
      ExpenseCategory.flight => Icons.flight_rounded,
      ExpenseCategory.hotel => Icons.hotel_rounded,
      ExpenseCategory.food => Icons.restaurant_rounded,
      ExpenseCategory.shopping => Icons.shopping_bag_rounded,
      ExpenseCategory.transport => Icons.directions_car_rounded,
      ExpenseCategory.activities => Icons.local_activity_rounded,
    };

Color expenseColor(ExpenseCategory c) => AppColors.chart[c.index % AppColors.chart.length];

class BudgetScreen extends ConsumerWidget {
  const BudgetScreen({super.key});

  Future<void> _editBudget(BuildContext context, WidgetRef ref, int current) async {
    final controller = TextEditingController(text: '$current');
    final value = await showDialog<int>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Total trip budget'),
        content: TextField(
          controller: controller,
          autofocus: true,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(prefixText: '৳ '),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, int.tryParse(controller.text.replaceAll(',', ''))),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (value != null && value > 0 && context.mounted) {
      await runApi(context, ref.read(budgetTotalProvider.notifier).set(value));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final s = ref.watch(budgetSummaryProvider);
    final expenses = ref.watch(expensesProvider);
    final text = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Travel Budget')),
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'add-expense',
        backgroundColor: AppColors.coral,
        foregroundColor: Colors.white,
        onPressed: () => showModalBottomSheet<void>(
          context: context,
          isScrollControlled: true,
          builder: (_) => const _AddExpenseSheet(),
        ),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Expense', style: TextStyle(fontWeight: FontWeight.w700)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 100),
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(gradient: AppColors.brandGradient, borderRadius: BorderRadius.circular(24)),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Text('Total budget', style: TextStyle(color: Colors.white70)),
                    const Spacer(),
                    InkWell(
                      onTap: () => _editBudget(context, ref, s.total),
                      child: const Row(
                        children: [
                          Icon(Icons.edit_rounded, color: Colors.white70, size: 16),
                          SizedBox(width: 4),
                          Text('Edit', style: TextStyle(color: Colors.white70, fontWeight: FontWeight.w600)),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(Fmt.bdt(s.total), style: const TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.w800)),
                const SizedBox(height: 16),
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: LinearProgressIndicator(
                    value: s.usedFraction.clamp(0, 1).toDouble(),
                    minHeight: 10,
                    backgroundColor: Colors.white24,
                    color: s.isOver ? AppColors.danger : s.isNearLimit ? AppColors.warning : AppColors.sunset,
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    _Figure(label: 'Spent', value: Fmt.bdt(s.spent)),
                    const Spacer(),
                    _Figure(
                      label: s.isOver ? 'Over by' : 'Remaining',
                      value: Fmt.bdt(s.remaining.abs()),
                      alignEnd: true,
                    ),
                  ],
                ),
              ],
            ),
          ),
          if (s.isOver || s.isNearLimit) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: (s.isOver ? AppColors.danger : AppColors.warning).withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: (s.isOver ? AppColors.danger : AppColors.warning).withValues(alpha: 0.4)),
              ),
              child: Row(
                children: [
                  Icon(Icons.warning_amber_rounded, color: s.isOver ? AppColors.danger : AppColors.warning),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      s.isOver
                          ? 'Budget alert: you are ${Fmt.bdt(-s.remaining)} over budget.'
                          : 'Heads up: you have used ${(s.usedFraction * 100).round()}% of your budget.',
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 20),
          if (s.byCategory.isNotEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Spending breakdown', style: text.titleMedium),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        SizedBox(
                          height: 150,
                          width: 150,
                          child: PieChart(
                            PieChartData(
                              sectionsSpace: 3,
                              centerSpaceRadius: 42,
                              sections: [
                                for (final e in s.byCategory.entries)
                                  PieChartSectionData(
                                    value: e.value.toDouble(),
                                    color: expenseColor(e.key),
                                    radius: 26,
                                    showTitle: false,
                                  ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(width: 20),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              for (final e in (s.byCategory.entries.toList()..sort((a, b) => b.value.compareTo(a.value))))
                                Padding(
                                  padding: const EdgeInsets.symmetric(vertical: 4),
                                  child: Row(
                                    children: [
                                      Container(
                                        width: 10,
                                        height: 10,
                                        decoration: BoxDecoration(color: expenseColor(e.key), shape: BoxShape.circle),
                                      ),
                                      const SizedBox(width: 8),
                                      Expanded(child: Text(e.key.label, style: const TextStyle(fontSize: 13))),
                                      Text('${(e.value / s.spent * 100).round()}%',
                                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
                                    ],
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(18, 18, 18, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('By category', style: text.titleMedium),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 170,
                    child: BarChart(
                      BarChartData(
                        gridData: const FlGridData(show: false),
                        borderData: FlBorderData(show: false),
                        barTouchData: BarTouchData(enabled: false),
                        titlesData: FlTitlesData(
                          leftTitles: const AxisTitles(),
                          rightTitles: const AxisTitles(),
                          topTitles: const AxisTitles(),
                          bottomTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              reservedSize: 28,
                              getTitlesWidget: (v, meta) => Padding(
                                padding: const EdgeInsets.only(top: 6),
                                child: Icon(expenseIcon(ExpenseCategory.values[v.toInt()]), size: 18, color: AppColors.textSecondary),
                              ),
                            ),
                          ),
                        ),
                        barGroups: [
                          for (final c in ExpenseCategory.values)
                            BarChartGroupData(x: c.index, barRods: [
                              BarChartRodData(
                                toY: (s.byCategory[c] ?? 0).toDouble(),
                                width: 22,
                                color: expenseColor(c),
                                borderRadius: BorderRadius.circular(6),
                              ),
                            ]),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SectionHeader('Expenses', padding: EdgeInsets.fromLTRB(0, 24, 0, 8)),
          if (expenses.isEmpty)
            const Padding(
              padding: EdgeInsets.all(24),
              child: Center(child: Text('No expenses yet — tap + Expense to add one.')),
            ),
          for (final e in expenses)
            Dismissible(
              key: ValueKey(e.id),
              direction: DismissDirection.endToStart,
              onDismissed: (_) => runApi(context, ref.read(expensesProvider.notifier).remove(e.id)),
              background: Container(
                alignment: Alignment.centerRight,
                padding: const EdgeInsets.only(right: 20),
                child: const Icon(Icons.delete_rounded, color: AppColors.danger),
              ),
              child: Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Card(
                  child: ListTile(
                    leading: IconBubble(icon: expenseIcon(e.category), color: expenseColor(e.category)),
                    title: Text(e.title, style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text('${e.category.label} · ${Fmt.date(e.date)}'),
                    trailing: Text(Fmt.bdt(e.amount), style: const TextStyle(fontWeight: FontWeight.w800)),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _Figure extends StatelessWidget {
  const _Figure({required this.label, required this.value, this.alignEnd = false});
  final String label;
  final String value;
  final bool alignEnd;

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: alignEnd ? CrossAxisAlignment.end : CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: Colors.white70, fontSize: 12)),
          Text(value, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 16)),
        ],
      );
}

class _AddExpenseSheet extends ConsumerStatefulWidget {
  const _AddExpenseSheet();

  @override
  ConsumerState<_AddExpenseSheet> createState() => _AddExpenseSheetState();
}

class _AddExpenseSheetState extends ConsumerState<_AddExpenseSheet> {
  final _title = TextEditingController();
  final _amount = TextEditingController();
  ExpenseCategory _cat = ExpenseCategory.food;
  bool _saving = false;

  @override
  void dispose() {
    _title.dispose();
    _amount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(24, 0, 24, 24 + MediaQuery.viewInsetsOf(context).bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Add expense', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          TextField(
            controller: _amount,
            autofocus: true,
            keyboardType: TextInputType.number,
            style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800),
            decoration: const InputDecoration(hintText: '0', prefixText: '৳ '),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _title,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(hintText: 'Description'),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final c in ExpenseCategory.values)
                ChoiceChip(
                  avatar: Icon(expenseIcon(c), size: 18, color: _cat == c ? Colors.white : expenseColor(c)),
                  label: Text(c.label),
                  selected: _cat == c,
                  onSelected: (_) => setState(() => _cat = c),
                ),
            ],
          ),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: _saving
                ? null
                : () async {
                    final amount = int.tryParse(_amount.text.replaceAll(',', ''));
                    if (amount == null || amount <= 0) return;
                    setState(() => _saving = true);
                    final ok = await runApi(
                      context,
                      ref
                          .read(expensesProvider.notifier)
                          .add(Expense(
                            id: uuid.v4(),
                            title: _title.text.trim().isEmpty ? _cat.label : _title.text.trim(),
                            amount: amount,
                            category: _cat,
                            date: DateTime.now(),
                          ))
                          .then((_) => true),
                    );
                    if (!context.mounted) return;
                    setState(() => _saving = false);
                    if (ok == true) Navigator.pop(context);
                  },
            child: const Text('Add expense'),
          ),
        ],
      ),
    );
  }
}
