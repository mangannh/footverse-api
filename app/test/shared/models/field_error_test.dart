import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/shared/models/field_error.dart';

void main() {
  group('FieldError', () {
    // Payload shape from error-spec §1 / §4.
    const json = <String, dynamic>{
      'field': 'email',
      'message': 'must be a valid email',
    };

    test('deserializes from a backend payload', () {
      final error = FieldError.fromJson(json);

      expect(error.field, 'email');
      expect(error.message, 'must be a valid email');
    });

    test('serializes to the same payload (round-trip)', () {
      final error = FieldError.fromJson(json);

      expect(error.toJson(), json);
    });
  });
}
