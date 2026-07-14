import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/product/models/brand_response.dart';
import 'package:footverse/features/product/models/category_response.dart';
import 'package:footverse/features/product/models/product_summary_response.dart';
import 'package:footverse/features/product/providers/product_list_provider.dart';
import 'package:footverse/features/product/repositories/brand_repository.dart';
import 'package:footverse/features/product/repositories/category_repository.dart';
import 'package:footverse/features/product/repositories/product_repository.dart';
import 'package:footverse/shared/models/page_response.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'product_list_provider_test.mocks.dart';

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

/// Stubs `searchProducts` for a specific `page` value, matching the remaining
/// named arguments loosely.
void _stubPage(
  MockProductRepository repository,
  int page,
  Future<PageResponse<ProductSummaryResponse>> Function() answer,
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
  ).thenAnswer((_) => answer());
}

@GenerateNiceMocks([
  MockSpec<ProductRepository>(),
  MockSpec<CategoryRepository>(),
  MockSpec<BrandRepository>(),
])
void main() {
  late MockProductRepository productRepository;
  late MockCategoryRepository categoryRepository;
  late MockBrandRepository brandRepository;
  late ProductListProvider provider;

  setUp(() {
    productRepository = MockProductRepository();
    categoryRepository = MockCategoryRepository();
    brandRepository = MockBrandRepository();

    when(
      categoryRepository.getCategories(),
    ).thenAnswer((_) async => <CategoryResponse>[]);
    when(
      brandRepository.getBrands(),
    ).thenAnswer((_) async => <BrandResponse>[]);
    _stubPage(productRepository, 0, () async => _page([1], last: true));

    provider = ProductListProvider(
      productRepository,
      categoryRepository,
      brandRepository,
    );
  });

  group('loadInitial', () {
    test('loads the first page and the filter sources', () async {
      when(categoryRepository.getCategories()).thenAnswer(
        (_) async => <CategoryResponse>[
          const CategoryResponse(id: 3, name: 'Running'),
        ],
      );
      when(brandRepository.getBrands()).thenAnswer(
        (_) async => <BrandResponse>[const BrandResponse(id: 5, name: 'Nike')],
      );
      _stubPage(productRepository, 0, () async => _page([1, 2], last: false));

      await provider.loadInitial();

      expect(provider.status, ProductListStatus.ready);
      expect(provider.products, hasLength(2));
      expect(provider.categories, hasLength(1));
      expect(provider.brands, hasLength(1));
      expect(provider.isEmpty, isFalse);
    });

    test(
      'exposes the empty state when the backend returns no products',
      () async {
        _stubPage(productRepository, 0, () async => _page([], last: true));

        await provider.loadInitial();

        expect(provider.status, ProductListStatus.ready);
        expect(provider.products, isEmpty);
        expect(provider.isEmpty, isTrue);
      },
    );

    test('exposes the error state when the first page fails', () async {
      _stubPage(
        productRepository,
        0,
        () async => throw const AppException(message: 'boom'),
      );

      await provider.loadInitial();

      expect(provider.status, ProductListStatus.error);
      expect(provider.products, isEmpty);
      expect(provider.error?.message, 'boom');
    });

    test('retry reloads the first page after an error', () async {
      _stubPage(
        productRepository,
        0,
        () async => throw const AppException(message: 'boom'),
      );

      await provider.loadInitial();
      expect(provider.status, ProductListStatus.error);

      _stubPage(productRepository, 0, () async => _page([1, 2], last: true));
      await provider.retry();

      expect(provider.status, ProductListStatus.ready);
      expect(provider.products.map((p) => p.id), <int>[1, 2]);
      expect(provider.error, isNull);
    });
  });

  group('filter and sort changes reset to the first page', () {
    setUp(() async {
      await provider.loadInitial();
      clearInteractions(productRepository);
    });

    test('search reloads from page 0 with the name filter', () async {
      _stubPage(productRepository, 0, () async => _page([9], last: true));

      await provider.setSearchText('zoom');

      verify(
        productRepository.searchProducts(
          name: 'zoom',
          brandId: anyNamed('brandId'),
          categoryId: anyNamed('categoryId'),
          page: 0,
          size: anyNamed('size'),
          sort: anyNamed('sort'),
        ),
      ).called(1);
      expect(provider.products.single.id, 9);
    });

    test('brand filter reloads from page 0 with the brandId', () async {
      _stubPage(productRepository, 0, () async => _page([9], last: true));

      await provider.setBrand(5);

      verify(
        productRepository.searchProducts(
          name: anyNamed('name'),
          brandId: 5,
          categoryId: anyNamed('categoryId'),
          page: 0,
          size: anyNamed('size'),
          sort: anyNamed('sort'),
        ),
      ).called(1);
    });

    test('category filter reloads from page 0 with the categoryId', () async {
      _stubPage(productRepository, 0, () async => _page([9], last: true));

      await provider.setCategory(3);

      verify(
        productRepository.searchProducts(
          name: anyNamed('name'),
          brandId: anyNamed('brandId'),
          categoryId: 3,
          page: 0,
          size: anyNamed('size'),
          sort: anyNamed('sort'),
        ),
      ).called(1);
    });

    test('sort reloads from page 0 with the sort field', () async {
      _stubPage(productRepository, 0, () async => _page([9], last: true));

      await provider.setSort(ProductSort.basePrice);

      verify(
        productRepository.searchProducts(
          name: anyNamed('name'),
          brandId: anyNamed('brandId'),
          categoryId: anyNamed('categoryId'),
          page: 0,
          size: anyNamed('size'),
          sort: ProductSort.basePrice,
        ),
      ).called(1);
    });
  });

  group('infinite scrolling', () {
    test('appends the next page and advances the page cursor', () async {
      _stubPage(productRepository, 0, () async => _page([1, 2], last: false));
      _stubPage(productRepository, 1, () async => _page([3, 4], last: true));

      await provider.loadInitial();
      await provider.loadNextPage();

      expect(provider.products.map((p) => p.id), <int>[1, 2, 3, 4]);
      verify(
        productRepository.searchProducts(
          name: anyNamed('name'),
          brandId: anyNamed('brandId'),
          categoryId: anyNamed('categoryId'),
          page: 1,
          size: anyNamed('size'),
          sort: anyNamed('sort'),
        ),
      ).called(1);
    });

    test('does not issue a duplicate request while one is in flight', () async {
      _stubPage(productRepository, 0, () async => _page([1], last: false));
      final gate = Completer<PageResponse<ProductSummaryResponse>>();
      _stubPage(productRepository, 1, () => gate.future);

      await provider.loadInitial();
      final first = provider.loadNextPage();
      final second = provider.loadNextPage();

      expect(provider.loadingNextPage, isTrue);
      gate.complete(_page([2], last: true));
      await Future.wait(<Future<void>>[first, second]);

      verify(
        productRepository.searchProducts(
          name: anyNamed('name'),
          brandId: anyNamed('brandId'),
          categoryId: anyNamed('categoryId'),
          page: 1,
          size: anyNamed('size'),
          sort: anyNamed('sort'),
        ),
      ).called(1);
    });

    test('stops requesting once the last page is reached', () async {
      _stubPage(productRepository, 0, () async => _page([1], last: true));

      await provider.loadInitial();
      await provider.loadNextPage();

      verifyNever(
        productRepository.searchProducts(
          name: anyNamed('name'),
          brandId: anyNamed('brandId'),
          categoryId: anyNamed('categoryId'),
          page: 1,
          size: anyNamed('size'),
          sort: anyNamed('sort'),
        ),
      );
    });

    test('a next-page error preserves the existing products', () async {
      _stubPage(productRepository, 0, () async => _page([1, 2], last: false));
      _stubPage(
        productRepository,
        1,
        () async => throw const AppException(message: 'page failed'),
      );

      await provider.loadInitial();
      await provider.loadNextPage();

      expect(provider.status, ProductListStatus.ready);
      expect(provider.products.map((p) => p.id), <int>[1, 2]);
      expect(provider.loadingNextPage, isFalse);
      expect(provider.nextPageError?.message, 'page failed');
    });

    test('discards a stale next-page response after a reload', () async {
      _stubPage(productRepository, 0, () async => _page([1, 2], last: false));
      final gate = Completer<PageResponse<ProductSummaryResponse>>();
      _stubPage(productRepository, 1, () => gate.future);

      await provider.loadInitial();
      final pending = provider.loadNextPage(); // page 1 hangs in flight

      // A search change reloads the list from page 0 with different content.
      _stubPage(productRepository, 0, () async => _page([9], last: true));
      await provider.setSearchText('zoom');

      // The stale page-1 response now arrives and must be ignored.
      gate.complete(_page([3, 4], last: false));
      await pending;

      expect(provider.products.map((p) => p.id), <int>[9]);
      expect(provider.status, ProductListStatus.ready);
      expect(provider.loadingNextPage, isFalse);
    });
  });

  group('lifecycle', () {
    test('a load that completes after dispose does not throw', () async {
      final gate = Completer<PageResponse<ProductSummaryResponse>>();
      _stubPage(productRepository, 0, () => gate.future);

      final pending = provider.loadInitial();
      provider.dispose();
      gate.complete(_page([1], last: true));

      // No notifyListeners fires after dispose, so this completes normally.
      await expectLater(pending, completes);
    });
  });
}
