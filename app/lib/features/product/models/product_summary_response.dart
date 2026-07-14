import 'package:json_annotation/json_annotation.dart';

import 'decimal_json.dart';

part 'product_summary_response.g.dart';

/// A compact product for list / search results (dto-spec §9). The server is the
/// single source of truth for [basePrice] and [averageRating]; the client only
/// displays them (business-rules).
@JsonSerializable(createToJson: false)
class ProductSummaryResponse {
  const ProductSummaryResponse({
    required this.id,
    required this.name,
    required this.basePrice,
    required this.brandName,
    required this.categoryName,
    required this.averageRating,
    required this.available,
    this.primaryImageUrl,
  });

  factory ProductSummaryResponse.fromJson(Map<String, dynamic> json) =>
      _$ProductSummaryResponseFromJson(json);

  final int id;
  final String name;
  @JsonKey(fromJson: decimalFromJson)
  final double basePrice;
  final String brandName;
  final String categoryName;
  final String? primaryImageUrl;
  @JsonKey(fromJson: decimalFromJson)
  final double averageRating;

  /// Derived: has ≥ 1 ACTIVE variant with stock > 0.
  final bool available;
}
