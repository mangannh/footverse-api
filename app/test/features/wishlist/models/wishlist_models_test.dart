import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/wishlist/models/add_wishlist_item_request.dart';
import 'package:footverse/features/wishlist/models/wishlist_item_response.dart';

Map<String, dynamic> _itemJson() => <String, dynamic>{
  'id': 700,
  'productId': 1,
  'productName': 'Air Zoom Pegasus',
  'primaryImageUrl': 'https://cdn.example.com/p1.jpg',
  'basePrice': 1500000.00,
  'available': true,
};

void main() {
  group('WishlistItemResponse (dto-spec §13)', () {
    test('maps every field from a real backend payload', () {
      final item = WishlistItemResponse.fromJson(_itemJson());

      expect(item.id, 700);
      expect(item.productId, 1);
      expect(item.productName, 'Air Zoom Pegasus');
      expect(item.primaryImageUrl, 'https://cdn.example.com/p1.jpg');
      expect(item.basePrice, 1500000.0);
      expect(item.available, isTrue);
    });

    test('accepts a null primaryImageUrl', () {
      final item = WishlistItemResponse.fromJson(
        _itemJson()..['primaryImageUrl'] = null,
      );

      expect(item.primaryImageUrl, isNull);
    });

    test('maps the unavailable flag', () {
      final item = WishlistItemResponse.fromJson(
        _itemJson()..['available'] = false,
      );

      expect(item.available, isFalse);
    });

    test('parses basePrice from a JSON number', () {
      final item = WishlistItemResponse.fromJson(
        _itemJson()..['basePrice'] = 1500000,
      );

      expect(item.basePrice, 1500000.0);
    });

    test('parses basePrice from a quoted decimal string', () {
      final item = WishlistItemResponse.fromJson(
        _itemJson()..['basePrice'] = '1500000.00',
      );

      expect(item.basePrice, 1500000.0);
    });
  });

  group('AddWishlistItemRequest (dto-spec §13)', () {
    test('serializes exactly the single spec field', () {
      const request = AddWishlistItemRequest(productId: 1);

      expect(request.toJson(), <String, dynamic>{'productId': 1});
    });
  });
}
