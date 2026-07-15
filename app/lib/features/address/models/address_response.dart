import 'package:json_annotation/json_annotation.dart';

part 'address_response.g.dart';

/// A customer's shipping address (dto-spec §8). The server owns the
/// exactly-one-default-per-user invariant; the client renders [isDefault] as
/// returned and never computes it (business-rules → Shipping Address).
@JsonSerializable(createToJson: false)
class AddressResponse {
  const AddressResponse({
    required this.id,
    required this.recipientName,
    required this.recipientPhone,
    required this.province,
    required this.district,
    required this.ward,
    required this.streetAddress,
    required this.isDefault,
  });

  factory AddressResponse.fromJson(Map<String, dynamic> json) =>
      _$AddressResponseFromJson(json);

  final int id;
  final String recipientName;
  final String recipientPhone;
  final String province;
  final String district;
  final String ward;
  final String streetAddress;
  final bool isDefault;
}
