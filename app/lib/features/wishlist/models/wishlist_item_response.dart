import 'package:json_annotation/json_annotation.dart';

import '../../product/models/decimal_json.dart';

part 'wishlist_item_response.g.dart';

/// One wishlist line — the wishlist row id combined with the product summary
/// (dto-spec §13). [basePrice] is server-owned and rendered as delivered; the
/// client never recomputes money (dto-spec §1; business-rules). [available] is
/// the server's derived purchasability. The list order is decided entirely by
/// the server (most-recently-added first); the client never reorders it.
@JsonSerializable(createToJson: false)
class WishlistItemResponse {
  const WishlistItemResponse({
    required this.id,
    required this.productId,
    required this.productName,
    required this.primaryImageUrl,
    required this.basePrice,
    required this.available,
  });

  factory WishlistItemResponse.fromJson(Map<String, dynamic> json) =>
      _$WishlistItemResponseFromJson(json);

  final int id;
  final int productId;
  final String productName;
  final String? primaryImageUrl;
  @JsonKey(fromJson: decimalFromJson)
  final double basePrice;
  final bool available;
}
