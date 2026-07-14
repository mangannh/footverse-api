import 'package:json_annotation/json_annotation.dart';

part 'refresh_token_request.g.dart';

/// Exchange a valid refresh token for a new token pair, or revoke it at logout
/// (dto-spec §6).
@JsonSerializable(createFactory: false)
class RefreshTokenRequest {
  const RefreshTokenRequest({required this.refreshToken});

  final String refreshToken;

  Map<String, dynamic> toJson() => _$RefreshTokenRequestToJson(this);
}
