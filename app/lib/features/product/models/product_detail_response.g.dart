// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'product_detail_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProductDetailResponse _$ProductDetailResponseFromJson(
  Map<String, dynamic> json,
) => ProductDetailResponse(
  id: (json['id'] as num).toInt(),
  name: json['name'] as String,
  basePrice: decimalFromJson(json['basePrice']),
  brandId: (json['brandId'] as num).toInt(),
  brandName: json['brandName'] as String,
  categoryId: (json['categoryId'] as num).toInt(),
  categoryName: json['categoryName'] as String,
  images: (json['images'] as List<dynamic>)
      .map((e) => ProductImageResponse.fromJson(e as Map<String, dynamic>))
      .toList(),
  variants: (json['variants'] as List<dynamic>)
      .map((e) => ProductVariantResponse.fromJson(e as Map<String, dynamic>))
      .toList(),
  averageRating: decimalFromJson(json['averageRating']),
  reviewCount: (json['reviewCount'] as num).toInt(),
  available: json['available'] as bool,
  createdAt: DateTime.parse(json['createdAt'] as String),
  description: json['description'] as String?,
);
