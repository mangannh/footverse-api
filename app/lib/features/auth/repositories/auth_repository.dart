import 'package:dio/dio.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/models/api_response.dart';
import '../models/auth_response.dart';
import '../models/login_request.dart';
import '../models/refresh_token_request.dart';
import '../models/register_request.dart';

/// The typed client of the four frozen auth endpoints (dto-spec §20).
///
/// It only calls the API, unwraps the [ApiResponse] envelope, and returns the
/// typed payload — throwing [AppException] on failure (the injected [Dio]'s
/// `ErrorInterceptor` has already mapped the transport error). It never touches
/// token storage, navigation, or auth state; the provider composes those
/// (flutter-guidelines §Error Handling; sprint-6-plan item 05).
class AuthRepository {
  const AuthRepository(this._dio);

  static const String _registerPath = '/api/v1/auth/register';
  static const String _loginPath = '/api/v1/auth/login';
  static const String _refreshPath = '/api/v1/auth/refresh';
  static const String _logoutPath = '/api/v1/auth/logout';

  final Dio _dio;

  Future<AuthResponse> register(RegisterRequest request) =>
      _authenticate(_registerPath, request.toJson());

  Future<AuthResponse> login(LoginRequest request) =>
      _authenticate(_loginPath, request.toJson());

  Future<AuthResponse> refresh(RefreshTokenRequest request) =>
      _authenticate(_refreshPath, request.toJson());

  Future<void> logout(RefreshTokenRequest request) async {
    try {
      await _dio.post<void>(_logoutPath, data: request.toJson());
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  Future<AuthResponse> _authenticate(
    String path,
    Map<String, dynamic> body,
  ) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(path, data: body);
      final envelope = ApiResponse<AuthResponse>.fromJson(
        response.data!,
        (json) => AuthResponse.fromJson(json! as Map<String, dynamic>),
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
