import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/remote_sync.dart';
import '../../../core/providers.dart';
import '../../../data/local/local_store.dart';
import '../../../data/models/expense.dart';

class ExpensesNotifier extends Notifier<List<Expense>> with RemoteSync<List<Expense>> {
  @override
  List<Expense> build() {
    if (watchApi() != null) return loadRemote(const []);
    return ref.watch(localStoreProvider).readList(LocalStore.expensesKey, Expense.fromJson) ?? _seed();
  }

  @override
  Future<void> refresh() => safeRefresh((a) => a.expenses());

  void _set(List<Expense> list) {
    state = list;
    if (api == null) ref.read(localStoreProvider).writeList(LocalStore.expensesKey, list, (e) => e.toJson());
  }

  Future<void> add(Expense e) async {
    final client = api;
    final saved = client == null ? e : await client.addExpense(e);
    _set([saved, ...state]..sort((a, b) => b.date.compareTo(a.date)));
  }

  /// Removed locally at once (swipe-to-delete), then on the server.
  Future<void> remove(String id) async {
    final previous = state;
    _set(state.where((e) => e.id != id).toList());
    final client = api;
    if (client == null) return;
    try {
      await client.deleteExpense(id);
    } catch (_) {
      state = previous;
      rethrow;
    }
  }

  List<Expense> _seed() {
    final now = DateTime.now();
    return [
      Expense(id: 'e1', title: 'Dhaka → Cox\'s Bazar flights', amount: 9000, category: ExpenseCategory.flight, date: now.subtract(const Duration(days: 2))),
      Expense(id: 'e2', title: 'Sea Pearl Resort deposit', amount: 9500, category: ExpenseCategory.hotel, date: now.subtract(const Duration(days: 3))),
      Expense(id: 'e3', title: 'Beach gear', amount: 2200, category: ExpenseCategory.shopping, date: now.subtract(const Duration(days: 4))),
      Expense(id: 'e4', title: 'Airport transfer', amount: 800, category: ExpenseCategory.transport, date: now.subtract(const Duration(days: 4))),
    ];
  }
}

final expensesProvider = NotifierProvider<ExpensesNotifier, List<Expense>>(ExpensesNotifier.new);

class BudgetTotalNotifier extends Notifier<int> with RemoteSync<int> {
  @override
  int build() {
    final cached = ref.watch(localStoreProvider).getInt(LocalStore.budgetKey) ?? 40000;
    if (watchApi() != null) return loadRemote(cached);
    return cached;
  }

  @override
  Future<void> refresh() => safeRefresh((a) => a.budgetTotal());

  Future<void> set(int value) async {
    final previous = state;
    state = value;
    await ref.read(localStoreProvider).setInt(LocalStore.budgetKey, value);
    final client = api;
    if (client == null) return;
    try {
      await client.setBudgetTotal(value);
    } catch (_) {
      state = previous;
      rethrow;
    }
  }
}

final budgetTotalProvider = NotifierProvider<BudgetTotalNotifier, int>(BudgetTotalNotifier.new);

class BudgetSummary {
  const BudgetSummary({required this.total, required this.spent, required this.byCategory});
  final int total;
  final int spent;
  final Map<ExpenseCategory, int> byCategory;

  int get remaining => total - spent;
  double get usedFraction => total == 0 ? 0 : spent / total;
  bool get isOver => spent > total;
  bool get isNearLimit => !isOver && usedFraction >= 0.8;
}

final budgetSummaryProvider = Provider<BudgetSummary>((ref) {
  final expenses = ref.watch(expensesProvider);
  final total = ref.watch(budgetTotalProvider);
  final by = <ExpenseCategory, int>{};
  for (final e in expenses) {
    by[e.category] = (by[e.category] ?? 0) + e.amount;
  }
  return BudgetSummary(total: total, spent: expenses.fold(0, (s, e) => s + e.amount), byCategory: by);
});
