import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/address/validators/address_validators.dart';

/// Verifies the client validators mirror the frozen constraints of
/// validation-spec §5 — all fields `@NotBlank`, `recipientPhone` the VN
/// `@Pattern("^0\d{9}$")` including the spec's "Example invalid" values — and
/// never reject input the server accepts (assumption 5).
void main() {
  group('recipientName', () {
    test('required when blank', () {
      expect(AddressValidators.recipientName(null), isNotNull);
      expect(AddressValidators.recipientName(''), isNotNull);
      expect(AddressValidators.recipientName('   '), isNotNull);
    });

    test('accepts a non-blank value', () {
      expect(AddressValidators.recipientName('Nguyen Van A'), isNull);
    });
  });

  group('recipientPhone', () {
    test('required when blank', () {
      expect(AddressValidators.recipientPhone(null), isNotNull);
      expect(AddressValidators.recipientPhone(''), isNotNull);
    });

    test('rejects the spec example invalid "12345"', () {
      expect(AddressValidators.recipientPhone('12345'), isNotNull);
    });

    test('rejects a 10-digit number that does not start with 0', () {
      expect(AddressValidators.recipientPhone('9876543210'), isNotNull);
    });

    test('accepts a valid VN number (10 digits, starts with 0)', () {
      expect(AddressValidators.recipientPhone('0901234567'), isNull);
    });
  });

  group('province', () {
    test('required when blank', () {
      expect(AddressValidators.province(''), isNotNull);
      expect(AddressValidators.province('   '), isNotNull);
      expect(AddressValidators.province('Hà Nội'), isNull);
    });
  });

  group('district', () {
    test('required when blank', () {
      expect(AddressValidators.district(''), isNotNull);
      expect(AddressValidators.district('Cầu Giấy'), isNull);
    });
  });

  group('ward', () {
    test('required when blank', () {
      expect(AddressValidators.ward(''), isNotNull);
      expect(AddressValidators.ward('Dịch Vọng'), isNull);
    });
  });

  group('streetAddress', () {
    test('required when blank', () {
      expect(AddressValidators.streetAddress(''), isNotNull);
      expect(AddressValidators.streetAddress('123 Xuân Thủy'), isNull);
    });
  });
}
