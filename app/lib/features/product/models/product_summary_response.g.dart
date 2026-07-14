// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'product_summary_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProductSummaryResponse _$ProductSummaryResponseFromJson(
  Map<String, dynamic> json,
) => ProductSummaryResponse(
  id: (json['id'] as num).toInt(),
  name: json['name'] as String,
  basePrice: decimalFromJson(json['basePrice']),
  brandName: json['brandName'] as String,
  categoryName: json['categoryName'] as String,
  averageRating: decimalFromJson(json['averageRating']),
  available: json['available'] as bool,
  primaryImageUrl: json['primaryImageUrl'] as String?,
);
