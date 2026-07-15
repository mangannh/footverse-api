import 'package:flutter/foundation.dart';

import '../../../core/error/app_exception.dart';
import '../models/add_wishlist_item_request.dart';
import '../models/wishlist_item_response.dart';
import '../repositories/wishlist_repository.dart';

/// Lifecycle of the wishlist load, which drives the full-screen states.
enum WishlistStatus { loading, ready, error }

/// Owns the caller's wishlist state (flutter-guidelines §State Management).
///
/// It is **application-scoped** — provided at the app root like `CartProvider`
/// because the product-detail toggle reads [contains] across screens — and loaded
/// when the session becomes authenticated (sign-in or restore); the wishlist
/// screen still refreshes on mount. The list order is the server's
/// (most-recently-added first) and is never reordered on the client. `POST` and
/// `DELETE` do not return the full list, so a successful mutation reloads it from
/// the server, keeping the server the single authority on membership and order
/// (business-rules → Wishlist). Mutations are **single-flight**: while one is in
/// flight the toggle is disabled, so a double-tap issues no second request. On
/// sign-out the state is reset so no wishlist leaks across accounts. `GET
/// /wishlist` is one unpaginated list (dto-spec §20); no paging idiom applies.
class WishlistProvider extends ChangeNotifier {
  WishlistProvider(this._repository);

  final WishlistRepository _repository;

  WishlistStatus _status = WishlistStatus.loading;
  List<WishlistItemResponse> _items = <WishlistItemResponse>[];
  AppException? _error;
  bool _mutating = false;
  bool _disposed = false;

  // Bumped on every [reset] (an account boundary) so a load or mutation still in
  // flight when the session ends is discarded — its stale data never repopulates
  // the state for the next user (business-rules → no cross-account leak).
  int _generation = 0;

  /// The load lifecycle status.
  WishlistStatus get status => _status;

  /// The caller's wishlist, in the server's order (most-recently-added first).
  List<WishlistItemResponse> get items => List.unmodifiable(_items);

  /// The load error, set only while [status] is [WishlistStatus.error].
  AppException? get error => _error;

  /// True while a mutation is in flight; the toggle disables itself so mutations
  /// stay single-flight.
  bool get isMutating => _mutating;

  /// True when the list loaded successfully but the caller has no items.
  bool get isEmpty => _status == WishlistStatus.ready && _items.isEmpty;

  /// Whether [productId] is on the wishlist — the read the toggle renders from.
  /// A plain scan of the loaded list; the wishlist is small and this is called
  /// once per toggle build, so no extra index is kept (no over-engineering).
  bool contains(int productId) =>
      _items.any((item) => item.productId == productId);

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  /// Loads the caller's wishlist (on sign-in / session restore and screen mount).
  Future<void> load() async {
    final generation = _generation;
    _status = WishlistStatus.loading;
    _error = null;
    _safeNotify();
    try {
      final items = await _repository.getWishlist();
      if (generation != _generation) {
        return;
      }
      _items = items;
      _status = WishlistStatus.ready;
    } on AppException catch (exception) {
      if (generation != _generation) {
        return;
      }
      _error = exception;
      _status = WishlistStatus.error;
    }
    _safeNotify();
  }

  /// Retries the load after a [WishlistStatus.error].
  Future<void> retry() => load();

  /// Adds a product to the wishlist (idempotent server-side — a duplicate add is
  /// not an error, business-rules → Wishlist), then reloads the server's list.
  Future<void> add(int productId) => _mutate(
    () => _repository.addProduct(AddWishlistItemRequest(productId: productId)),
  );

  /// Removes a product from the wishlist, then reloads the server's list.
  Future<void> remove(int productId) =>
      _mutate(() => _repository.removeProduct(productId));

  /// Clears the in-memory wishlist on sign-out. It never calls the API — a
  /// signed-out caller has no wishlist (the session bridge reloads on sign-in).
  void reset() {
    _generation++;
    _items = <WishlistItemResponse>[];
    _error = null;
    _mutating = false;
    _status = WishlistStatus.loading;
    _safeNotify();
  }

  /// Runs a single-flight mutation: it no-ops if one is already in flight, and
  /// reloads the server's list after the mutation so membership and order stay
  /// server-authoritative. It rethrows an [AppException] so the caller renders
  /// the enveloped message; the list is left unchanged on failure.
  Future<void> _mutate(Future<void> Function() action) async {
    if (_mutating) {
      return;
    }
    final generation = _generation;
    _mutating = true;
    _safeNotify();
    try {
      await action();
      final items = await _repository.getWishlist();
      if (generation == _generation) {
        _items = items;
        _status = WishlistStatus.ready;
        _error = null;
      }
    } finally {
      if (generation == _generation) {
        _mutating = false;
        _safeNotify();
      }
    }
  }

  // Guards against notifying after disposal: an in-flight load or mutation that
  // completes once this provider is gone must not call notifyListeners.
  void _safeNotify() {
    if (!_disposed) {
      notifyListeners();
    }
  }
}
