import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/product/repositories/brand_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'brand_repository_test.mocks.dart';

Response<Map<String, dynamic>> _listResponse() =>
    Response<Map<String, dynamic>>(
      requestOptions: RequestOptions(path: '/api/v1/brands'),
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 5,
            'name': 'Nike',
            'logoUrl': 'https://cdn.example.com/nike.png',
            'description': 'Just do it',
          },
          <String, dynamic>{
            'id': 6,
            'name': 'Adidas',
            'logoUrl': null,
            'description': null,
          },
        ],
        'timestamp': '2025-01-15T10:30:00',
      },
    );

@GenerateNiceMocks([MockSpec<Dio>()])
void main() {
  late MockDio dio;
  late BrandRepository repository;

  setUp(() {
    dio = MockDio();
    repository = BrandRepository(dio);
  });

  test('GETs the brands path and returns the typed list', () async {
    when(
      dio.get<Map<String, dynamic>>(any),
    ).thenAnswer((_) async => _listResponse());

    final brands = await repository.getBrands();

    final captured = verify(dio.get<Map<String, dynamic>>(captureAny)).captured;
    expect(captured[0], '/api/v1/brands');
    expect(brands, hasLength(2));
    expect(brands.first.name, 'Nike');
    expect(brands.last.logoUrl, isNull);
  });

  test('surfaces a transport error as a typed AppException', () async {
    when(dio.get<Map<String, dynamic>>(any)).thenThrow(
      DioException(
        requestOptions: RequestOptions(path: '/api/v1/brands'),
        error: const AppException(message: 'Unable to reach the server.'),
      ),
    );

    await expectLater(repository.getBrands(), throwsA(isA<AppException>()));
  });
}
