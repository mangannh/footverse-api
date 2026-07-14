import 'package:dio/dio.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/models/api_response.dart';
import '../models/category_response.dart';

/// The typed client of the frozen public category read (dto-spec §20).
///
/// It only calls the API, unwraps the [ApiResponse] envelope, and returns the
/// typed payload — throwing [AppException] on failure. Public endpoint,
/// independent of authentication.
class CategoryRepository {
  const CategoryRepository(this._dio);

  static const String _categoriesPath = '/api/v1/categories';

  final Dio _dio;

  /// `GET /categories` — all categories.
  Future<List<CategoryResponse>> getCategories() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(_categoriesPath);
      final envelope = ApiResponse<List<CategoryResponse>>.fromJson(
        response.data!,
        (json) => (json! as List<dynamic>)
            .map(
              (item) => CategoryResponse.fromJson(item as Map<String, dynamic>),
            )
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
