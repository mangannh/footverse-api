import 'package:json_annotation/json_annotation.dart';

import '../../product/models/decimal_json.dart';

part 'cart_item_response.g.dart';

/// One line in the caller's cart (dto-spec §12). The money fields
/// ([unitPrice], [lineTotal]) are server-computed and rendered as delivered;
/// the client never recomputes them (dto-spec §1; business-rules). [available]
/// is false when the variant is inactive or out of stock.
@JsonSerializable(createToJson: false)
class CartItemResponse {
  const CartItemResponse({
    required this.id,
    required this.productVariantId,
    required this.productId,
    required this.productName,
    required this.productImageUrl,
    required this.color,
    required this.size,
    required this.unitPrice,
    required this.quantity,
    required this.lineTotal,
    required this.available,
  });

  factory CartItemResponse.fromJson(Map<String, dynamic> json) =>
      _$CartItemResponseFromJson(json);

  final int id;
  final int productVariantId;
  final int productId;
  final String productName;
  final String? productImageUrl;
  final String color;
  final String size;
  @JsonKey(fromJson: decimalFromJson)
  final double unitPrice;
  final int quantity;
  @JsonKey(fromJson: decimalFromJson)
  final double lineTotal;
  final bool available;
}
