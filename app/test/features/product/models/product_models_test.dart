import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/product/models/brand_response.dart';
import 'package:footverse/features/product/models/category_response.dart';
import 'package:footverse/features/product/models/decimal_json.dart';
import 'package:footverse/features/product/models/product_detail_response.dart';
import 'package:footverse/features/product/models/product_image_response.dart';
import 'package:footverse/features/product/models/product_summary_response.dart';
import 'package:footverse/features/product/models/product_variant_response.dart';
import 'package:footverse/features/product/models/product_variant_status.dart';
import 'package:footverse/features/product/models/review_response.dart';

Map<String, dynamic> _summaryJson() => <String, dynamic>{
  'id': 1,
  'name': 'Air Zoom Pegasus',
  'basePrice': 1500000.00,
  'brandName': 'Nike',
  'categoryName': 'Running',
  'primaryImageUrl': 'https://cdn.example.com/p1.jpg',
  'averageRating': 4.5,
  'available': true,
};

Map<String, dynamic> _imageJson() => <String, dynamic>{
  'id': 10,
  'imageUrl': 'https://cdn.example.com/p1-a.jpg',
  'displayOrder': 0,
  'isPrimary': true,
};

Map<String, dynamic> _variantJson() => <String, dynamic>{
  'id': 100,
  'color': 'Black',
  'size': '42',
  'price': 1650000.00,
  'stockQuantity': 7,
  'status': 'ACTIVE',
  'sku': 'AZP-BLK-42',
};

Map<String, dynamic> _detailJson() => <String, dynamic>{
  'id': 1,
  'name': 'Air Zoom Pegasus',
  'description': 'A responsive daily trainer.',
  'basePrice': 1500000.00,
  'brandId': 5,
  'brandName': 'Nike',
  'categoryId': 3,
  'categoryName': 'Running',
  'images': <Map<String, dynamic>>[
    _imageJson(),
    <String, dynamic>{
      'id': 11,
      'imageUrl': 'https://cdn.example.com/p1-b.jpg',
      'displayOrder': 1,
      'isPrimary': false,
    },
  ],
  'variants': <Map<String, dynamic>>[_variantJson()],
  'averageRating': 4.5,
  'reviewCount': 12,
  'available': true,
  'createdAt': '2025-01-15T10:30:00',
};

Map<String, dynamic> _reviewJson() => <String, dynamic>{
  'id': 200,
  'userFullName': 'Nguyen Van A',
  'userAvatarUrl': 'https://cdn.example.com/a.jpg',
  'rating': 5,
  'comment': 'Great shoe.',
  'createdAt': '2025-01-15T10:30:00',
  'updatedAt': '2025-01-16T08:00:00',
};

void main() {
  group('decimalFromJson (dto-spec §9 BigDecimal on the wire)', () {
    test('parses a JSON number', () {
      expect(decimalFromJson(4.5), 4.5);
      expect(decimalFromJson(0), 0.0);
      expect(decimalFromJson(1500000), 1500000.0);
    });

    test('parses a quoted decimal string', () {
      expect(decimalFromJson('4.5'), 4.5);
      expect(decimalFromJson('1500000.00'), 1500000.0);
    });

    test('rejects a non-numeric value', () {
      expect(() => decimalFromJson(true), throwsFormatException);
      expect(() => decimalFromJson(null), throwsFormatException);
    });
  });

  group('ProductSummaryResponse (dto-spec §9)', () {
    test('maps every field', () {
      final summary = ProductSummaryResponse.fromJson(_summaryJson());

      expect(summary.id, 1);
      expect(summary.name, 'Air Zoom Pegasus');
      expect(summary.basePrice, 1500000.0);
      expect(summary.brandName, 'Nike');
      expect(summary.categoryName, 'Running');
      expect(summary.primaryImageUrl, 'https://cdn.example.com/p1.jpg');
      expect(summary.averageRating, 4.5);
      expect(summary.available, isTrue);
    });

    test('accepts averageRating as a decimal string', () {
      final summary = ProductSummaryResponse.fromJson(
        _summaryJson()..['averageRating'] = '4.5',
      );

      expect(summary.averageRating, 4.5);
    });

    test('accepts a null primaryImageUrl', () {
      final summary = ProductSummaryResponse.fromJson(
        _summaryJson()..['primaryImageUrl'] = null,
      );

      expect(summary.primaryImageUrl, isNull);
    });
  });

  group('ProductImageResponse (dto-spec §9)', () {
    test('maps every field', () {
      final image = ProductImageResponse.fromJson(_imageJson());

      expect(image.id, 10);
      expect(image.imageUrl, 'https://cdn.example.com/p1-a.jpg');
      expect(image.displayOrder, 0);
      expect(image.isPrimary, isTrue);
    });
  });

  group('ProductVariantResponse (dto-spec §9)', () {
    test('maps every field including the status enum', () {
      final variant = ProductVariantResponse.fromJson(_variantJson());

      expect(variant.id, 100);
      expect(variant.color, 'Black');
      expect(variant.size, '42');
      expect(variant.price, 1650000.0);
      expect(variant.stockQuantity, 7);
      expect(variant.status, ProductVariantStatus.active);
      expect(variant.sku, 'AZP-BLK-42');
    });

    test('maps the INACTIVE status', () {
      final variant = ProductVariantResponse.fromJson(
        _variantJson()..['status'] = 'INACTIVE',
      );

      expect(variant.status, ProductVariantStatus.inactive);
    });
  });

  group('ProductDetailResponse (dto-spec §9)', () {
    test('maps every field with nested images and variants in order', () {
      final detail = ProductDetailResponse.fromJson(_detailJson());

      expect(detail.id, 1);
      expect(detail.name, 'Air Zoom Pegasus');
      expect(detail.description, 'A responsive daily trainer.');
      expect(detail.basePrice, 1500000.0);
      expect(detail.brandId, 5);
      expect(detail.brandName, 'Nike');
      expect(detail.categoryId, 3);
      expect(detail.categoryName, 'Running');
      expect(detail.images, hasLength(2));
      expect(detail.images.first.displayOrder, 0);
      expect(detail.images.last.displayOrder, 1);
      expect(detail.variants, hasLength(1));
      expect(detail.variants.first.status, ProductVariantStatus.active);
      expect(detail.averageRating, 4.5);
      expect(detail.reviewCount, 12);
      expect(detail.available, isTrue);
      expect(detail.createdAt, DateTime.parse('2025-01-15T10:30:00'));
    });

    test('accepts a null description', () {
      final detail = ProductDetailResponse.fromJson(
        _detailJson()..['description'] = null,
      );

      expect(detail.description, isNull);
    });
  });

  group('CategoryResponse (dto-spec §10)', () {
    test('maps every field', () {
      final category = CategoryResponse.fromJson(<String, dynamic>{
        'id': 3,
        'name': 'Running',
        'description': 'Running shoes',
      });

      expect(category.id, 3);
      expect(category.name, 'Running');
      expect(category.description, 'Running shoes');
    });

    test('accepts a null description', () {
      final category = CategoryResponse.fromJson(<String, dynamic>{
        'id': 3,
        'name': 'Running',
        'description': null,
      });

      expect(category.description, isNull);
    });
  });

  group('BrandResponse (dto-spec §11)', () {
    test('maps every field', () {
      final brand = BrandResponse.fromJson(<String, dynamic>{
        'id': 5,
        'name': 'Nike',
        'logoUrl': 'https://cdn.example.com/nike.png',
        'description': 'Just do it',
      });

      expect(brand.id, 5);
      expect(brand.name, 'Nike');
      expect(brand.logoUrl, 'https://cdn.example.com/nike.png');
      expect(brand.description, 'Just do it');
    });

    test('accepts a null logoUrl', () {
      final brand = BrandResponse.fromJson(<String, dynamic>{
        'id': 5,
        'name': 'Nike',
        'logoUrl': null,
        'description': null,
      });

      expect(brand.logoUrl, isNull);
      expect(brand.description, isNull);
    });
  });

  group('ReviewResponse (dto-spec §16)', () {
    test('maps every field', () {
      final review = ReviewResponse.fromJson(_reviewJson());

      expect(review.id, 200);
      expect(review.userFullName, 'Nguyen Van A');
      expect(review.userAvatarUrl, 'https://cdn.example.com/a.jpg');
      expect(review.rating, 5);
      expect(review.comment, 'Great shoe.');
      expect(review.createdAt, DateTime.parse('2025-01-15T10:30:00'));
      expect(review.updatedAt, DateTime.parse('2025-01-16T08:00:00'));
    });

    test('accepts a null avatar and comment', () {
      final review = ReviewResponse.fromJson(
        _reviewJson()
          ..['userAvatarUrl'] = null
          ..['comment'] = null,
      );

      expect(review.userAvatarUrl, isNull);
      expect(review.comment, isNull);
    });
  });
}
