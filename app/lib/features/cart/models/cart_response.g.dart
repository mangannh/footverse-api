// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'cart_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CartResponse _$CartResponseFromJson(Map<String, dynamic> json) => CartResponse(
  items: (json['items'] as List<dynamic>)
      .map((e) => CartItemResponse.fromJson(e as Map<String, dynamic>))
      .toList(),
  subtotal: decimalFromJson(json['subtotal']),
  itemCount: (json['itemCount'] as num).toInt(),
);
