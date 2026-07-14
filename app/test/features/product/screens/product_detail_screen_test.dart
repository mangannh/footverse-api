import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/product/models/product_detail_response.dart';
import 'package:footverse/features/product/models/review_response.dart';
import 'package:footverse/features/product/repositories/product_repository.dart';
import 'package:footverse/features/product/screens/product_detail_screen.dart';
import 'package:footverse/shared/models/page_response.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'product_detail_screen_test.mocks.dart';

// A minimal detail (no images, no variants) so the header stays short and the
// review list decides whether the page can scroll.
ProductDetailResponse _detail() => ProductDetailResponse(
  id: 1,
  name: 'Air Zoom Pegasus',
  basePrice: 1500000,
  brandId: 5,
  brandName: 'Nike',
  categoryId: 3,
  categoryName: 'Running',
  images: const [],
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
  PageResponse<ReviewResponse> result,
) {
  when(
    repository.getProductReviews(
      any,
      page: argThat(equals(page), named: 'page'),
      size: anyNamed('size'),
    ),
  ).thenAnswer((_) async => result);
}

@GenerateNiceMocks([MockSpec<ProductRepository>()])
void main() {
  testWidgets(
    'auto-loads the next review page when the first page cannot scroll',
    (tester) async {
      // A tall viewport so the short first page leaves nothing to scroll,
      // triggering the auto-fill.
      await tester.binding.setSurfaceSize(const Size(400, 2000));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      final repository = MockProductRepository();
      when(repository.getProduct(any)).thenAnswer((_) async => _detail());
      _stubReviews(repository, 0, _reviewPage([1], last: false));
      _stubReviews(repository, 1, _reviewPage([2], last: true));

      await tester.pumpWidget(
        MaterialApp(
          home: ProductDetailScreen(
            productId: 1,
            productRepository: repository,
          ),
        ),
      );

      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 10));
      }

      expect(find.text('User 1'), findsOneWidget);
      expect(find.text('User 2'), findsOneWidget);
      verify(
        repository.getProductReviews(
          any,
          page: argThat(equals(1), named: 'page'),
          size: anyNamed('size'),
        ),
      ).called(1);
    },
  );
}
