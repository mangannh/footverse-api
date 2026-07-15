import 'package:json_annotation/json_annotation.dart';

part 'add_cart_item_request.g.dart';

/// The write body for adding a line to the cart (dto-spec §12).
///
/// The server merges a repeated variant into the existing line and remains the
/// authority on stock and availability (business-rules → Shopping Cart); this
/// model only serializes the wire body.
@JsonSerializable(createFactory: false)
class AddCartItemRequest {
  const AddCartItemRequest({
    required this.productVariantId,
    required this.quantity,
  });

  final int productVariantId;
  final int quantity;

  Map<String, dynamic> toJson() => _$AddCartItemRequestToJson(this);
}
