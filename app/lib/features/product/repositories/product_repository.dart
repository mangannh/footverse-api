import 'package:dio/dio.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/models/api_response.dart';
import '../../../shared/models/page_response.dart';
import '../models/product_detail_response.dart';
import '../models/product_summary_response.dart';
import '../models/review_response.dart';

/// The product-search sort fields whitelisted by the backend
/// (validation-spec §6). A value outside the whitelist is rejected server-side
/// with `400 PRODUCT_SORT_INVALID`; modelling the whitelist as an enum makes a
/// non-whitelisted value unrepresentable at the call site.
enum ProductSort {
  createdAt('createdAt'),
  basePrice('basePrice'),
  name('name');

  const ProductSort(this.field);

  /// The value sent as the `sort` query parameter.
  final String field;
}

/// The typed client of the frozen public catalog reads (dto-spec §20).
///
/// It only calls the API, unwraps the [ApiResponse] envelope, and returns the
/// typed payload — throwing [AppException] on failure (the injected [Dio]'s
/// `ErrorInterceptor` has already mapped the transport error). It holds no
/// business logic and is independent of authentication (public endpoints).
class ProductRepository {
  const ProductRepository(this._dio);

  static const String _productsPath = '/api/v1/products';

  final Dio _dio;

  /// `GET /products` — paginated search with the optional `name` / `brandId` /
  /// `categoryId` filters, the whitelisted [sort], and Spring paging
  /// (validation-spec §6). Absent filters are not sent.
  Future<PageResponse<ProductSummaryResponse>> searchProducts({
    String? name,
    int? brandId,
    int? categoryId,
    int page = 0,
    int size = 20,
    ProductSort? sort,
  }) async {
    final query = <String, dynamic>{
      'page': page,
      'size': size,
      'name': ?name,
      'brandId': ?brandId,
      'categoryId': ?categoryId,
      'sort': ?sort?.field,
    };
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        _productsPath,
        queryParameters: query,
      );
      final envelope =
          ApiResponse<PageResponse<ProductSummaryResponse>>.fromJson(
            response.data!,
            (json) => PageResponse<ProductSummaryResponse>.fromJson(
              json! as Map<String, dynamic>,
              (item) => ProductSummaryResponse.fromJson(
                item! as Map<String, dynamic>,
              ),
            ),
          );
      return envelope.data!;
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `GET /products/{id}` — full product detail; an unknown id surfaces as an
  /// `AppException` carrying `PRODUCT_NOT_FOUND` (error-spec §8).
  Future<ProductDetailResponse> getProduct(int id) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '$_productsPath/$id',
      );
      final envelope = ApiResponse<ProductDetailResponse>.fromJson(
        response.data!,
        (json) => ProductDetailResponse.fromJson(json! as Map<String, dynamic>),
      );
      return envelope.data!;
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `GET /products/{id}/reviews` — the product's public reviews, paginated.
  Future<PageResponse<ReviewResponse>> getProductReviews(
    int id, {
    int page = 0,
    int size = 20,
  }) async {
    final query = <String, dynamic>{'page': page, 'size': size};
    try {
      final response = await _dio.get<Map<String, dynamic>>(
        '$_productsPath/$id/reviews',
        queryParameters: query,
      );
      final envelope = ApiResponse<PageResponse<ReviewResponse>>.fromJson(
        response.data!,
        (json) => PageResponse<ReviewResponse>.fromJson(
          json! as Map<String, dynamic>,
          (item) => ReviewResponse.fromJson(item! as Map<String, dynamic>),
        ),
      );
      return envelope.data!;
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
