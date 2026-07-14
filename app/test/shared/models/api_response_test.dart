import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/shared/models/api_response.dart';
import 'package:footverse/shared/models/field_error.dart';

/// Deserializes the generic payload as a [FieldError] — the only concrete
/// wire model available at this stage — exercising the `fromJsonT` callback.
FieldError _fieldErrorFromJson(Object? json) =>
    FieldError.fromJson(json! as Map<String, dynamic>);

void main() {
  group('ApiResponse — success envelope', () {
    // ApiResponse.ok(data) shape (dto-spec §5): success = true, message = "OK",
    // errorCode / errors omitted.
    final json = <String, dynamic>{
      'success': true,
      'message': 'OK',
      'data': <String, dynamic>{'field': 'email', 'message': 'ok'},
      'timestamp': '2025-01-15T10:30:00',
    };

    test('deserializes the envelope and its generic payload', () {
      final response = ApiResponse<FieldError>.fromJson(
        json,
        _fieldErrorFromJson,
      );

      expect(response.success, isTrue);
      expect(response.message, 'OK');
      expect(response.data, isA<FieldError>());
      expect(response.data!.field, 'email');
      expect(response.errorCode, isNull);
      expect(response.errors, isNull);
      expect(response.timestamp, DateTime.parse('2025-01-15T10:30:00'));
    });

    test('round-trips through toJson / fromJson', () {
      final original = ApiResponse<FieldError>.fromJson(
        json,
        _fieldErrorFromJson,
      );

      final encoded =
          jsonDecode(jsonEncode(original.toJson((value) => value.toJson())))
              as Map<String, dynamic>;
      final restored = ApiResponse<FieldError>.fromJson(
        encoded,
        _fieldErrorFromJson,
      );

      expect(restored.success, original.success);
      expect(restored.message, original.message);
      expect(restored.data!.field, original.data!.field);
      expect(restored.timestamp, original.timestamp);
    });
  });

  group('ApiResponse — business error envelope', () {
    // error-spec §8.2: USER_EMAIL_DUPLICATED, no data, no errors.
    final json = <String, dynamic>{
      'success': false,
      'message': 'Email already exists',
      'errorCode': 'USER_EMAIL_DUPLICATED',
      'timestamp': '2025-01-15T10:30:00',
    };

    test('deserializes the error code with a null payload', () {
      final response = ApiResponse<FieldError>.fromJson(
        json,
        _fieldErrorFromJson,
      );

      expect(response.success, isFalse);
      expect(response.data, isNull);
      expect(response.errorCode, 'USER_EMAIL_DUPLICATED');
      expect(response.message, 'Email already exists');
      expect(response.errors, isNull);
    });
  });

  group('ApiResponse — validation error envelope', () {
    // error-spec §3 / §4: VALIDATION_ERROR with a populated errors[] array.
    final json = <String, dynamic>{
      'success': false,
      'message': 'Validation failed',
      'errorCode': 'VALIDATION_ERROR',
      'errors': <Map<String, dynamic>>[
        <String, dynamic>{
          'field': 'quantity',
          'message': 'must be greater than or equal to 1',
        },
      ],
      'timestamp': '2025-01-15T10:30:00',
    };

    test('deserializes the field-level errors', () {
      final response = ApiResponse<FieldError>.fromJson(
        json,
        _fieldErrorFromJson,
      );

      expect(response.success, isFalse);
      expect(response.errorCode, 'VALIDATION_ERROR');
      expect(response.errors, hasLength(1));
      expect(response.errors!.first.field, 'quantity');
      expect(
        response.errors!.first.message,
        'must be greater than or equal to 1',
      );
    });

    test('round-trips the errors[] array', () {
      final original = ApiResponse<FieldError>.fromJson(
        json,
        _fieldErrorFromJson,
      );

      final encoded =
          jsonDecode(jsonEncode(original.toJson((value) => value.toJson())))
              as Map<String, dynamic>;
      final restored = ApiResponse<FieldError>.fromJson(
        encoded,
        _fieldErrorFromJson,
      );

      expect(restored.errorCode, 'VALIDATION_ERROR');
      expect(restored.errors, hasLength(1));
      expect(restored.errors!.first.field, 'quantity');
    });
  });
}
