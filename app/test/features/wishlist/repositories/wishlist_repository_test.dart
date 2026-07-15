import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/wishlist/models/add_wishlist_item_request.dart';
import 'package:footverse/features/wishlist/models/wishlist_item_response.dart';
import 'package:footverse/features/wishlist/repositories/wishlist_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'wishlist_repository_test.mocks.dart';

Map<String, dynamic> _itemData({required int id, required int productId}) =>
    <String, dynamic>{
      'id': id,
      'productId': productId,
      'productName': 'Product $productId',
      'primaryImageUrl': 'https://cdn.example.com/p$productId.jpg',
      'basePrice': 1500000.00,
      'available': true,
    };

Response<Map<String, dynamic>> _listResponse() =>
    Response<Map<String, dynamic>>(
      requestOptions: RequestOptions(path: '/api/v1/wishlist'),
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': <Map<String, dynamic>>[
          _itemData(id: 700, productId: 3),
          _itemData(id: 699, productId: 2),
          _itemData(id: 698, productId: 1),
        ],
        'timestamp': '2025-01-15T10:30:00',
      },
    );

Response<Map<String, dynamic>> _singleResponse(int statusCode) =>
    Response<Map<String, dynamic>>(
      requestOptions: RequestOptions(path: '/api/v1/wishlist'),
      statusCode: statusCode,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': _itemData(id: 700, productId: 1),
        'timestamp': '2025-01-15T10:30:00',
      },
    );

DioException _errorWith(String errorCode, int statusCode) => DioException(
  requestOptions: RequestOptions(path: '/api/v1/wishlist'),
  error: AppException(
    message: 'error',
    statusCode: statusCode,
    errorCode: errorCode,
  ),
);

const AddWishlistItemRequest _request = AddWishlistItemRequest(productId: 1);

@GenerateNiceMocks([MockSpec<Dio>()])
void main() {
  late MockDio dio;
  late WishlistRepository repository;

  setUp(() {
    dio = MockDio();
    repository = WishlistRepository(dio);
  });

  group('getWishlist', () {
    test('GETs the wishlist path and preserves the server order', () async {
      when(
        dio.get<Map<String, dynamic>>(any),
      ).thenAnswer((_) async => _listResponse());

      final items = await repository.getWishlist();

      final captured = verify(
        dio.get<Map<String, dynamic>>(captureAny),
      ).captured;
      expect(captured[0], '/api/v1/wishlist');
      expect(items, hasLength(3));
      // Order is returned exactly as the server delivered it (no client sort).
      expect(items.map((item) => item.id).toList(), <int>[700, 699, 698]);
      expect(items.first.productId, 3);
      expect(items.first.basePrice, 1500000.0);
    });

    test('surfaces a transport error as a typed AppException', () async {
      when(
        dio.get<Map<String, dynamic>>(any),
      ).thenThrow(_errorWith('UNAUTHORIZED', 401));

      await expectLater(
        repository.getWishlist(),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'UNAUTHORIZED',
          ),
        ),
      );
    });
  });

  group('addProduct', () {
    test('POSTs the wishlist path with the body, returns typed', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenAnswer((_) async => _singleResponse(201));

      final item = await repository.addProduct(_request);

      final captured = verify(
        dio.post<Map<String, dynamic>>(
          captureAny,
          data: captureAnyNamed('data'),
        ),
      ).captured;
      expect(captured[0], '/api/v1/wishlist');
      expect(captured[1], _request.toJson());
      expect(item.id, 700);
      expect(item.productId, 1);
    });

    test('a fresh add (HTTP 201) returns the created item', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenAnswer((_) async => _singleResponse(201));

      final item = await repository.addProduct(_request);

      expect(item, isA<WishlistItemResponse>());
      expect(item.productId, 1);
    });

    test(
      'a duplicate add (HTTP 200) returns the existing item, no error',
      () async {
        when(
          dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
        ).thenAnswer((_) async => _singleResponse(200));

        final item = await repository.addProduct(_request);

        expect(item, isA<WishlistItemResponse>());
        expect(item.productId, 1);
      },
    );

    test('surfaces PRODUCT_NOT_FOUND as a typed AppException', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenThrow(_errorWith('PRODUCT_NOT_FOUND', 404));

      await expectLater(
        repository.addProduct(_request),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'PRODUCT_NOT_FOUND',
          ),
        ),
      );
    });
  });

  group('removeProduct', () {
    test('DELETEs the wishlist product-id path', () async {
      when(dio.delete<void>(any)).thenAnswer(
        (_) async => Response<void>(
          requestOptions: RequestOptions(path: '/api/v1/wishlist/1'),
          statusCode: 200,
        ),
      );

      await repository.removeProduct(1);

      final captured = verify(dio.delete<void>(captureAny)).captured;
      expect(captured[0], '/api/v1/wishlist/1');
    });

    test('surfaces a transport error as a typed AppException', () async {
      when(dio.delete<void>(any)).thenThrow(_errorWith('UNAUTHORIZED', 401));

      await expectLater(
        repository.removeProduct(1),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'UNAUTHORIZED',
          ),
        ),
      );
    });
  });
}
