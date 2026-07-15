import 'package:dio/dio.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/models/api_response.dart';
import '../models/add_wishlist_item_request.dart';
import '../models/wishlist_item_response.dart';

/// The typed client of the three frozen wishlist endpoints (dto-spec §20).
///
/// It only calls the API, unwraps the [ApiResponse] envelope, and returns the
/// typed payload — throwing [AppException] on failure (the injected [Dio]'s
/// `ErrorInterceptor` has already mapped the transport error, and its
/// `AuthInterceptor` has attached the CUSTOMER bearer). It holds no business
/// logic and touches no storage or navigation. The server's ordering
/// (most-recently-added first) is preserved as delivered; a duplicate add
/// resolves idempotently (HTTP `200`) with the existing item — no client-side
/// duplicate handling (business-rules → Wishlist).
class WishlistRepository {
  const WishlistRepository(this._dio);

  static const String _wishlistPath = '/api/v1/wishlist';

  final Dio _dio;

  /// `GET /wishlist` — the caller's wishlist in the server's order.
  Future<List<WishlistItemResponse>> getWishlist() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(_wishlistPath);
      final envelope = ApiResponse<List<WishlistItemResponse>>.fromJson(
        response.data!,
        (json) => (json! as List<dynamic>)
            .map(
              (item) =>
                  WishlistItemResponse.fromJson(item as Map<String, dynamic>),
            )
            .toList(),
      );
      return envelope.data!;
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `POST /wishlist` — add a product; idempotent when already present.
  Future<WishlistItemResponse> addProduct(
    AddWishlistItemRequest request,
  ) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        _wishlistPath,
        data: request.toJson(),
      );
      final envelope = ApiResponse<WishlistItemResponse>.fromJson(
        response.data!,
        (json) => WishlistItemResponse.fromJson(json! as Map<String, dynamic>),
      );
      return envelope.data!;
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `DELETE /wishlist/{productId}` — remove a product from the wishlist.
  Future<void> removeProduct(int productId) async {
    try {
      await _dio.delete<void>('$_wishlistPath/$productId');
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  AppException _asAppException(DioException exception) {
    final error = exception.error;
    if (error is AppException) {
      return error;
    }
    return const AppException(message: 'An unexpected error occurred');
  }
}
