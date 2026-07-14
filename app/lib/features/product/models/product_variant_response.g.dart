// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'product_variant_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProductVariantResponse _$ProductVariantResponseFromJson(
  Map<String, dynamic> json,
) => ProductVariantResponse(
  id: (json['id'] as num).toInt(),
  color: json['color'] as String,
  size: json['size'] as String,
  price: decimalFromJson(json['price']),
  stockQuantity: (json['stockQuantity'] as num).toInt(),
  status: $enumDecode(_$ProductVariantStatusEnumMap, json['status']),
  sku: json['sku'] as String,
);

const _$ProductVariantStatusEnumMap = {
  ProductVariantStatus.active: 'ACTIVE',
  ProductVariantStatus.inactive: 'INACTIVE',
};
