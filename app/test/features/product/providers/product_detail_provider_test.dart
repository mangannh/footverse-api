import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/product/models/product_detail_response.dart';
import 'package:footverse/features/product/models/product_image_response.dart';
import 'package:footverse/features/product/models/review_response.dart';
import 'package:footverse/features/product/providers/product_detail_provider.dart';
import 'package:footverse/features/product/repositories/product_repository.dart';
import 'package:footverse/shared/models/page_response.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'product_detail_provider_test.mocks.dart';

ProductImageResponse _image(int id, int order) => ProductImageResponse(
  id: id,
  imageUrl: 'image-$id',
  displayOrder: order,
  isPrimary: order == 0,
);

ProductDetailResponse _detail({List<ProductImageResponse>? images}) =>
    ProductDetailResponse(
      id: 1,
      name: 'Air Zoom Pegasus',
      basePrice: 1500000,
      brandId: 5,
      brandName: 'Nike',
      categoryId: 3,
      categoryName: 'Running',
      images: images ?? const <ProductImageResponse>[],
      variants: const [],
      averageRating: 4.5,
      reviewCount: 2,
      available: true,
      createdAt: DateTime.parse('2025-01-15T10:30:00'),
    );

ReviewResponse _review(int id) => ReviewResponse(
  id: id,
  userFullName: 'User $id',
  rating: 5,
  createdAt: DateTime.parse('2025-01-15T10:30:00'),
  updatedAt: DateTime.parse('2025-01-15T10:30:00'),
);

PageResponse<ReviewResponse> _reviewPage(List<int> ids, {required bool last}) =>
    PageResponse<ReviewResponse>(
      content: ids.map(_review).toList(),
      page: 0,
      size: 20,
      totalElements: ids.length,
      totalPages: 1,
      last: last,
    );

void _stubReviews(
  MockProductRepository repository,
  int page,
  Future<PageResponse<ReviewResponse>> Function() answer,
) {
  when(
    repository.getProductReviews(
      any,
      page: argThat(equals(page), named: 'page'),
      size: anyNamed('size'),
    ),
  ).thenAnswer((_) => answer());
}

@GenerateNiceMocks([MockSpec<ProductRepository>()])
void main() {
  late MockProductRepository repository;
  late ProductDetailProvider provider;

  setUp(() {
    repository = MockProductRepository();
    when(repository.getProduct(any)).thenAnswer((_) async => _detail());
    _stubReviews(repository, 0, () async => _reviewPage([1], last: true));
    provider = ProductDetailProvider(repository, 1);
  });

  group('product detail', () {
    test('loads the detail and its first review page', () async {
      await provider.load();

      expect(provider.status, ProductDetailStatus.ready);
      expect(provider.detail?.id, 1);
      expect(provider.reviewsStatus, ReviewsStatus.ready);
      expect(provider.reviews, hasLength(1));
      expect(provider.isNotFound, isFalse);
    });

    test('sorts images by displayOrder ascending', () async {
      when(repository.getProduct(any)).thenAnswer(
        (_) async => _detail(
          images: <ProductImageResponse>[
            _image(30, 2),
            _image(10, 0),
            _image(20, 1),
          ],
        ),
      );

      await provider.load();

      expect(provider.images.map((image) => image.displayOrder), <int>[
        0,
        1,
        2,
      ]);
    });

    test('exposes a generic error when the detail load fails', () async {
      when(
        repository.getProduct(any),
      ).thenThrow(const AppException(message: 'Server error', statusCode: 500));

      await provider.load();

      expect(provider.status, ProductDetailStatus.error);
      expect(provider.isNotFound, isFalse);
      expect(provider.error?.message, 'Server error');
    });

    test('flags an unknown product as not found (404)', () async {
      when(repository.getProduct(any)).thenThrow(
        const AppException(
          message: 'Product not found',
          statusCode: 404,
          errorCode: 'PRODUCT_NOT_FOUND',
        ),
      );

      await provider.load();

      expect(provider.status, ProductDetailStatus.error);
      expect(provider.isNotFound, isTrue);
      expect(provider.error?.message, 'Product not found');
    });
  });

  group('reviews', () {
    test('exposes the empty state when there are no reviews', () async {
      _stubReviews(repository, 0, () async => _reviewPage([], last: true));

      await provider.load();

      expect(provider.reviewsStatus, ReviewsStatus.ready);
      expect(provider.isReviewsEmpty, isTrue);
    });

    test(
      'surfaces a first-page review error without hiding the product',
      () async {
        _stubReviews(
          repository,
          0,
          () async => throw const AppException(message: 'reviews down'),
        );

        await provider.load();

        expect(provider.status, ProductDetailStatus.ready);
        expect(provider.reviewsStatus, ReviewsStatus.error);
        expect(provider.reviewsError?.message, 'reviews down');
      },
    );

    test('appends the next review page and advances the cursor', () async {
      _stubReviews(repository, 0, () async => _reviewPage([1, 2], last: false));
      _stubReviews(repository, 1, () async => _reviewPage([3, 4], last: true));

      await provider.load();
      await provider.loadNextReviews();

      expect(provider.reviews.map((r) => r.id), <int>[1, 2, 3, 4]);
    });

    test('stops requesting once the last review page is reached', () async {
      _stubReviews(repository, 0, () async => _reviewPage([1], last: true));

      await provider.load();
      await provider.loadNextReviews();

      verifyNever(
        repository.getProductReviews(
          any,
          page: argThat(equals(1), named: 'page'),
          size: anyNamed('size'),
        ),
      );
    });

    test('does not issue a duplicate request while one is in flight', () async {
      _stubReviews(repository, 0, () async => _reviewPage([1], last: false));
      final gate = Completer<PageResponse<ReviewResponse>>();
      _stubReviews(repository, 1, () => gate.future);

      await provider.load();
      final first = provider.loadNextReviews();
      final second = provider.loadNextReviews();

      expect(provider.loadingNextReviews, isTrue);
      gate.complete(_reviewPage([2], last: true));
      await Future.wait(<Future<void>>[first, second]);

      verify(
        repository.getProductReviews(
          any,
          page: argThat(equals(1), named: 'page'),
          size: anyNamed('size'),
        ),
      ).called(1);
    });

    test('a next-page error preserves the existing reviews', () async {
      _stubReviews(repository, 0, () async => _reviewPage([1, 2], last: false));
      _stubReviews(
        repository,
        1,
        () async => throw const AppException(message: 'page failed'),
      );

      await provider.load();
      await provider.loadNextReviews();

      expect(provider.reviews.map((r) => r.id), <int>[1, 2]);
      expect(provider.loadingNextReviews, isFalse);
      expect(provider.nextReviewsError?.message, 'page failed');

      // Retry loads the same failed page (the cursor did not advance).
      _stubReviews(repository, 1, () async => _reviewPage([3], last: true));
      await provider.loadNextReviews();

      expect(provider.reviews.map((r) => r.id), <int>[1, 2, 3]);
      expect(provider.nextReviewsError, isNull);
    });

    test('discards a stale next-page response after a reload', () async {
      _stubReviews(repository, 0, () async => _reviewPage([1, 2], last: false));
      final gate = Completer<PageResponse<ReviewResponse>>();
      _stubReviews(repository, 1, () => gate.future);

      await provider.load();
      final pending = provider.loadNextReviews(); // page 1 hangs

      // A reload replaces the review list with a fresh first page.
      _stubReviews(repository, 0, () async => _reviewPage([9], last: true));
      await provider.retry();

      // The stale page-1 response now arrives and must be ignored.
      gate.complete(_reviewPage([3, 4], last: false));
      await pending;

      expect(provider.reviews.map((r) => r.id), <int>[9]);
      expect(provider.loadingNextReviews, isFalse);
    });

    test('retries the first review page after it failed', () async {
      _stubReviews(
        repository,
        0,
        () async => throw const AppException(message: 'reviews down'),
      );

      await provider.load();
      expect(provider.reviewsStatus, ReviewsStatus.error);

      _stubReviews(repository, 0, () async => _reviewPage([1, 2], last: true));
      await provider.retryReviews();

      expect(provider.reviewsStatus, ReviewsStatus.ready);
      expect(provider.reviews.map((r) => r.id), <int>[1, 2]);
      expect(provider.reviewsError, isNull);
    });
  });

  group('lifecycle', () {
    test('a request that completes after dispose does not throw', () async {
      final gate = Completer<ProductDetailResponse>();
      when(repository.getProduct(any)).thenAnswer((_) => gate.future);

      final pending = provider.load();
      provider.dispose();
      gate.complete(_detail());

      // No notifyListeners fires after dispose, so this completes normally.
      await expectLater(pending, completes);
    });
  });
}
