import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/product/models/product_detail_response.dart';
import 'package:footverse/features/product/repositories/product_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'product_repository_test.mocks.dart';

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

Map<String, dynamic> _detailJson() => <String, dynamic>{
  'id': 1,
  'name': 'Air Zoom Pegasus',
  'description': null,
  'basePrice': 1500000.00,
  'brandId': 5,
  'brandName': 'Nike',
  'categoryId': 3,
  'categoryName': 'Running',
  'images': <Map<String, dynamic>>[],
  'variants': <Map<String, dynamic>>[],
  'averageRating': 4.5,
  'reviewCount': 0,
  'available': true,
  'createdAt': '2025-01-15T10:30:00',
};

Map<String, dynamic> _reviewJson() => <String, dynamic>{
  'id': 200,
  'userFullName': 'Nguyen Van A',
  'userAvatarUrl': null,
  'rating': 5,
  'comment': 'Great shoe.',
  'createdAt': '2025-01-15T10:30:00',
  'updatedAt': '2025-01-15T10:30:00',
};

Response<Map<String, dynamic>> _pageResponse(
  String path,
  List<Map<String, dynamic>> content,
) => Response<Map<String, dynamic>>(
  requestOptions: RequestOptions(path: path),
  statusCode: 200,
  data: <String, dynamic>{
    'success': true,
    'message': 'OK',
    'data': <String, dynamic>{
      'content': content,
      'page': 0,
      'size': 20,
      'totalElements': content.length,
      'totalPages': 1,
      'last': true,
    },
    'timestamp': '2025-01-15T10:30:00',
  },
);

Response<Map<String, dynamic>> _detailResponse(String path) =>
    Response<Map<String, dynamic>>(
      requestOptions: RequestOptions(path: path),
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': _detailJson(),
        'timestamp': '2025-01-15T10:30:00',
      },
    );

DioException _errorWith(String errorCode, int statusCode) => DioException(
  requestOptions: RequestOptions(path: '/api/v1/products'),
  error: AppException(
    message: 'Product not found',
    statusCode: statusCode,
    errorCode: errorCode,
  ),
);

@GenerateNiceMocks([MockSpec<Dio>()])
void main() {
  late MockDio dio;
  late ProductRepository repository;

  setUp(() {
    dio = MockDio();
    repository = ProductRepository(dio);
  });

  group('searchProducts', () {
    test('encodes only page and size when no filter is given', () async {
      when(
        dio.get<Map<String, dynamic>>(
          any,
          queryParameters: anyNamed('queryParameters'),
        ),
      ).thenAnswer((_) async => _pageResponse('/api/v1/products', []));

      await repository.searchProducts();

      final captured = verify(
        dio.get<Map<String, dynamic>>(
          captureAny,
          queryParameters: captureAnyNamed('queryParameters'),
        ),
      ).captured;
      expect(captured[0], '/api/v1/products');
      expect(captured[1], <String, dynamic>{'page': 0, 'size': 20});
    });

    test('encodes every filter, paging, and the sort field', () async {
      when(
        dio.get<Map<String, dynamic>>(
          any,
          queryParameters: anyNamed('queryParameters'),
        ),
      ).thenAnswer(
        (_) async => _pageResponse('/api/v1/products', [_summaryJson()]),
      );

      await repository.searchProducts(
        name: 'zoom',
        brandId: 5,
        categoryId: 3,
        page: 2,
        size: 50,
        sort: ProductSort.basePrice,
      );

      final captured = verify(
        dio.get<Map<String, dynamic>>(
          captureAny,
          queryParameters: captureAnyNamed('queryParameters'),
        ),
      ).captured;
      expect(captured[1], <String, dynamic>{
        'page': 2,
        'size': 50,
        'name': 'zoom',
        'brandId': 5,
        'categoryId': 3,
        'sort': 'basePrice',
      });
    });

    test('surfaces the full PageResponse metadata', () async {
      when(
        dio.get<Map<String, dynamic>>(
          any,
          queryParameters: anyNamed('queryParameters'),
        ),
      ).thenAnswer(
        (_) async => _pageResponse('/api/v1/products', [_summaryJson()]),
      );

      final page = await repository.searchProducts();

      expect(page.content, hasLength(1));
      expect(page.content.first.name, 'Air Zoom Pegasus');
      expect(page.page, 0);
      expect(page.size, 20);
      expect(page.totalElements, 1);
      expect(page.totalPages, 1);
      expect(page.last, isTrue);
    });

    test('maps each whitelisted sort to its query field', () {
      expect(ProductSort.createdAt.field, 'createdAt');
      expect(ProductSort.basePrice.field, 'basePrice');
      expect(ProductSort.name.field, 'name');
      expect(ProductSort.values, hasLength(3));
    });
  });

  group('getProduct', () {
    test('GETs the product path and returns the typed detail', () async {
      when(
        dio.get<Map<String, dynamic>>(any),
      ).thenAnswer((_) async => _detailResponse('/api/v1/products/1'));

      final detail = await repository.getProduct(1);

      final captured = verify(
        dio.get<Map<String, dynamic>>(captureAny),
      ).captured;
      expect(captured[0], '/api/v1/products/1');
      expect(detail, isA<ProductDetailResponse>());
      expect(detail.id, 1);
    });

    test('surfaces PRODUCT_NOT_FOUND as a typed AppException', () async {
      when(
        dio.get<Map<String, dynamic>>(any),
      ).thenThrow(_errorWith('PRODUCT_NOT_FOUND', 404));

      await expectLater(
        repository.getProduct(999),
        throwsA(
          isA<AppException>()
              .having((e) => e.errorCode, 'errorCode', 'PRODUCT_NOT_FOUND')
              .having((e) => e.statusCode, 'statusCode', 404),
        ),
      );
    });
  });

  group('getProductReviews', () {
    test('GETs the reviews path with paging and returns the page', () async {
      when(
        dio.get<Map<String, dynamic>>(
          any,
          queryParameters: anyNamed('queryParameters'),
        ),
      ).thenAnswer(
        (_) async =>
            _pageResponse('/api/v1/products/1/reviews', [_reviewJson()]),
      );

      final page = await repository.getProductReviews(1, page: 1, size: 10);

      final captured = verify(
        dio.get<Map<String, dynamic>>(
          captureAny,
          queryParameters: captureAnyNamed('queryParameters'),
        ),
      ).captured;
      expect(captured[0], '/api/v1/products/1/reviews');
      expect(captured[1], <String, dynamic>{'page': 1, 'size': 10});
      expect(page.content, hasLength(1));
      expect(page.content.first.userFullName, 'Nguyen Van A');
    });
  });
}
