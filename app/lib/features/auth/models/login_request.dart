import 'package:json_annotation/json_annotation.dart';

part 'login_request.g.dart';

/// Authenticate and obtain a JWT (dto-spec §6).
@JsonSerializable(createFactory: false)
class LoginRequest {
  const LoginRequest({required this.email, required this.password});

  final String email;
  final String password;

  Map<String, dynamic> toJson() => _$LoginRequestToJson(this);
}
