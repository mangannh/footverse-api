import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/shared/models/field_error.dart';

void main() {
  group('AppException', () {
    test('carries every field of a backend error envelope', () {
      const errors = <FieldError>[
        FieldError(field: 'email', message: 'must be a valid email'),
      ];

      const exception = AppException(
        message: 'Validation failed',
        statusCode: 400,
        errorCode: 'VALIDATION_ERROR',
        errors: errors,
      );

      expect(exception.message, 'Validation failed');
      expect(exception.statusCode, 400);
      expect(exception.errorCode, 'VALIDATION_ERROR');
      expect(exception.errors, errors);
      expect(exception.isNetworkError, isFalse);
    });

    test('represents a network failure with no response', () {
      const exception = AppException.network(message: 'No connection');

      expect(exception.message, 'No connection');
      expect(exception.isNetworkError, isTrue);
      expect(exception.statusCode, isNull);
      expect(exception.errorCode, isNull);
      expect(exception.errors, isNull);
    });
  });
}
