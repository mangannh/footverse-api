// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'wishlist_item_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WishlistItemResponse _$WishlistItemResponseFromJson(
  Map<String, dynamic> json,
) => WishlistItemResponse(
  id: (json['id'] as num).toInt(),
  productId: (json['productId'] as num).toInt(),
  productName: json['productName'] as String,
  primaryImageUrl: json['primaryImageUrl'] as String?,
  basePrice: decimalFromJson(json['basePrice']),
  available: json['available'] as bool,
);
