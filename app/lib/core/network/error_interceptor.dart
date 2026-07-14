import 'package:dio/dio.dart';

import '../../shared/models/api_response.dart';
import '../error/app_exception.dart';

/// Translates every [DioException] into an [AppException] so no raw transport
/// error leaves the networking layer (flutter-guidelines §Networking).
///
/// When the server answered, the error envelope (error-spec §1) is parsed for
/// its `message`, `errorCode`, and field `errors`; when no response was
/// received (timeout, socket, connection failure) a network [AppException] is
/// produced. The mapped [AppException] is attached to the rejected exception's
/// [DioException.error] and the original stack trace is preserved; repositories
/// rethrow it (flutter-guidelines §Error Handling). It contains no
/// authentication logic — that is the `AuthInterceptor` (Sprint 6 item 06).
class ErrorInterceptor extends Interceptor {
  static const String _networkFailureMessage =
      'Unable to reach the server. Please check your connection.';
  static const String _unexpectedMessage = 'An unexpected error occurred';

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    handler.reject(err.copyWith(error: _map(err)));
  }

  AppException _map(DioException err) {
    final response = err.response;
    if (response == null) {
      return const AppException.network(message: _networkFailureMessage);
    }
    return _mapResponse(response);
  }

  AppException _mapResponse(Response<dynamic> response) {
    final statusCode = response.statusCode;
    final data = response.data;
    if (data is Map<String, dynamic>) {
      try {
        final envelope = ApiResponse<Object?>.fromJson(data, (json) => json);
        return AppException(
          message: envelope.message,
          statusCode: statusCode,
          errorCode: envelope.errorCode,
          errors: envelope.errors,
        );
      } on Object {
        // Body is a JSON object but not a well-formed envelope: fall back to a
        // status-only exception rather than letting the parse error escape.
        return AppException(
          message: _unexpectedMessage,
          statusCode: statusCode,
        );
      }
    }
    return AppException(message: _unexpectedMessage, statusCode: statusCode);
  }
}
