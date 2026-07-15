import 'package:dio/dio.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/models/api_response.dart';
import '../models/add_cart_item_request.dart';
import '../models/cart_response.dart';
import '../models/update_cart_item_request.dart';

/// The typed client of the four frozen cart endpoints (dto-spec §20).
///
/// Every endpoint returns the full [CartResponse] — including
/// `DELETE /cart/items/{id}` — so each method unwraps the [ApiResponse]
/// envelope and returns the typed cart, throwing [AppException] on failure (the
/// injected [Dio]'s `ErrorInterceptor` has already mapped the transport error,
/// and its `AuthInterceptor` has attached the CUSTOMER bearer). It holds no
/// business logic and touches no storage or navigation.
class CartRepository {
  const CartRepository(this._dio);

  static const String _cartPath = '/api/v1/cart';
  static const String _cartItemsPath = '/api/v1/cart/items';

  final Dio _dio;

  /// `GET /cart` — the caller's cart.
  Future<CartResponse> getCart() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(_cartPath);
      return _unwrapCart(response);
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `POST /cart/items` — add a line to the caller's cart.
  Future<CartResponse> addItem(AddCartItemRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        _cartItemsPath,
        data: request.toJson(),
      );
      return _unwrapCart(response);
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `PUT /cart/items/{id}` — update a cart line's quantity.
  Future<CartResponse> updateItem(
    int cartItemId,
    UpdateCartItemRequest request,
  ) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '$_cartItemsPath/$cartItemId',
        data: request.toJson(),
      );
      return _unwrapCart(response);
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `DELETE /cart/items/{id}` — remove a cart line, returning the updated cart.
  Future<CartResponse> removeItem(int cartItemId) async {
    try {
      final response = await _dio.delete<Map<String, dynamic>>(
        '$_cartItemsPath/$cartItemId',
      );
      return _unwrapCart(response);
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  CartResponse _unwrapCart(Response<Map<String, dynamic>> response) {
    final envelope = ApiResponse<CartResponse>.fromJson(
      response.data!,
      (json) => CartResponse.fromJson(json! as Map<String, dynamic>),
    );
    return envelope.data!;
  }

  AppException _asAppException(DioException exception) {
    final error = exception.error;
    if (error is AppException) {
      return error;
    }
    return const AppException(message: 'An unexpected error occurred');
  }
}
