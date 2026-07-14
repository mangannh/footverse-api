import 'package:json_annotation/json_annotation.dart';

import 'user_response.dart';

part 'auth_response.g.dart';

/// Result of a successful register / login / refresh (dto-spec §6).
@JsonSerializable(createToJson: false)
class AuthResponse {
  const AuthResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.tokenType,
    required this.user,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) =>
      _$AuthResponseFromJson(json);

  final String accessToken;
  final String refreshToken;
  final int expiresIn;
  final String tokenType;
  final UserResponse user;
}
