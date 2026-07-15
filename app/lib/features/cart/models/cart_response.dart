import 'package:json_annotation/json_annotation.dart';

import '../../product/models/decimal_json.dart';
import 'cart_item_response.dart';

part 'cart_response.g.dart';

/// The caller's cart with server-computed aggregates (dto-spec §12).
///
/// [subtotal] and [itemCount] are computed by the server and rendered exactly
/// as delivered; the client never recomputes money or aggregates (dto-spec §1;
/// business-rules). [itemCount] is Σ quantity of all lines — the cart badge
/// value — not the distinct-line count (`items.length`).
@JsonSerializable(createToJson: false)
class CartResponse {
  const CartResponse({
    required this.items,
    required this.subtotal,
    required this.itemCount,
  });

  factory CartResponse.fromJson(Map<String, dynamic> json) =>
      _$CartResponseFromJson(json);

  final List<CartItemResponse> items;
  @JsonKey(fromJson: decimalFromJson)
  final double subtotal;
  final int itemCount;
}
