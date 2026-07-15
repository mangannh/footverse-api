import 'package:flutter/foundation.dart';

import '../../../core/error/app_exception.dart';
import '../models/address_request.dart';
import '../models/address_response.dart';
import '../repositories/address_repository.dart';

/// Lifecycle of the address-list load, which drives the full-screen states.
enum AddressListStatus { loading, ready, error }

/// Owns the customer's address-list state (flutter-guidelines §State Management).
///
/// It loads the caller's addresses and drives create / update / delete through
/// the [AddressRepository], reloading the whole list after every **successful**
/// mutation so the server-decided [AddressResponse.isDefault] flags are always
/// rendered exactly as returned — the client never computes which address is
/// default (business-rules → Shipping Address). A load failure moves to
/// [AddressListStatus.error] with a retry affordance; a mutation failure is
/// rethrown to the caller (never swallowed) so the screen renders the enveloped
/// message, leaving the list unchanged. It is screen-scoped — created by the
/// address list screen and disposed with it — so it needs no sign-out reset;
/// `GET /addresses` is an unpaginated list (dto-spec §20), so no paging applies.
class AddressProvider extends ChangeNotifier {
  AddressProvider(this._repository);

  final AddressRepository _repository;

  AddressListStatus _status = AddressListStatus.loading;
  List<AddressResponse> _addresses = <AddressResponse>[];
  AppException? _error;
  bool _disposed = false;

  /// The load lifecycle status.
  AddressListStatus get status => _status;

  /// The caller's addresses, in the server's order.
  List<AddressResponse> get addresses => List.unmodifiable(_addresses);

  /// The load error, set only while [status] is [AddressListStatus.error].
  AppException? get error => _error;

  /// True when the list loaded successfully but the caller has no addresses.
  bool get isEmpty => _status == AddressListStatus.ready && _addresses.isEmpty;

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }

  /// Loads the caller's addresses (called once on screen mount).
  Future<void> load() => _load();

  /// Retries the load after an [AddressListStatus.error].
  Future<void> retry() => _load();

  /// Creates an address, then reloads the list from the server.
  ///
  /// Rethrows [AppException] on failure without touching the list state; the
  /// caller renders the enveloped message (flutter-guidelines §Error Handling).
  Future<void> createAddress(AddressRequest request) async {
    await _repository.createAddress(request);
    await _load();
  }

  /// Updates an address, then reloads the list from the server. Rethrows on
  /// failure (see [createAddress]).
  Future<void> updateAddress(int id, AddressRequest request) async {
    await _repository.updateAddress(id, request);
    await _load();
  }

  /// Deletes an address, then reloads the list from the server. Rethrows on
  /// failure — e.g. `409 ADDRESS_DEFAULT_NOT_DELETABLE` when deleting the default
  /// while other addresses exist (error-spec §8.8) — leaving the list unchanged.
  Future<void> deleteAddress(int id) async {
    await _repository.deleteAddress(id);
    await _load();
  }

  Future<void> _load() async {
    _status = AddressListStatus.loading;
    _error = null;
    _safeNotify();
    try {
      _addresses = await _repository.getAddresses();
      _status = AddressListStatus.ready;
    } on AppException catch (exception) {
      _error = exception;
      _status = AddressListStatus.error;
    }
    _safeNotify();
  }

  // Guards against notifying after disposal: a load that completes once the
  // screen (and this provider) is gone must not call notifyListeners.
  void _safeNotify() {
    if (!_disposed) {
      notifyListeners();
    }
  }
}
