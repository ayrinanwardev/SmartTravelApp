import 'package:dio/dio.dart';

import '../../data/models/destination.dart';
import '../../data/models/expense.dart';
import '../../data/models/hotel.dart';
import '../../data/models/journal_entry.dart';
import '../../data/models/trip.dart';
import 'api_client.dart';

class AuthResult {
  const AuthResult({
    required this.token,
    required this.name,
    required this.email,
    required this.budgetTotal,
    required this.notificationsEnabled,
  });

  final String token;
  final String name;
  final String email;
  final int budgetTotal;
  final bool notificationsEnabled;
}

/// Typed wrapper around the Wanderly Spring Boot REST API (see backend/README.md).
/// Every method throws [ApiException] with a readable message on failure.
class WanderlyApi {
  WanderlyApi(this._dio);

  final Dio _dio;

  String get baseUrl => _dio.options.baseUrl;

  /// "/uploads/a.jpg" → "http://10.0.2.2:8080/uploads/a.jpg"
  String resolveUrl(String url) => url.startsWith('/') ? '$baseUrl$url' : url;

  static String _date(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  Future<T> _call<T>(Future<T> Function() fn) async {
    try {
      return await fn();
    } catch (e) {
      throw ApiException.from(e);
    }
  }

  Future<Map<String, dynamic>> _getMap(String path, {Map<String, dynamic>? query}) =>
      _call(() async => (await _dio.get<Map<String, dynamic>>(path, queryParameters: query)).data!);

  Future<List<dynamic>> _getList(String path, {Map<String, dynamic>? query}) =>
      _call(() async => (await _dio.get<List<dynamic>>(path, queryParameters: query)).data!);

  Future<Map<String, dynamic>> _send(String method, String path, [Object? body]) => _call(() async {
        final res = await _dio.request<dynamic>(path, data: body, options: Options(method: method));
        return res.data is Map<String, dynamic> ? res.data as Map<String, dynamic> : <String, dynamic>{};
      });

  // ------------------------------------------------------------------ auth

  AuthResult _auth(Map<String, dynamic> j) {
    final u = j['user'] as Map<String, dynamic>;
    return AuthResult(
      token: j['token'] as String,
      name: u['name'] as String,
      email: u['email'] as String,
      budgetTotal: (u['budgetTotal'] as int?) ?? 40000,
      notificationsEnabled: (u['notificationsEnabled'] as bool?) ?? true,
    );
  }

  Future<AuthResult> login(String email, String password) async =>
      _auth(await _send('POST', '/api/auth/login', {'email': email, 'password': password}));

  Future<AuthResult> register(String name, String email, String password) async =>
      _auth(await _send('POST', '/api/auth/register', {'name': name, 'email': email, 'password': password}));

  Future<void> updateProfile({String? name, bool? notificationsEnabled}) => _send('PATCH', '/api/users/me', {
        'name': ?name,
        'notificationsEnabled': ?notificationsEnabled,
      });

  // ------------------------------------------------------------- catalogue

  Future<List<Destination>> destinations() async => (await _getList('/api/destinations', query: {'full': true}))
      .map((e) => Destination.fromJson(e as Map<String, dynamic>))
      .toList();

  Future<List<Hotel>> hotels({String? destinationId}) async =>
      (await _getList('/api/hotels', query: {'destinationId': ?destinationId}))
          .map((e) => Hotel.fromJson(e as Map<String, dynamic>))
          .toList();

  // ----------------------------------------------------------------- trips

  Map<String, dynamic> _activityBody(Activity a) =>
      {'title': a.title, 'time': a.time, 'type': a.type.name, 'note': a.note, 'cost': a.cost};

  Future<List<Trip>> trips() async =>
      (await _getList('/api/trips')).map((e) => Trip.fromJson(e as Map<String, dynamic>)).toList();

  Future<Trip> trip(String id) async => Trip.fromJson(await _getMap('/api/trips/$id'));

  /// Creates a trip; [days] (optional) carries a pre-built itinerary, e.g. from the AI planner.
  Future<Trip> createTrip({
    required String destinationId,
    required String title,
    required DateTime startDate,
    required DateTime endDate,
    required int travelers,
    int? budget,
    List<ItineraryDay>? days,
  }) async =>
      Trip.fromJson(await _send('POST', '/api/trips', {
        'destinationId': destinationId,
        'title': title,
        'startDate': _date(startDate),
        'endDate': _date(endDate),
        'travelers': travelers,
        'budget': ?budget,
        if (days != null)
          'days': [
            for (final d in days) {'dayNumber': d.dayNumber, 'activities': d.activities.map(_activityBody).toList()},
          ],
      }));

  Future<void> deleteTrip(String id) => _send('DELETE', '/api/trips/$id');

  Future<Activity> addActivity(String tripId, int dayNumber, Activity a) async =>
      Activity.fromJson(await _send('POST', '/api/trips/$tripId/days/$dayNumber/activities', _activityBody(a)));

  Future<Activity> updateActivity(String tripId, int dayNumber, Activity a) async => Activity.fromJson(
      await _send('PUT', '/api/trips/$tripId/days/$dayNumber/activities/${a.id}', _activityBody(a)));

  Future<void> deleteActivity(String tripId, int dayNumber, String activityId) =>
      _send('DELETE', '/api/trips/$tripId/days/$dayNumber/activities/$activityId');

  Future<void> reorderActivities(String tripId, int dayNumber, List<String> activityIds) =>
      _send('PUT', '/api/trips/$tripId/days/$dayNumber/activities/order', {'activityIds': activityIds});

  // -------------------------------------------------------------- wishlist

  Future<Set<String>> wishlistIds() async => (await _getList('/api/wishlist/ids')).cast<String>().toSet();

  Future<void> saveDestination(String id) => _send('PUT', '/api/wishlist/$id');

  Future<void> unsaveDestination(String id) => _send('DELETE', '/api/wishlist/$id');

  // ---------------------------------------------------------------- budget

  Future<List<Expense>> expenses() async =>
      (await _getList('/api/expenses')).map((e) => Expense.fromJson(e as Map<String, dynamic>)).toList();

  Future<Expense> addExpense(Expense e) async => Expense.fromJson(await _send('POST', '/api/expenses', {
        'title': e.title,
        'amount': e.amount,
        'category': e.category.name,
        'date': _date(e.date),
      }));

  Future<void> deleteExpense(String id) => _send('DELETE', '/api/expenses/$id');

  Future<int> budgetTotal() async => (await _getMap('/api/budget'))['total'] as int;

  Future<void> setBudgetTotal(int total) => _send('PUT', '/api/budget', {'total': total});

  // --------------------------------------------------------------- journal

  Future<List<JournalEntry>> journal() async => (await _getList('/api/journal'))
      .map((e) => JournalEntry.fromApi(e as Map<String, dynamic>, resolveUrl))
      .toList();

  /// Uploads a local photo and returns its server path ("/uploads/...").
  Future<String> uploadPhoto(String filePath) => _call(() async {
        final name = filePath.split(RegExp(r'[\\/]')).last;
        final ext = name.contains('.') ? name.split('.').last.toLowerCase() : 'jpg';
        final type = switch (ext) { 'png' => 'png', 'webp' => 'webp', 'heic' => 'heic', _ => 'jpeg' };
        final form = FormData.fromMap({
          'file': await MultipartFile.fromFile(filePath, filename: name, contentType: DioMediaType('image', type)),
        });
        final res = await _dio.post<Map<String, dynamic>>('/api/journal/photos', data: form);
        return res.data!['url'] as String;
      });

  Future<JournalEntry> createJournalEntry(JournalEntry e, List<String> photoUrls) async =>
      JournalEntry.fromApi(
        await _send('POST', '/api/journal', {
          'title': e.title,
          'body': e.body,
          'location': e.location,
          'date': _date(e.date),
          'album': e.album,
          'photoUrls': photoUrls,
        }),
        resolveUrl,
      );

  Future<void> deleteJournalEntry(String id) => _send('DELETE', '/api/journal/$id');

  // -------------------------------------------------------------- bookings

  Future<List<Booking>> bookings() async =>
      (await _getList('/api/bookings')).map((e) => Booking.fromJson(e as Map<String, dynamic>)).toList();

  Future<Booking> book({
    required String hotelId,
    required DateTime checkIn,
    required DateTime checkOut,
    required int guests,
    required int rooms,
  }) async =>
      Booking.fromJson(await _send('POST', '/api/bookings', {
        'hotelId': hotelId,
        'checkIn': _date(checkIn),
        'checkOut': _date(checkOut),
        'guests': guests,
        'rooms': rooms,
      }));

  // -------------------------------------------------------------------- AI

  Future<Map<String, dynamic>> aiPlan(String prompt) => _send('POST', '/api/ai/plan', {'prompt': prompt});
}
