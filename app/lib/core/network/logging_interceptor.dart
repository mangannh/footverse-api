import 'dart:developer' as developer;

import 'package:dio/dio.dart';

/// Logs requests, responses, and errors in debug builds only.
///
/// It is registered exclusively under `kDebugMode` (see `createDio`) so no
/// network logging is compiled into release builds. It observes traffic only —
/// method, URI, and status — and carries no authentication or business logic
/// (flutter-guidelines §Networking).
class LoggingInterceptor extends Interceptor {
  static const String _name = 'network';

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    developer.log('--> ${options.method} ${options.uri}', name: _name);
    handler.next(options);
  }

  @override
  void onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) {
    developer.log(
      '<-- ${response.statusCode} ${response.requestOptions.uri}',
      name: _name,
    );
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    developer.log(
      '<-- ERROR ${err.response?.statusCode} ${err.requestOptions.uri}',
      name: _name,
    );
    handler.next(err);
  }
}
