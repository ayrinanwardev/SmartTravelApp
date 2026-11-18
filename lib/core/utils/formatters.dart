import 'package:intl/intl.dart';

abstract final class Fmt {
  static final _bdt = NumberFormat.currency(locale: 'en_US', symbol: '৳', decimalDigits: 0);
  static final _compact = NumberFormat.compact(locale: 'en_US');
  static final _date = DateFormat('d MMM yyyy');
  static final _shortDate = DateFormat('d MMM');
  static final _weekday = DateFormat('EEE, d MMM');

  static String bdt(num value) => _bdt.format(value);
  static String compact(num value) => _compact.format(value);
  static String date(DateTime d) => _date.format(d);
  static String shortDate(DateTime d) => _shortDate.format(d);
  static String weekday(DateTime d) => _weekday.format(d);

  static String range(DateTime start, DateTime end) =>
      start.year == end.year ? '${shortDate(start)} – ${date(end)}' : '${date(start)} – ${date(end)}';
}
