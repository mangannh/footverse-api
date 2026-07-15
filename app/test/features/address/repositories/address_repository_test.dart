import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/address/models/address_request.dart';
import 'package:footverse/features/address/repositories/address_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'address_repository_test.mocks.dart';

Map<String, dynamic> _addressData({bool isDefault = true}) => <String, dynamic>{
  'id': 42,
  'recipientName': 'Nguyen Van A',
  'recipientPhone': '0901234567',
  'province': 'Hà Nội',
  'district': 'Cầu Giấy',
  'ward': 'Dịch Vọng',
  'streetAddress': '123 Xuân Thủy',
  'isDefault': isDefault,
};

Response<Map<String, dynamic>> _singleResponse(String path) =>
    Response<Map<String, dynamic>>(
      requestOptions: RequestOptions(path: path),
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': _addressData(),
        'timestamp': '2025-01-15T10:30:00',
      },
    );

Response<Map<String, dynamic>> _listResponse() =>
    Response<Map<String, dynamic>>(
      requestOptions: RequestOptions(path: '/api/v1/addresses'),
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': <Map<String, dynamic>>[
          _addressData(),
          _addressData(isDefault: false)..['id'] = 43,
        ],
        'timestamp': '2025-01-15T10:30:00',
      },
    );

DioException _errorWith(String errorCode, int statusCode) => DioException(
  requestOptions: RequestOptions(path: '/api/v1/addresses'),
  error: AppException(
    message: 'error',
    statusCode: statusCode,
    errorCode: errorCode,
  ),
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

@GenerateNiceMocks([MockSpec<Dio>()])
void main() {
  late MockDio dio;
  late AddressRepository repository;

  setUp(() {
    dio = MockDio();
    repository = AddressRepository(dio);
  });

  group('getAddresses', () {
    test('GETs the addresses path and returns the typed list', () async {
      when(
        dio.get<Map<String, dynamic>>(any),
      ).thenAnswer((_) async => _listResponse());

      final addresses = await repository.getAddresses();

      final captured = verify(
        dio.get<Map<String, dynamic>>(captureAny),
      ).captured;
      expect(captured[0], '/api/v1/addresses');
      expect(addresses, hasLength(2));
      expect(addresses.first.id, 42);
      expect(addresses.first.isDefault, isTrue);
      expect(addresses.last.isDefault, isFalse);
    });

    test('surfaces a transport error as a typed AppException', () async {
      when(
        dio.get<Map<String, dynamic>>(any),
      ).thenThrow(_errorWith('ADDRESS_FORBIDDEN', 403));

      await expectLater(
        repository.getAddresses(),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'ADDRESS_FORBIDDEN',
          ),
        ),
      );
    });
  });

  group('createAddress', () {
    test('POSTs the addresses path with the body, returns typed', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenAnswer((_) async => _singleResponse('/api/v1/addresses'));

      final result = await repository.createAddress(_request);

      final captured = verify(
        dio.post<Map<String, dynamic>>(
          captureAny,
          data: captureAnyNamed('data'),
        ),
      ).captured;
      expect(captured[0], '/api/v1/addresses');
      expect(captured[1], _request.toJson());
      expect(result.id, 42);
      expect(result.recipientName, 'Nguyen Van A');
    });
  });

  group('updateAddress', () {
    test('PUTs the address id path with the body, returns typed', () async {
      when(
        dio.put<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenAnswer((_) async => _singleResponse('/api/v1/addresses/42'));

      final result = await repository.updateAddress(42, _request);

      final captured = verify(
        dio.put<Map<String, dynamic>>(
          captureAny,
          data: captureAnyNamed('data'),
        ),
      ).captured;
      expect(captured[0], '/api/v1/addresses/42');
      expect(captured[1], _request.toJson());
      expect(result.id, 42);
    });

    test('surfaces ADDRESS_NOT_FOUND as a typed AppException', () async {
      when(
        dio.put<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenThrow(_errorWith('ADDRESS_NOT_FOUND', 404));

      await expectLater(
        repository.updateAddress(99, _request),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'ADDRESS_NOT_FOUND',
          ),
        ),
      );
    });
  });

  group('deleteAddress', () {
    test('DELETEs the address id path', () async {
      when(dio.delete<void>(any)).thenAnswer(
        (_) async => Response<void>(
          requestOptions: RequestOptions(path: '/api/v1/addresses/42'),
          statusCode: 200,
        ),
      );

      await repository.deleteAddress(42);

      final captured = verify(dio.delete<void>(captureAny)).captured;
      expect(captured[0], '/api/v1/addresses/42');
    });

    test(
      'surfaces ADDRESS_DEFAULT_NOT_DELETABLE as a typed AppException',
      () async {
        when(
          dio.delete<void>(any),
        ).thenThrow(_errorWith('ADDRESS_DEFAULT_NOT_DELETABLE', 409));

        await expectLater(
          repository.deleteAddress(42),
          throwsA(
            isA<AppException>().having(
              (e) => e.errorCode,
              'errorCode',
              'ADDRESS_DEFAULT_NOT_DELETABLE',
            ),
          ),
        );
      },
    );
  });
}
