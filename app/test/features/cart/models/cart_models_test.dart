import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/cart/models/add_cart_item_request.dart';
import 'package:footverse/features/cart/models/cart_item_response.dart';
import 'package:footverse/features/cart/models/cart_response.dart';
import 'package:footverse/features/cart/models/update_cart_item_request.dart';

Map<String, dynamic> _itemJson() => <String, dynamic>{
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
};

Map<String, dynamic> _cartJson() => <String, dynamic>{
  'items': <Map<String, dynamic>>[_itemJson()],
  'subtotal': 3300000.00,
  'itemCount': 2,
};

void main() {
  group('CartItemResponse (dto-spec §12)', () {
    test('maps every field from a real backend payload', () {
      final item = CartItemResponse.fromJson(_itemJson());

      expect(item.id, 500);
      expect(item.productVariantId, 100);
      expect(item.productId, 1);
      expect(item.productName, 'Air Zoom Pegasus');
      expect(item.productImageUrl, 'https://cdn.example.com/p1.jpg');
      expect(item.color, 'Black');
      expect(item.size, '42');
      expect(item.unitPrice, 1650000.0);
      expect(item.quantity, 2);
      expect(item.lineTotal, 3300000.0);
      expect(item.available, isTrue);
    });

    test('accepts a null productImageUrl', () {
      final item = CartItemResponse.fromJson(
        _itemJson()..['productImageUrl'] = null,
      );

      expect(item.productImageUrl, isNull);
    });

    test('maps the unavailable flag', () {
      final item = CartItemResponse.fromJson(
        _itemJson()..['available'] = false,
      );

      expect(item.available, isFalse);
    });

    test('parses unitPrice and lineTotal from a JSON number', () {
      final item = CartItemResponse.fromJson(
        _itemJson()
          ..['unitPrice'] = 1650000
          ..['lineTotal'] = 3300000,
      );

      expect(item.unitPrice, 1650000.0);
      expect(item.lineTotal, 3300000.0);
    });

    test('parses unitPrice and lineTotal from a quoted decimal string', () {
      final item = CartItemResponse.fromJson(
        _itemJson()
          ..['unitPrice'] = '1650000.00'
          ..['lineTotal'] = '3300000.00',
      );

      expect(item.unitPrice, 1650000.0);
      expect(item.lineTotal, 3300000.0);
    });
  });

  group('CartResponse (dto-spec §12)', () {
    test('maps aggregates and nested lines from a real backend payload', () {
      final cart = CartResponse.fromJson(_cartJson());

      expect(cart.items, hasLength(1));
      expect(cart.items.first.id, 500);
      expect(cart.subtotal, 3300000.0);
      expect(cart.itemCount, 2);
    });

    test('parses subtotal from a JSON number', () {
      final cart = CartResponse.fromJson(_cartJson()..['subtotal'] = 3300000);

      expect(cart.subtotal, 3300000.0);
    });

    test('parses subtotal from a quoted decimal string', () {
      final cart = CartResponse.fromJson(
        _cartJson()..['subtotal'] = '3300000.00',
      );

      expect(cart.subtotal, 3300000.0);
    });

    test('maps an empty cart', () {
      final cart = CartResponse.fromJson(<String, dynamic>{
        'items': <Map<String, dynamic>>[],
        'subtotal': 0,
        'itemCount': 0,
      });

      expect(cart.items, isEmpty);
      expect(cart.subtotal, 0.0);
      expect(cart.itemCount, 0);
    });
  });

  group('AddCartItemRequest (dto-spec §12)', () {
    test('serializes exactly the two spec fields', () {
      const request = AddCartItemRequest(productVariantId: 100, quantity: 2);

      expect(request.toJson(), <String, dynamic>{
        'productVariantId': 100,
        'quantity': 2,
      });
    });
  });

  group('UpdateCartItemRequest (dto-spec §12)', () {
    test('serializes exactly the single spec field', () {
      const request = UpdateCartItemRequest(quantity: 3);

      expect(request.toJson(), <String, dynamic>{'quantity': 3});
    });
  });
}
