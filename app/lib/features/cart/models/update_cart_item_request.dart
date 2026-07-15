import 'package:json_annotation/json_annotation.dart';

part 'update_cart_item_request.g.dart';

/// The write body for updating a cart line's quantity (dto-spec §12).
///
/// The server remains the authority on the `quantity ≥ 1` and stock constraints
/// (validation-spec §7); this model only serializes the wire body.
@JsonSerializable(createFactory: false)
class UpdateCartItemRequest {
  const UpdateCartItemRequest({required this.quantity});

  final int quantity;

  Map<String, dynamic> toJson() => _$UpdateCartItemRequestToJson(this);
}
