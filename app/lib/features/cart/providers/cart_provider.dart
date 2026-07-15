import 'package:flutter/foundation.dart';

import '../../../core/error/app_exception.dart';
import '../models/add_cart_item_request.dart';
import '../models/cart_response.dart';
import '../models/update_cart_item_request.dart';
import '../repositories/cart_repository.dart';

/// Lifecycle of the cart load, which drives the full-screen states.
enum CartStatus { loading, ready, error }

/// Owns the caller's single cart state (flutter-guidelines §State Management).
///
/// It is **application-scoped** — provided at the app root like `AuthProvider`
/// because its [itemCount] feeds the catalog app-bar badge — and loaded when the
/// session becomes authenticated (sign-in or restore); the cart screen still
/// refreshes on mount. Every mutation replaces the whole state with the
/// [CartResponse] the server returns (each cart endpoint returns the full cart,
/// dto-spec §20), so the client never edits a line or recomputes money or
/// aggregates (dto-spec §1; business-rules → Shopping Cart). Mutations are
/// **single-flight**: while one is in flight the mutating affordances are
/// disabled, so two responses can never apply out of order. On sign-out the
/// state is reset so no cart leaks across accounts. `GET /cart` is one
/// unpaginated aggregate (dto-spec §20); no paging idiom applies.
class CartProvider extends ChangeNotifier {
  CartProvider(this._repository);

  final CartRepository _repository;

  CartStatus _status = CartStatus.loading;
  CartResponse? _cart;
  AppException? _error;
  bool _mutating = false;
  bool _disposed = false;

  // Bumped on every [reset] (an account boundary) so a load or mutation still in
  // flight when the session ends is discarded — its stale cart never repopulates
  // the state for the next user (business-rules → no cross-account leak).
  int _generation = 0;

  /// The load lifecycle status.
  CartStatus get status => _status;

  /// The loaded cart, or null before the first successful load / after a reset.
  CartResponse? get cart => _cart;

  /// The cart badge value — Σ quantity from the server (dto-spec §12), 0 when no
  /// cart is loaded. Never the distinct-line count (`items.length`).
  int get itemCount => _cart?.itemCount ?? 0;

  /// The load error, set only while [status] is [CartStatus.error].
  AppException? get error => _error;

  /// True while a mutation is in flight; the screen disables mutating
  /// affordances so mutations stay single-flight.
  bool get isMutating => _mutating;

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  /// Loads the caller's cart (on sign-in / session restore and on screen mount).
  Future<void> load() async {
    final generation = _generation;
    _status = CartStatus.loading;
    _error = null;
    _safeNotify();
    try {
      final cart = await _repository.getCart();
      if (generation != _generation) {
        return;
      }
      _cart = cart;
      _status = CartStatus.ready;
    } on AppException catch (exception) {
      if (generation != _generation) {
        return;
      }
      _error = exception;
      _status = CartStatus.error;
    }
    _safeNotify();
  }

  /// Retries the load after a [CartStatus.error].
  Future<void> retry() => load();

  /// Adds one unit of a variant to the cart (consumed by sprint-7-plan item 07).
  /// The server merges a repeated variant into the existing line and returns the
  /// full cart (business-rules → Shopping Cart).
  Future<void> addItem(int productVariantId) => _mutate(
    () => _repository.addItem(
      AddCartItemRequest(productVariantId: productVariantId, quantity: 1),
    ),
  );

  /// Updates a line's quantity (`PUT /cart/items/{id}`). The caller enforces the
  /// `quantity ≥ 1` lower bound in the UI; the server stays authoritative
  /// (validation-spec §7).
  Future<void> updateQuantity(int cartItemId, int quantity) => _mutate(
    () => _repository.updateItem(
      cartItemId,
      UpdateCartItemRequest(quantity: quantity),
    ),
  );

  /// Removes a line (`DELETE /cart/items/{id}`), returning the updated cart.
  Future<void> removeItem(int cartItemId) =>
      _mutate(() => _repository.removeItem(cartItemId));

  /// Clears the in-memory cart on sign-out. It never calls the API — a signed-out
  /// caller has no cart to load (the session bridge reloads on the next sign-in).
  void reset() {
    _generation++;
    _cart = null;
    _error = null;
    _mutating = false;
    _status = CartStatus.loading;
    _safeNotify();
  }

  /// Runs a single-flight mutation: it no-ops if one is already in flight, and
  /// replaces the whole state with the returned [CartResponse]. It rethrows an
  /// [AppException] so the screen renders the enveloped message; the state is
  /// left unchanged on failure.
  Future<void> _mutate(Future<CartResponse> Function() action) async {
    if (_mutating) {
      return;
    }
    final generation = _generation;
    _mutating = true;
    _safeNotify();
    try {
      final cart = await action();
      if (generation == _generation) {
        _cart = cart;
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
