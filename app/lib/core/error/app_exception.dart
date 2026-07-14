import '../../shared/models/field_error.dart';

/// A typed client-side error representing a failed API call.
///
/// It carries the fields of the backend error envelope (error-spec §1) — the
/// HTTP [statusCode], the machine [errorCode], the user-safe [message], and the
/// field-level [errors] — and additionally represents a network failure where
/// no response was received ([isNetworkError]). It only represents an error; it
/// performs no error handling.
class AppException implements Exception {
  /// Creates an exception from a backend error envelope.
  const AppException({
    required this.message,
    this.statusCode,
    this.errorCode,
    this.errors,
  }) : isNetworkError = false;

  /// Creates an exception for a network failure where no response was received.
  const AppException.network({required this.message})
    : statusCode = null,
      errorCode = null,
      errors = null,
      isNetworkError = true;

  /// User-safe, human-readable message.
  final String message;

  /// HTTP status code of the response; null when no response was received.
  final int? statusCode;

  /// Machine-readable error code from the envelope; null for a network failure.
  final String? errorCode;

  /// Field-level validation errors; present only on a validation failure.
  final List<FieldError>? errors;

  /// True when the request failed without any response (network failure).
  final bool isNetworkError;

  @override
  String toString() =>
      'AppException(statusCode: $statusCode, errorCode: $errorCode, '
      'message: $message)';
}
