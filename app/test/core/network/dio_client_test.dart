import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/network/dio_client.dart';
import 'package:footverse/core/network/error_interceptor.dart';
import 'package:footverse/core/network/logging_interceptor.dart';

import '../../support/dio_test_helpers.dart';

void main() {
  group('createDio', () {
    test('configures the base URL and JSON defaults', () {
      final dio = createDio(baseUrl: 'http://test.local');

      expect(dio.options.baseUrl, 'http://test.local');
      expect(dio.options.contentType, Headers.jsonContentType);
      expect(dio.options.responseType, ResponseType.json);
    });

    test('registers the ErrorInterceptor', () {
      final dio = createDio(baseUrl: 'http://test.local');

      expect(dio.interceptors.whereType<ErrorInterceptor>(), isNotEmpty);
    });

    test('registers the LoggingInterceptor in debug builds', () {
      // `flutter test` runs under kDebugMode, so the debug-only interceptor is
      // present here; in a release build the kDebugMode guard excludes it.
      final dio = createDio(baseUrl: 'http://test.local');

      expect(dio.interceptors.whereType<LoggingInterceptor>(), isNotEmpty);
    });

    test('no interceptor adds an Authorization header', () async {
      RequestOptions? captured;
      final dio = createDio(baseUrl: 'http://test.local');
      dio.httpClientAdapter = FakeHttpClientAdapter((options) async {
        captured = options;
        return jsonResponseBody(200, <String, dynamic>{
          'success': true,
          'message': 'OK',
          'timestamp': '2025-01-15T10:30:00',
        });
      });

      await dio.get('/anything');

      expect(captured, isNotNull);
      expect(captured!.headers.containsKey('Authorization'), isFalse);
    });
  });
}
