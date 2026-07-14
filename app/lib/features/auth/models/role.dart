import 'package:json_annotation/json_annotation.dart';

/// User role (dto-spec §4).
enum Role {
  @JsonValue('CUSTOMER')
  customer,
  @JsonValue('ADMIN')
  admin,
}
