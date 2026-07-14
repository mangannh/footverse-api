import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/core/network/dio_client.dart';

import '../../support/dio_test_helpers.dart';

/// Builds the app's configured [Dio] and swaps in a fake adapter so the real
/// interceptor chain runs without a socket.
Dio _dioReturning(int statusCode, Map<String, dynamic> body) {
  final dio = createDio(baseUrl: 'http://test.local');
  dio.httpClientAdapter = FakeHttpClientAdapter(
    (options) async => jsonResponseBody(statusCode, body),
  );
  return dio;
}

Map<String, dynamic> _errorEnvelope(
  String message,
  String errorCode, {
  List<Map<String, dynamic>>? errors,
}) => <String, dynamic>{
  'success': false,
  'message': message,
  'errorCode': errorCode,
  'errors': ?errors,
  'timestamp': '2025-01-15T10:30:00',
};

Future<AppException> _captureAppException(Future<void> Function() call) async {
  try {
    await call();
    fail('Expected the request to fail');
  } on DioException catch (e) {
    // The interceptor must never let a raw DioException escape without a mapped
    // AppException attached to `error`.
    expect(e.error, isA<AppException>());
    return e.error! as AppException;
  }
}

void main() {
  group('ErrorInterceptor — enveloped server errors', () {
    test('maps a 400 VALIDATION_ERROR envelope', () async {
      final dio = _dioReturning(
        400,
        _errorEnvelope(
          'Validation failed',
          'VALIDATION_ERROR',
          errors: [
            {
              'field': 'quantity',
              'message': 'must be greater than or equal to 1',
            },
          ],
        ),
      );

      final exception = await _captureAppException(() => dio.get('/anything'));

      expect(exception.statusCode, 400);
      expect(exception.errorCode, 'VALIDATION_ERROR');
      expect(exception.message, 'Validation failed');
      expect(exception.errors, hasLength(1));
      expect(exception.errors!.first.field, 'quantity');
      expect(exception.isNetworkError, isFalse);
    });

    test('maps a 401 UNAUTHORIZED envelope', () async {
      final dio = _dioReturning(
        401,
        _errorEnvelope('Authentication required', 'UNAUTHORIZED'),
      );

      final exception = await _captureAppException(() => dio.get('/anything'));

      expect(exception.statusCode, 401);
      expect(exception.errorCode, 'UNAUTHORIZED');
      expect(exception.errors, isNull);
      expect(exception.isNetworkError, isFalse);
    });

    test('maps a 404 PRODUCT_NOT_FOUND envelope', () async {
      final dio = _dioReturning(
        404,
        _errorEnvelope('Product not found', 'PRODUCT_NOT_FOUND'),
      );

      final exception = await _captureAppException(() => dio.get('/anything'));

      expect(exception.statusCode, 404);
      expect(exception.errorCode, 'PRODUCT_NOT_FOUND');
      expect(exception.isNetworkError, isFalse);
    });

    test('maps a 409 USER_EMAIL_DUPLICATED envelope', () async {
      final dio = _dioReturning(
        409,
        _errorEnvelope('Email already exists', 'USER_EMAIL_DUPLICATED'),
      );

      final exception = await _captureAppException(() => dio.get('/anything'));

      expect(exception.statusCode, 409);
      expect(exception.errorCode, 'USER_EMAIL_DUPLICATED');
      expect(exception.isNetworkError, isFalse);
    });
  });

  group('ErrorInterceptor — transport failures', () {
    test('maps a connection failure to a network AppException', () async {
      final dio = createDio(baseUrl: 'http://test.local');
      dio.httpClientAdapter = FakeHttpClientAdapter(
        (options) async => throw DioException.connectionError(
          requestOptions: options,
          reason: 'failed to connect',
        ),
      );

      final exception = await _captureAppException(() => dio.get('/anything'));

      expect(exception.isNetworkError, isTrue);
      expect(exception.statusCode, isNull);
      expect(exception.errorCode, isNull);
    });

    test(
      'maps a non-enveloped error body to a status-only AppException',
      () async {
        final dio = createDio(baseUrl: 'http://test.local');
        dio.httpClientAdapter = FakeHttpClientAdapter(
          (options) async =>
              ResponseBody.fromString('<html>gateway</html>', 502),
        );

        final exception = await _captureAppException(
          () => dio.get('/anything'),
        );

        expect(exception.statusCode, 502);
        expect(exception.isNetworkError, isFalse);
      },
    );
  });
}
