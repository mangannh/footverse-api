import 'package:json_annotation/json_annotation.dart';

part 'product_image_response.g.dart';

/// A product image (dto-spec §9). Images are delivered sorted by [displayOrder]
/// ascending inside a [ProductDetailResponse].
@JsonSerializable(createToJson: false)
class ProductImageResponse {
  const ProductImageResponse({
    required this.id,
    required this.imageUrl,
    required this.displayOrder,
    required this.isPrimary,
  });

  factory ProductImageResponse.fromJson(Map<String, dynamic> json) =>
      _$ProductImageResponseFromJson(json);

  final int id;
  final String imageUrl;
  final int displayOrder;
  final bool isPrimary;
}
