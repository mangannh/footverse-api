import 'package:json_annotation/json_annotation.dart';

import 'role.dart';

part 'user_response.g.dart';

/// A user's profile (dto-spec §7). Passwords are never included.
@JsonSerializable(createToJson: false)
class UserResponse {
  const UserResponse({
    required this.id,
    required this.email,
    required this.fullName,
    required this.phone,
    required this.role,
    required this.enabled,
    required this.createdAt,
    required this.updatedAt,
    this.avatarUrl,
  });

  factory UserResponse.fromJson(Map<String, dynamic> json) =>
      _$UserResponseFromJson(json);

  final int id;
  final String email;
  final String fullName;
  final String phone;
  final String? avatarUrl;
  final Role role;
  final bool enabled;
  final DateTime createdAt;
  final DateTime updatedAt;
}
