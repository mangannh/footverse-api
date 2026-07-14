// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'product_image_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProductImageResponse _$ProductImageResponseFromJson(
  Map<String, dynamic> json,
) => ProductImageResponse(
  id: (json['id'] as num).toInt(),
  imageUrl: json['imageUrl'] as String,
  displayOrder: (json['displayOrder'] as num).toInt(),
  isPrimary: json['isPrimary'] as bool,
);
