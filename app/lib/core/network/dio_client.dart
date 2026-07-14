import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../config/app_config.dart';
import 'error_interceptor.dart';
import 'logging_interceptor.dart';

/// Builds the single [Dio] instance shared across the app
/// (flutter-guidelines §Networking).
///
/// It is configured with the [AppConfig.apiBaseUrl] base URL (assumption 6) and
/// JSON request/response defaults. Repositories receive this one instance
/// through constructor injection; no repository constructs its own [Dio] and no
/// widget uses [Dio] directly. The [LoggingInterceptor] is registered only under
/// [kDebugMode] so logging is compiled out of release builds; the
/// [ErrorInterceptor] maps every transport error to an `AppException`.
Dio createDio({String? baseUrl}) {
  final dio = Dio(
    BaseOptions(
      baseUrl: baseUrl ?? AppConfig.apiBaseUrl,
      contentType: Headers.jsonContentType,
      responseType: ResponseType.json,
    ),
  );

  if (kDebugMode) {
    dio.interceptors.add(LoggingInterceptor());
  }
  dio.interceptors.add(ErrorInterceptor());

  return dio;
}
