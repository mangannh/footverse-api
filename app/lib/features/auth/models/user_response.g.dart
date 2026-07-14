// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UserResponse _$UserResponseFromJson(Map<String, dynamic> json) => UserResponse(
  id: (json['id'] as num).toInt(),
  email: json['email'] as String,
  fullName: json['fullName'] as String,
  phone: json['phone'] as String,
  role: $enumDecode(_$RoleEnumMap, json['role']),
  enabled: json['enabled'] as bool,
  createdAt: DateTime.parse(json['createdAt'] as String),
  updatedAt: DateTime.parse(json['updatedAt'] as String),
  avatarUrl: json['avatarUrl'] as String?,
);

const _$RoleEnumMap = {Role.customer: 'CUSTOMER', Role.admin: 'ADMIN'};
