import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/product/repositories/category_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'category_repository_test.mocks.dart';

Response<Map<String, dynamic>> _listResponse() =>
    Response<Map<String, dynamic>>(
      requestOptions: RequestOptions(path: '/api/v1/categories'),
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': <Map<String, dynamic>>[
          <String, dynamic>{
            'id': 3,
            'name': 'Running',
            'description': 'Running shoes',
          },
          <String, dynamic>{'id': 4, 'name': 'Casual', 'description': null},
        ],
        'timestamp': '2025-01-15T10:30:00',
      },
    );

@GenerateNiceMocks([MockSpec<Dio>()])
void main() {
  late MockDio dio;
  late CategoryRepository repository;

  setUp(() {
    dio = MockDio();
    repository = CategoryRepository(dio);
  });

  test('GETs the categories path and returns the typed list', () async {
    when(
      dio.get<Map<String, dynamic>>(any),
    ).thenAnswer((_) async => _listResponse());

    final categories = await repository.getCategories();

    final captured = verify(dio.get<Map<String, dynamic>>(captureAny)).captured;
    expect(captured[0], '/api/v1/categories');
    expect(categories, hasLength(2));
    expect(categories.first.name, 'Running');
    expect(categories.last.description, isNull);
  });

  test('surfaces a transport error as a typed AppException', () async {
    when(dio.get<Map<String, dynamic>>(any)).thenThrow(
      DioException(
        requestOptions: RequestOptions(path: '/api/v1/categories'),
        error: const AppException(message: 'Unable to reach the server.'),
      ),
    );

    await expectLater(repository.getCategories(), throwsA(isA<AppException>()));
  });
}
