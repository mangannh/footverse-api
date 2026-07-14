import 'package:flutter/foundation.dart';

import '../../../core/error/app_exception.dart';
import '../models/product_detail_response.dart';
import '../models/product_image_response.dart';
import '../models/review_response.dart';
import '../repositories/product_repository.dart';

/// Lifecycle of the product-detail load.
enum ProductDetailStatus { loading, ready, error }

/// Lifecycle of the first review page (independent of the detail so a review
/// failure never hides the product).
enum ReviewsStatus { loading, ready, error }

/// Owns the read-only product-detail state: the product itself plus its
/// paginated public reviews (flutter-guidelines §State Management).
///
/// It coordinates state and calls the repository only; it holds no UI and
/// computes no money or aggregates — the server is the single source of truth
/// (business-rules). The review list uses the same infinite-scroll idiom as the
/// catalog list (sprint-6-plan item 10): single-flight [loadNextReviews],
/// stop-at-`last`, preserved list on a next-page error, and a request-generation
/// guard so a reload discards a stale in-flight response.
class ProductDetailProvider extends ChangeNotifier {
  ProductDetailProvider(this._productRepository, this._productId);

  static const int _reviewPageSize = 20;

  final ProductRepository _productRepository;
  final int _productId;

  ProductDetailStatus _status = ProductDetailStatus.loading;
  ProductDetailResponse? _detail;
  List<ProductImageResponse> _images = <ProductImageResponse>[];
  AppException? _error;

  ReviewsStatus _reviewsStatus = ReviewsStatus.loading;
  List<ReviewResponse> _reviews = <ReviewResponse>[];
  int _reviewPage = 0;
  bool _reviewsLast = false;
  bool _loadingNextReviews = false;
  AppException? _reviewsError;
  AppException? _nextReviewsError;

  // Incremented on every first-review load so a superseded in-flight review
  // request (after a reload) has its response discarded.
  int _reviewsRequestId = 0;

  bool _disposed = false;

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  // Guards against notifying after disposal: an in-flight request that completes
  // once the screen (and this provider) is gone must not call notifyListeners.
  void _safeNotify() {
    if (!_disposed) {
      notifyListeners();
    }
  }

  ProductDetailStatus get status => _status;
  ProductDetailResponse? get detail => _detail;

  /// The product's images sorted by `displayOrder` ascending — the client does
  /// not assume the backend order (dto-spec §9).
  List<ProductImageResponse> get images => List.unmodifiable(_images);

  /// The detail-load error, set only while [status] is
  /// [ProductDetailStatus.error]; its `message` is user-safe (error-spec §1).
  AppException? get error => _error;

  /// True when the product does not exist (`404 PRODUCT_NOT_FOUND`), so the UI
  /// can show a dedicated not-found state rather than a generic error.
  bool get isNotFound => _error?.statusCode == 404;

  ReviewsStatus get reviewsStatus => _reviewsStatus;
  List<ReviewResponse> get reviews => List.unmodifiable(_reviews);
  bool get loadingNextReviews => _loadingNextReviews;
  AppException? get reviewsError => _reviewsError;
  AppException? get nextReviewsError => _nextReviewsError;

  /// True when the first review page loaded successfully but was empty.
  bool get isReviewsEmpty =>
      _reviewsStatus == ReviewsStatus.ready && _reviews.isEmpty;

  /// Loads the product detail and, on success, its first review page.
  Future<void> load() async {
    _status = ProductDetailStatus.loading;
    _detail = null;
    _images = <ProductImageResponse>[];
    _error = null;
    _safeNotify();
    try {
      final detail = await _productRepository.getProduct(_productId);
      _detail = detail;
      _images = <ProductImageResponse>[...detail.images]
        ..sort((a, b) => a.displayOrder.compareTo(b.displayOrder));
      _status = ProductDetailStatus.ready;
      _safeNotify();
      await _loadFirstReviews();
    } on AppException catch (exception) {
      _error = exception;
      _status = ProductDetailStatus.error;
      _safeNotify();
    }
  }

  /// Retries the full detail (and reviews) load after an error.
  Future<void> retry() => load();

  /// Retries the first review page after a [ReviewsStatus.error].
  Future<void> retryReviews() => _loadFirstReviews();

  /// Appends the next review page when scrolling nears the bottom. No-op unless
  /// the first page is shown, and never while a load is in flight or once the
  /// last page has been reached — so no duplicate or surplus request is issued.
  Future<void> loadNextReviews() async {
    if (_reviewsStatus != ReviewsStatus.ready) {
      return;
    }
    if (_loadingNextReviews || _reviewsLast) {
      return;
    }
    final requestId = _reviewsRequestId;
    _loadingNextReviews = true;
    _nextReviewsError = null;
    _safeNotify();
    final next = _reviewPage + 1;
    try {
      final page = await _productRepository.getProductReviews(
        _productId,
        page: next,
        size: _reviewPageSize,
      );
      if (requestId != _reviewsRequestId) {
        return;
      }
      _reviews = <ReviewResponse>[..._reviews, ...page.content];
      _reviewPage = next;
      _reviewsLast = page.last;
    } on AppException catch (exception) {
      if (requestId != _reviewsRequestId) {
        return;
      }
      _nextReviewsError = exception;
    } finally {
      if (requestId == _reviewsRequestId) {
        _loadingNextReviews = false;
        _safeNotify();
      }
    }
  }

  Future<void> _loadFirstReviews() async {
    final requestId = ++_reviewsRequestId;
    _reviewsStatus = ReviewsStatus.loading;
    _reviews = <ReviewResponse>[];
    _reviewPage = 0;
    _reviewsLast = false;
    _loadingNextReviews = false;
    _reviewsError = null;
    _nextReviewsError = null;
    _safeNotify();
    try {
      final page = await _productRepository.getProductReviews(
        _productId,
        page: 0,
        size: _reviewPageSize,
      );
      if (requestId != _reviewsRequestId) {
        return;
      }
      _reviews = page.content;
      _reviewsLast = page.last;
      _reviewsStatus = ReviewsStatus.ready;
    } on AppException catch (exception) {
      if (requestId != _reviewsRequestId) {
        return;
      }
      _reviewsError = exception;
      _reviewsStatus = ReviewsStatus.error;
    }
    if (requestId != _reviewsRequestId) {
      return;
    }
    _safeNotify();
  }
}
