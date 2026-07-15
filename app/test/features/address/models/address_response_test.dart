import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/address/models/address_request.dart';
import 'package:footverse/features/address/models/address_response.dart';

Map<String, dynamic> _addressJson() => <String, dynamic>{
  'id': 42,
  'recipientName': 'Nguyen Van A',
  'recipientPhone': '0901234567',
  'province': 'Hà Nội',
  'district': 'Cầu Giấy',
  'ward': 'Dịch Vọng',
  'streetAddress': '123 Xuân Thủy',
  'isDefault': true,
};

void main() {
  group('AddressResponse (dto-spec §8)', () {
    test('deserializes a real backend payload including isDefault', () {
      final address = AddressResponse.fromJson(_addressJson());

      expect(address.id, 42);
      expect(address.recipientName, 'Nguyen Van A');
      expect(address.recipientPhone, '0901234567');
      expect(address.province, 'Hà Nội');
      expect(address.district, 'Cầu Giấy');
      expect(address.ward, 'Dịch Vọng');
      expect(address.streetAddress, '123 Xuân Thủy');
      expect(address.isDefault, isTrue);
    });

    test('maps isDefault false', () {
      final address = AddressResponse.fromJson(
        _addressJson()..['isDefault'] = false,
      );

      expect(address.isDefault, isFalse);
    });
  });

  group('AddressRequest (dto-spec §8, validation-spec §2)', () {
    test('serializes every write field including isDefault', () {
      const request = AddressRequest(
        recipientName: 'Nguyen Van A',
        recipientPhone: '0901234567',
        province: 'Hà Nội',
        district: 'Cầu Giấy',
        ward: 'Dịch Vọng',
        streetAddress: '123 Xuân Thủy',
        isDefault: true,
      );

      expect(request.toJson(), <String, dynamic>{
        'recipientName': 'Nguyen Van A',
        'recipientPhone': '0901234567',
        'province': 'Hà Nội',
        'district': 'Cầu Giấy',
        'ward': 'Dịch Vọng',
        'streetAddress': '123 Xuân Thủy',
        'isDefault': true,
      });
    });

    test('omits the optional isDefault when it is not set', () {
      const request = AddressRequest(
        recipientName: 'Nguyen Van A',
        recipientPhone: '0901234567',
        province: 'Hà Nội',
        district: 'Cầu Giấy',
        ward: 'Dịch Vọng',
        streetAddress: '123 Xuân Thủy',
      );

      expect(request.toJson().containsKey('isDefault'), isFalse);
    });
  });
}
