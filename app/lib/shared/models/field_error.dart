import 'package:json_annotation/json_annotation.dart';

part 'field_error.g.dart';

/// One field-level validation error (dto-spec §5).
@JsonSerializable()
class FieldError {
  const FieldError({required this.field, required this.message});

  factory FieldError.fromJson(Map<String, dynamic> json) =>
      _$FieldErrorFromJson(json);

  /// Offending field name.
  final String field;

  /// Validation message.
  final String message;

  Map<String, dynamic> toJson() => _$FieldErrorToJson(this);
}
