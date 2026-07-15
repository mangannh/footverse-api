import 'package:json_annotation/json_annotation.dart';

part 'add_wishlist_item_request.g.dart';

/// The write body for adding a product to the wishlist (dto-spec §13).
///
/// A duplicate add is an idempotent no-op on the server (business-rules →
/// Wishlist); this model only serializes the wire body.
@JsonSerializable(createFactory: false)
class AddWishlistItemRequest {
  const AddWishlistItemRequest({required this.productId});

  final int productId;

  Map<String, dynamic> toJson() => _$AddWishlistItemRequestToJson(this);
}
