import 'package:json_annotation/json_annotation.dart';

import 'decimal_json.dart';
import 'product_image_response.dart';
import 'product_variant_response.dart';

part 'product_detail_response.g.dart';

/// Full product detail (dto-spec §9). [images] arrive sorted by `displayOrder`
/// ascending; [averageRating] / [reviewCount] are server-computed aggregates the
/// client only displays (business-rules).
@JsonSerializable(createToJson: false)
class ProductDetailResponse {
  const ProductDetailResponse({
    required this.id,
    required this.name,
    required this.basePrice,
    required this.brandId,
    required this.brandName,
    required this.categoryId,
    required this.categoryName,
    required this.images,
    required this.variants,
    required this.averageRating,
    required this.reviewCount,
    required this.available,
    required this.createdAt,
    this.description,
  });

  factory ProductDetailResponse.fromJson(Map<String, dynamic> json) =>
      _$ProductDetailResponseFromJson(json);

  final int id;
  final String name;
  final String? description;
  @JsonKey(fromJson: decimalFromJson)
  final double basePrice;
  final int brandId;
  final String brandName;
  final int categoryId;
  final String categoryName;
  final List<ProductImageResponse> images;
  final List<ProductVariantResponse> variants;
  @JsonKey(fromJson: decimalFromJson)
  final double averageRating;
  final int reviewCount;

  /// Derived purchasability.
  final bool available;
  final DateTime createdAt;
}
