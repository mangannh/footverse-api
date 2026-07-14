import 'package:json_annotation/json_annotation.dart';

import 'field_error.dart';

part 'api_response.g.dart';

/// Standard success/error envelope wrapping every backend response
/// (dto-spec §5).
@JsonSerializable(genericArgumentFactories: true)
class ApiResponse<T> {
  const ApiResponse({
    required this.success,
    required this.message,
    required this.timestamp,
    this.data,
    this.errorCode,
    this.errors,
  });

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Object? json) fromJsonT,
  ) => _$ApiResponseFromJson(json, fromJsonT);

  /// True on success, false on error.
  final bool success;

  /// Human-readable message (e.g. "OK").
  final String message;

  /// Payload; null on error.
  final T? data;

  /// Machine error code; present on error only.
  final String? errorCode;

  /// Field-level validation errors; present on validation failure.
  final List<FieldError>? errors;

  /// Server timestamp of the response.
  final DateTime timestamp;

  Map<String, dynamic> toJson(Object? Function(T value) toJsonT) =>
      _$ApiResponseToJson(this, toJsonT);
}
