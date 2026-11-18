import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

/// Where the Spring Boot API lives and whether to use it.
///
///   flutter run                                   → local backend on your PC (see below)
///   flutter run --release / flutter build apk     → deployed backend on Render ([productionUrl])
///   flutter run --dart-define=API_URL=http://192.168.0.5:8080   (any explicit URL wins)
///   flutter run --dart-define=USE_API=false       → offline demo mode (local mock data)
abstract final class ApiConfig {
  static const useApi = bool.fromEnvironment('USE_API', defaultValue: true);
  static const _override = String.fromEnvironment('API_URL');

  /// The Docker container deployed on Render (backend/DEPLOY.md).
  /// Update this if Render gives your service a different URL.
  static const productionUrl = 'https://wanderly-api-latest.onrender.com';

  static String get baseUrl {
    if (_override.isNotEmpty) return _override;
    if (kReleaseMode) return productionUrl;
    // Debug: the Android emulator reaches the host PC at 10.0.2.2; everything else uses localhost.
    if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) return 'http://10.0.2.2:8080';
    return 'http://localhost:8080';
  }
}

/// Error with a user-friendly message, built from the API's problem+json body.
class ApiException implements Exception {
  ApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  bool get isUnauthorized => statusCode == 401;

  factory ApiException.from(Object error) {
    if (error is ApiException) return error;
    if (error is DioException) {
      final res = error.response;
      if (res == null) {
        return ApiException(
          error.type == DioExceptionType.connectionTimeout || error.type == DioExceptionType.connectionError
              ? "Can't reach the Wanderly server at ${ApiConfig.baseUrl}. Is the backend running?"
              : 'Network error — please try again.',
        );
      }
      final data = res.data;
      String? message;
      if (data is Map) {
        final errors = data['errors'];
        if (errors is Map && errors.isNotEmpty) {
          message = errors.entries.map((e) => '${e.key} ${e.value}').join('\n');
        } else {
          message = (data['detail'] ?? data['title'])?.toString();
        }
      }
      return ApiException(
        message ?? (res.statusCode == 401 ? 'Please sign in again.' : 'Server error (${res.statusCode}).'),
        statusCode: res.statusCode,
      );
    }
    return ApiException(error.toString());
  }

  @override
  String toString() => message;
}

/// Creates the configured Dio instance: base URL, timeouts, JWT header and
/// a hook that fires when the server rejects the token.
Dio createDio({required String? Function() token, required void Function() onUnauthorized}) {
  final dio = Dio(BaseOptions(
    baseUrl: ApiConfig.baseUrl,
    connectTimeout: const Duration(seconds: 8),
    // Render's free plan sleeps when idle; the first request can take ~1 minute to wake it.
    receiveTimeout: const Duration(seconds: 75),
    contentType: Headers.jsonContentType,
  ));
  dio.interceptors.add(InterceptorsWrapper(
    onRequest: (options, handler) {
      final t = token();
      if (t != null) options.headers['Authorization'] = 'Bearer $t';
      handler.next(options);
    },
    onError: (e, handler) {
      final isAuthCall = e.requestOptions.path.startsWith('/api/auth/');
      if (e.response?.statusCode == 401 && !isAuthCall) onUnauthorized();
      handler.next(e);
    },
  ));
  if (kDebugMode) {
    dio.interceptors.add(LogInterceptor(requestBody: false, responseBody: false, logPrint: (o) => debugPrint('[api] $o')));
  }
  return dio;
}
