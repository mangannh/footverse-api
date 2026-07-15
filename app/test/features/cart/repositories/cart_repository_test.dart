import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/cart/models/add_cart_item_request.dart';
import 'package:footverse/features/cart/models/update_cart_item_request.dart';
import 'package:footverse/features/cart/repositories/cart_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'cart_repository_test.mocks.dart';

Map<String, dynamic> _cartData() => <String, dynamic>{
  'items': <Map<String, dynamic>>[
    <String, dynamic>{
      'id': 500,
      'productVariantId': 100,
      'productId': 1,
      'productName': 'Air Zoom Pegasus',
      'productImageUrl': 'https://cdn.example.com/p1.jpg',
      'color': 'Black',
      'size': '42',
      'unitPrice': 1650000.00,
      'quantity': 2,
      'lineTotal': 3300000.00,
      'available': true,
    },
  ],
  'subtotal': 3300000.00,
  'itemCount': 2,
};

Response<Map<String, dynamic>> _cartResponse(String path) =>
    Response<Map<String, dynamic>>(
      requestOptions: RequestOptions(path: path),
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': _cartData(),
        'timestamp': '2025-01-15T10:30:00',
      },
    );

DioException _errorWith(String errorCode, int statusCode) => DioException(
  requestOptions: RequestOptions(path: '/api/v1/cart'),
  error: AppException(
    message: 'error',
    statusCode: statusCode,
    errorCode: errorCode,
  ),
);

const AddCartItemRequest _addRequest = AddCartItemRequest(
  productVariantId: 100,
  quantity: 2,
);

const UpdateCartItemRequest _updateRequest = UpdateCartItemRequest(quantity: 3);

@GenerateNiceMocks([MockSpec<Dio>()])
void main() {
  late MockDio dio;
  late CartRepository repository;

  setUp(() {
    dio = MockDio();
    repository = CartRepository(dio);
  });

  group('getCart', () {
    test('GETs the cart path and returns the typed cart', () async {
      when(
        dio.get<Map<String, dynamic>>(any),
      ).thenAnswer((_) async => _cartResponse('/api/v1/cart'));

      final cart = await repository.getCart();

      final captured = verify(
        dio.get<Map<String, dynamic>>(captureAny),
      ).captured;
      expect(captured[0], '/api/v1/cart');
      expect(cart.items, hasLength(1));
      expect(cart.items.first.id, 500);
      expect(cart.items.first.available, isTrue);
      expect(cart.subtotal, 3300000.0);
      expect(cart.itemCount, 2);
    });

    test('surfaces a transport error as a typed AppException', () async {
      when(
        dio.get<Map<String, dynamic>>(any),
      ).thenThrow(_errorWith('CART_ITEM_FORBIDDEN', 403));

      await expectLater(
        repository.getCart(),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'CART_ITEM_FORBIDDEN',
          ),
        ),
      );
    });
  });

  group('addItem', () {
    test('POSTs the items path with the body, returns typed cart', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenAnswer((_) async => _cartResponse('/api/v1/cart/items'));

      final cart = await repository.addItem(_addRequest);

      final captured = verify(
        dio.post<Map<String, dynamic>>(
          captureAny,
          data: captureAnyNamed('data'),
        ),
      ).captured;
      expect(captured[0], '/api/v1/cart/items');
      expect(captured[1], _addRequest.toJson());
      expect(cart.itemCount, 2);
    });

    test('surfaces PRODUCT_VARIANT_INACTIVE as a typed AppException', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenThrow(_errorWith('PRODUCT_VARIANT_INACTIVE', 400));

      await expectLater(
        repository.addItem(_addRequest),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'PRODUCT_VARIANT_INACTIVE',
          ),
        ),
      );
    });

    test(
      'surfaces PRODUCT_VARIANT_INSUFFICIENT_STOCK as a typed AppException',
      () async {
        when(
          dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
        ).thenThrow(_errorWith('PRODUCT_VARIANT_INSUFFICIENT_STOCK', 400));

        await expectLater(
          repository.addItem(_addRequest),
          throwsA(
            isA<AppException>().having(
              (e) => e.errorCode,
              'errorCode',
              'PRODUCT_VARIANT_INSUFFICIENT_STOCK',
            ),
          ),
        );
      },
    );

    test(
      'surfaces PRODUCT_VARIANT_NOT_FOUND as a typed AppException',
      () async {
        when(
          dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
        ).thenThrow(_errorWith('PRODUCT_VARIANT_NOT_FOUND', 404));

        await expectLater(
          repository.addItem(_addRequest),
          throwsA(
            isA<AppException>().having(
              (e) => e.errorCode,
              'errorCode',
              'PRODUCT_VARIANT_NOT_FOUND',
            ),
          ),
        );
      },
    );
  });

  group('updateItem', () {
    test('PUTs the item id path with the body, returns typed cart', () async {
      when(
        dio.put<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenAnswer((_) async => _cartResponse('/api/v1/cart/items/500'));

      final cart = await repository.updateItem(500, _updateRequest);

      final captured = verify(
        dio.put<Map<String, dynamic>>(
          captureAny,
          data: captureAnyNamed('data'),
        ),
      ).captured;
      expect(captured[0], '/api/v1/cart/items/500');
      expect(captured[1], _updateRequest.toJson());
      expect(cart.itemCount, 2);
    });

    test('surfaces CART_ITEM_NOT_FOUND as a typed AppException', () async {
      when(
        dio.put<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenThrow(_errorWith('CART_ITEM_NOT_FOUND', 404));

      await expectLater(
        repository.updateItem(999, _updateRequest),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'CART_ITEM_NOT_FOUND',
          ),
        ),
      );
    });
  });

  group('removeItem', () {
    test('DELETEs the item id path and returns the typed cart', () async {
      when(
        dio.delete<Map<String, dynamic>>(any),
      ).thenAnswer((_) async => _cartResponse('/api/v1/cart/items/500'));

      final cart = await repository.removeItem(500);

      final captured = verify(
        dio.delete<Map<String, dynamic>>(captureAny),
      ).captured;
      expect(captured[0], '/api/v1/cart/items/500');
      expect(cart.itemCount, 2);
    });

    test('surfaces CART_ITEM_FORBIDDEN as a typed AppException', () async {
      when(
        dio.delete<Map<String, dynamic>>(any),
      ).thenThrow(_errorWith('CART_ITEM_FORBIDDEN', 403));

      await expectLater(
        repository.removeItem(500),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'CART_ITEM_FORBIDDEN',
          ),
        ),
      );
    });
  });
}
