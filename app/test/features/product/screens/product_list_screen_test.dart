import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/cart/providers/cart_provider.dart';
import 'package:footverse/features/cart/repositories/cart_repository.dart';
import 'package:footverse/features/product/models/brand_response.dart';
import 'package:footverse/features/product/models/category_response.dart';
import 'package:footverse/features/product/models/product_summary_response.dart';
import 'package:footverse/features/product/repositories/brand_repository.dart';
import 'package:footverse/features/product/repositories/category_repository.dart';
import 'package:footverse/features/product/repositories/product_repository.dart';
import 'package:footverse/features/product/screens/product_list_screen.dart';
import 'package:footverse/shared/models/page_response.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:provider/provider.dart';

import 'product_list_screen_test.mocks.dart';

// No primaryImageUrl: the card shows its placeholder, so the test never issues a
// network image request.
ProductSummaryResponse _product(int id) => ProductSummaryResponse(
  id: id,
  name: 'Product $id',
  basePrice: 100,
  brandName: 'Nike',
  categoryName: 'Running',
  averageRating: 4.5,
  available: true,
);

PageResponse<ProductSummaryResponse> _page(
  List<int> ids, {
  required bool last,
}) => PageResponse<ProductSummaryResponse>(
  content: ids.map(_product).toList(),
  page: 0,
  size: 20,
  totalElements: ids.length,
  totalPages: 1,
  last: last,
);

void _stubPage(
  MockProductRepository repository,
  int page,
  PageResponse<ProductSummaryResponse> result,
) {
  when(
    repository.searchProducts(
      name: anyNamed('name'),
      brandId: anyNamed('brandId'),
      categoryId: anyNamed('categoryId'),
      page: argThat(equals(page), named: 'page'),
      size: anyNamed('size'),
      sort: anyNamed('sort'),
    ),
  ).thenAnswer((_) async => result);
}

@GenerateNiceMocks([
  MockSpec<ProductRepository>(),
  MockSpec<CategoryRepository>(),
  MockSpec<BrandRepository>(),
  MockSpec<CartRepository>(),
])
void main() {
  late MockProductRepository productRepository;
  late MockCategoryRepository categoryRepository;
  late MockBrandRepository brandRepository;
  late MockCartRepository cartRepository;

  setUp(() {
    productRepository = MockProductRepository();
    categoryRepository = MockCategoryRepository();
    brandRepository = MockBrandRepository();
    cartRepository = MockCartRepository();
    when(
      categoryRepository.getCategories(),
    ).thenAnswer((_) async => <CategoryResponse>[]);
    when(
      brandRepository.getBrands(),
    ).thenAnswer((_) async => <BrandResponse>[]);
  });

  testWidgets(
    'auto-loads the next page when the first page does not fill the viewport',
    (tester) async {
      // A single-item first page cannot scroll; the next page must load on its
      // own so the user is never stuck with a partial list.
      _stubPage(productRepository, 0, _page([1], last: false));
      _stubPage(productRepository, 1, _page([2], last: true));

      await tester.pumpWidget(
        MaterialApp(
          home: ChangeNotifierProvider<CartProvider>(
            create: (_) => CartProvider(cartRepository),
            child: ProductListScreen(
              productRepository: productRepository,
              categoryRepository: categoryRepository,
              brandRepository: brandRepository,
            ),
          ),
        ),
      );

      // Let loadInitial and the post-frame auto-load both settle.
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 10));
      }

      expect(find.text('Product 1'), findsOneWidget);
      expect(find.text('Product 2'), findsOneWidget);
      verify(
        productRepository.searchProducts(
          name: anyNamed('name'),
          brandId: anyNamed('brandId'),
          categoryId: anyNamed('categoryId'),
          page: argThat(equals(1), named: 'page'),
          size: anyNamed('size'),
          sort: anyNamed('sort'),
        ),
      ).called(1);
    },
  );
}
