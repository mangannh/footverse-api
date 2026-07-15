// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'address_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AddressResponse _$AddressResponseFromJson(Map<String, dynamic> json) =>
    AddressResponse(
      id: (json['id'] as num).toInt(),
      recipientName: json['recipientName'] as String,
      recipientPhone: json['recipientPhone'] as String,
      province: json['province'] as String,
      district: json['district'] as String,
      ward: json['ward'] as String,
      streetAddress: json['streetAddress'] as String,
      isDefault: json['isDefault'] as bool,
    );
