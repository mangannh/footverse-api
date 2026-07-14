import 'package:flutter/foundation.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/models/page_response.dart';
import '../models/brand_response.dart';
import '../models/category_response.dart';
import '../models/product_summary_response.dart';
import '../repositories/brand_repository.dart';
import '../repositories/category_repository.dart';
import '../repositories/product_repository.dart';

/// Lifecycle of the first-page load, which drives the full-screen states.
enum ProductListStatus { loading, ready, error }

/// Owns the catalog list state: the active search text / brand / category /
/// sort, the accumulated products, the paging cursor, and the loading / error /
/// empty states (flutter-guidelines §State Management).
///
/// It coordinates state and calls the repositories only; it holds no UI and
/// never computes money or aggregates (business-rules — the server is the single
/// source of truth). Pagination is infinite scrolling: [loadNextPage] appends
/// the next page, is single-flight, and stops once the last page is reached.
class ProductListProvider extends ChangeNotifier {
  ProductListProvider(
    this._productRepository,
    this._categoryRepository,
    this._brandRepository,
  );

  static const int _pageSize = 20;

  final ProductRepository _productRepository;
  final CategoryRepository _categoryRepository;
  final BrandRepository _brandRepository;

  ProductListStatus _status = ProductListStatus.loading;
  List<ProductSummaryResponse> _products = <ProductSummaryResponse>[];
  List<CategoryResponse> _categories = <CategoryResponse>[];
  List<BrandResponse> _brands = <BrandResponse>[];
  String _searchText = '';
  int? _brandId;
  int? _categoryId;
  ProductSort? _sort;
  int _page = 0;
  bool _last = false;
  bool _loadingNextPage = false;
  AppException? _error;
  AppException? _nextPageError;

  // Incremented on every reload so an in-flight load whose captured id no longer
  // matches is a stale request whose response is discarded (a filter/search/sort
  // change that supersedes it).
  int _requestId = 0;

  bool _disposed = false;

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  // Guards against notifying after disposal: an in-flight load that completes
  // once the screen (and this provider) is gone must not call notifyListeners.
  void _safeNotify() {
    if (!_disposed) {
      notifyListeners();
    }
  }

  ProductListStatus get status => _status;
  List<ProductSummaryResponse> get products => List.unmodifiable(_products);
  List<CategoryResponse> get categories => List.unmodifiable(_categories);
  List<BrandResponse> get brands => List.unmodifiable(_brands);
  String get searchText => _searchText;
  int? get brandId => _brandId;
  int? get categoryId => _categoryId;
  ProductSort? get sort => _sort;
  bool get loadingNextPage => _loadingNextPage;

  /// The first-page error, set only while [status] is [ProductListStatus.error].
  AppException? get error => _error;

  /// The last next-page error, set when appending a page failed; the existing
  /// products are preserved.
  AppException? get nextPageError => _nextPageError;

  /// True when the first page loaded successfully but returned no products.
  bool get isEmpty => _status == ProductListStatus.ready && _products.isEmpty;

  /// Loads the filter sources and the first page (called once on screen mount).
  Future<void> loadInitial() async {
    await _loadFilters();
    await _reload();
  }

  /// Retries the first-page load after a [ProductListStatus.error].
  Future<void> retry() => _reload();

  Future<void> setSearchText(String value) async {
    if (value == _searchText) {
      return;
    }
    _searchText = value;
    await _reload();
  }

  Future<void> setBrand(int? brandId) async {
    if (brandId == _brandId) {
      return;
    }
    _brandId = brandId;
    await _reload();
  }

  Future<void> setCategory(int? categoryId) async {
    if (categoryId == _categoryId) {
      return;
    }
    _categoryId = categoryId;
    await _reload();
  }

  Future<void> setSort(ProductSort? sort) async {
    if (sort == _sort) {
      return;
    }
    _sort = sort;
    await _reload();
  }

  /// Appends the next page when scrolling nears the bottom. No-op unless the
  /// first page is shown, and never while a load is in flight or once the last
  /// page has been reached — so no duplicate or surplus request is issued.
  Future<void> loadNextPage() async {
    if (_status != ProductListStatus.ready) {
      return;
    }
    if (_loadingNextPage || _last) {
      return;
    }
    final requestId = _requestId;
    _loadingNextPage = true;
    _nextPageError = null;
    _safeNotify();
    final next = _page + 1;
    try {
      final page = await _search(next);
      if (requestId != _requestId) {
        return;
      }
      _products = <ProductSummaryResponse>[..._products, ...page.content];
      _page = next;
      _last = page.last;
    } on AppException catch (exception) {
      if (requestId != _requestId) {
        return;
      }
      _nextPageError = exception;
    } finally {
      // Only the current request owns the loading flag; a superseded one leaves
      // the reload's state untouched.
      if (requestId == _requestId) {
        _loadingNextPage = false;
        _safeNotify();
      }
    }
  }

  /// Clears the list, resets to the first page, and reloads with the current
  /// filters — the single path every filter/search/sort change funnels through.
  Future<void> _reload() async {
    final requestId = ++_requestId;
    _status = ProductListStatus.loading;
    _products = <ProductSummaryResponse>[];
    _page = 0;
    _last = false;
    _loadingNextPage = false;
    _error = null;
    _nextPageError = null;
    _safeNotify();
    try {
      final page = await _search(0);
      if (requestId != _requestId) {
        return;
      }
      _products = page.content;
      _last = page.last;
      _status = ProductListStatus.ready;
    } on AppException catch (exception) {
      if (requestId != _requestId) {
        return;
      }
      _error = exception;
      _status = ProductListStatus.error;
    }
    _safeNotify();
  }

  Future<void> _loadFilters() async {
    try {
      _categories = await _categoryRepository.getCategories();
      _brands = await _brandRepository.getBrands();
      _safeNotify();
    } on AppException {
      // Filters are optional chrome: a failure leaves them empty rather than
      // breaking the catalog list.
    }
  }

  Future<PageResponse<ProductSummaryResponse>> _search(int page) {
    return _productRepository.searchProducts(
      name: _searchText.isEmpty ? null : _searchText,
      brandId: _brandId,
      categoryId: _categoryId,
      sort: _sort,
      page: page,
      size: _pageSize,
    );
  }
}
