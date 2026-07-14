import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/auth/validators/auth_validators.dart';

/// Verifies the client validators mirror the frozen constraints of
/// validation-spec §1/§3 — including the exact "Example invalid" values the
/// spec lists — and never reject input the server accepts (assumption 5).
void main() {
  group('email', () {
    test('required when blank', () {
      expect(AuthValidators.email(null), isNotNull);
      expect(AuthValidators.email(''), isNotNull);
      expect(AuthValidators.email('   '), isNotNull);
    });

    test('rejects the spec example invalid "foo@"', () {
      expect(AuthValidators.email('foo@'), isNotNull);
    });

    test('accepts valid emails (no stricter than the server @Email)', () {
      expect(AuthValidators.email('user@example.com'), isNull);
      // Hibernate @Email accepts a dot-less domain, so the client must too.
      expect(AuthValidators.email('foo@bar'), isNull);
    });
  });

  group('password (register)', () {
    test('required when empty', () {
      expect(AuthValidators.password(''), isNotNull);
    });

    test('rejects the spec example invalids', () {
      expect(AuthValidators.password('pass'), isNotNull); // too short
      expect(AuthValidators.password('12345678'), isNotNull); // no letter
      expect(AuthValidators.password('abcdefgh'), isNotNull); // no digit
    });

    test('accepts min 8 with a letter and a digit', () {
      expect(AuthValidators.password('abc12345'), isNull);
    });
  });

  group('requiredPassword (login)', () {
    test('only requires non-empty', () {
      expect(AuthValidators.requiredPassword(''), isNotNull);
      expect(AuthValidators.requiredPassword('x'), isNull);
    });
  });

  group('fullName', () {
    test('required when blank', () {
      expect(AuthValidators.fullName(''), isNotNull);
      expect(AuthValidators.fullName('   '), isNotNull);
      expect(AuthValidators.fullName('Nguyen Van A'), isNull);
    });
  });

  group('phone', () {
    test('required when blank', () {
      expect(AuthValidators.phone(''), isNotNull);
    });

    test('rejects the spec example invalids', () {
      expect(AuthValidators.phone('123'), isNotNull); // too short
      expect(
        AuthValidators.phone('9876543210'),
        isNotNull,
      ); // must start with 0
    });

    test('accepts a valid VN number (10 digits, starts with 0)', () {
      expect(AuthValidators.phone('0901234567'), isNull);
    });
  });
}
