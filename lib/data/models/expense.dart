enum ExpenseCategory {
  flight('Flights'),
  hotel('Hotels'),
  food('Food'),
  shopping('Shopping'),
  transport('Local transport'),
  activities('Activities');

  const ExpenseCategory(this.label);
  final String label;
}

class Expense {
  const Expense({
    required this.id,
    required this.title,
    required this.amount,
    required this.category,
    required this.date,
  });

  final String id;
  final String title;
  final int amount;
  final ExpenseCategory category;
  final DateTime date;

  Map<String, dynamic> toJson() =>
      {'id': id, 'title': title, 'amount': amount, 'category': category.name, 'date': date.toIso8601String()};

  factory Expense.fromJson(Map<String, dynamic> j) => Expense(
        id: j['id'] as String,
        title: j['title'] as String,
        amount: j['amount'] as int,
        category: ExpenseCategory.values.byName(j['category'] as String),
        date: DateTime.parse(j['date'] as String),
      );
}
