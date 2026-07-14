import 'package:json_annotation/json_annotation.dart';

import 'decimal_json.dart';
import 'product_variant_status.dart';

part 'product_variant_response.g.dart';

/// A product variant (dto-spec §9). Carries its effective [price]
/// (`priceOverride`, or the product's base price when none).
@JsonSerializable(createToJson: false)
class ProductVariantResponse {
  const ProductVariantResponse({
    required this.id,
    required this.color,
    required this.size,
    required this.price,
    required this.stockQuantity,
    required this.status,
    required this.sku,
  });

  factory ProductVariantResponse.fromJson(Map<String, dynamic> json) =>
      _$ProductVariantResponseFromJson(json);

  final int id;
  final String color;
  final String size;
  @JsonKey(fromJson: decimalFromJson)
  final double price;
  final int stockQuantity;
  final ProductVariantStatus status;
  final String sku;
}
