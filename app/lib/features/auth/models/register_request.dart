import 'package:json_annotation/json_annotation.dart';

part 'register_request.g.dart';

/// Register a new customer account (dto-spec §6).
@JsonSerializable(createFactory: false)
class RegisterRequest {
  const RegisterRequest({
    required this.email,
    required this.password,
    required this.fullName,
    required this.phone,
  });

  final String email;
  final String password;
  final String fullName;
  final String phone;

  Map<String, dynamic> toJson() => _$RegisterRequestToJson(this);
}
