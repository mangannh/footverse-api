import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/address/models/address_request.dart';
import 'package:footverse/features/address/models/address_response.dart';
import 'package:footverse/features/address/providers/address_provider.dart';
import 'package:footverse/features/address/repositories/address_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'address_provider_test.mocks.dart';

AddressResponse _address({int id = 1, bool isDefault = true}) =>
    AddressResponse(
      id: id,
      recipientName: 'Nguyen Van A',
      recipientPhone: '0901234567',
      province: 'Hà Nội',
      district: 'Cầu Giấy',
      ward: 'Dịch Vọng',
      streetAddress: '123 Xuân Thủy',
      isDefault: isDefault,
    );

const AddressRequest _request = AddressRequest(
  recipientName: 'Nguyen Van A',
  recipientPhone: '0901234567',
  province: 'Hà Nội',
  district: 'Cầu Giấy',
  ward: 'Dịch Vọng',
  streetAddress: '123 Xuân Thủy',
  isDefault: true,
);

const AppException _forbidden = AppException(
  message: 'You cannot access this address',
  statusCode: 403,
  errorCode: 'ADDRESS_FORBIDDEN',
);

const AppException _defaultNotDeletable = AppException(
  message: 'Choose another default address before deleting the current default',
  statusCode: 409,
  errorCode: 'ADDRESS_DEFAULT_NOT_DELETABLE',
);

@GenerateNiceMocks([MockSpec<AddressRepository>()])
void main() {
  late MockAddressRepository repository;
  late AddressProvider provider;

  setUp(() {
    repository = MockAddressRepository();
    provider = AddressProvider(repository);
  });

  group('load', () {
    test('moves loading → ready and exposes the returned addresses', () async {
      final statuses = <AddressListStatus>[];
      provider.addListener(() => statuses.add(provider.status));
      when(
        repository.getAddresses(),
      ).thenAnswer((_) async => <AddressResponse>[_address()]);

      await provider.load();

      expect(statuses, <AddressListStatus>[
        AddressListStatus.loading,
        AddressListStatus.ready,
      ]);
      expect(provider.status, AddressListStatus.ready);
      expect(provider.addresses, hasLength(1));
      expect(provider.addresses.first.isDefault, isTrue);
      expect(provider.isEmpty, isFalse);
      expect(provider.error, isNull);
    });

    test('a successful but empty load reports the empty state', () async {
      when(
        repository.getAddresses(),
      ).thenAnswer((_) async => <AddressResponse>[]);

      await provider.load();

      expect(provider.status, AddressListStatus.ready);
      expect(provider.isEmpty, isTrue);
    });

    test('a failed load moves to error and preserves the exception', () async {
      final statuses = <AddressListStatus>[];
      provider.addListener(() => statuses.add(provider.status));
      when(repository.getAddresses()).thenThrow(_forbidden);

      await provider.load();

      expect(statuses, <AddressListStatus>[
        AddressListStatus.loading,
        AddressListStatus.error,
      ]);
      expect(provider.status, AddressListStatus.error);
      expect(provider.error, same(_forbidden));
      expect(provider.isEmpty, isFalse);
    });
  });

  group('retry', () {
    test('re-runs the load and recovers from an earlier error', () async {
      when(repository.getAddresses()).thenThrow(_forbidden);
      await provider.load();
      expect(provider.status, AddressListStatus.error);

      when(
        repository.getAddresses(),
      ).thenAnswer((_) async => <AddressResponse>[_address()]);
      await provider.retry();

      expect(provider.status, AddressListStatus.ready);
      expect(provider.error, isNull);
      expect(provider.addresses, hasLength(1));
    });
  });

  group('createAddress', () {
    test('creates then reloads the list from the server', () async {
      when(
        repository.getAddresses(),
      ).thenAnswer((_) async => <AddressResponse>[_address(id: 5)]);
      when(
        repository.createAddress(any),
      ).thenAnswer((_) async => _address(id: 5));

      await provider.createAddress(_request);

      verify(repository.createAddress(_request)).called(1);
      verify(repository.getAddresses()).called(1);
      expect(provider.status, AddressListStatus.ready);
      expect(provider.addresses.single.id, 5);
    });

    test('rethrows and does not reload when the create fails', () async {
      when(repository.createAddress(any)).thenThrow(_forbidden);

      await expectLater(
        provider.createAddress(_request),
        throwsA(same(_forbidden)),
      );
      verifyNever(repository.getAddresses());
    });
  });

  group('updateAddress', () {
    test('updates then reloads the list from the server', () async {
      when(
        repository.getAddresses(),
      ).thenAnswer((_) async => <AddressResponse>[_address(id: 7)]);
      when(
        repository.updateAddress(any, any),
      ).thenAnswer((_) async => _address(id: 7));

      await provider.updateAddress(7, _request);

      verify(repository.updateAddress(7, _request)).called(1);
      verify(repository.getAddresses()).called(1);
      expect(provider.addresses.single.id, 7);
    });
  });

  group('deleteAddress', () {
    test('deletes then reloads the list from the server', () async {
      when(
        repository.getAddresses(),
      ).thenAnswer((_) async => <AddressResponse>[]);
      when(repository.deleteAddress(any)).thenAnswer((_) async {});

      await provider.deleteAddress(3);

      verify(repository.deleteAddress(3)).called(1);
      verify(repository.getAddresses()).called(1);
      expect(provider.isEmpty, isTrue);
    });

    test('rethrows ADDRESS_DEFAULT_NOT_DELETABLE without reloading', () async {
      when(repository.deleteAddress(any)).thenThrow(_defaultNotDeletable);

      await expectLater(
        provider.deleteAddress(1),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'ADDRESS_DEFAULT_NOT_DELETABLE',
          ),
        ),
      );
      verifyNever(repository.getAddresses());
    });
  });
}
