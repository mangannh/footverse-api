import 'package:json_annotation/json_annotation.dart';

/// Lifecycle status of a product variant (dto-spec §4).
enum ProductVariantStatus {
  @JsonValue('ACTIVE')
  active,
  @JsonValue('INACTIVE')
  inactive,
}
