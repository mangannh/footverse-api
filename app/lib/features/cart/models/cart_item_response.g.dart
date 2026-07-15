// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'cart_item_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CartItemResponse _$CartItemResponseFromJson(Map<String, dynamic> json) =>
    CartItemResponse(
      id: (json['id'] as num).toInt(),
      productVariantId: (json['productVariantId'] as num).toInt(),
      productId: (json['productId'] as num).toInt(),
      productName: json['productName'] as String,
      productImageUrl: json['productImageUrl'] as String?,
      color: json['color'] as String,
      size: json['size'] as String,
      unitPrice: decimalFromJson(json['unitPrice']),
      quantity: (json['quantity'] as num).toInt(),
      lineTotal: decimalFromJson(json['lineTotal']),
      available: json['available'] as bool,
    );
