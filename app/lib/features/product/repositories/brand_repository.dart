import 'package:dio/dio.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/models/api_response.dart';
import '../models/brand_response.dart';

/// The typed client of the frozen public brand read (dto-spec §20).
///
/// It only calls the API, unwraps the [ApiResponse] envelope, and returns the
/// typed payload — throwing [AppException] on failure. Public endpoint,
/// independent of authentication.
class BrandRepository {
  const BrandRepository(this._dio);

  static const String _brandsPath = '/api/v1/brands';

  final Dio _dio;

  /// `GET /brands` — all brands.
  Future<List<BrandResponse>> getBrands() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(_brandsPath);
      final envelope = ApiResponse<List<BrandResponse>>.fromJson(
        response.data!,
        (json) => (json! as List<dynamic>)
            .map((item) => BrandResponse.fromJson(item as Map<String, dynamic>))
            .toList(),
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
