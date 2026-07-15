import 'package:json_annotation/json_annotation.dart';

part 'address_request.g.dart';

/// The write body for creating and updating a shipping address (dto-spec §8).
///
/// `CreateAddressRequest` and `UpdateAddressRequest` are field-identical in the
/// frozen contract (dto-spec §8; validation-spec §2 records them as a single
/// requested `AddressRequest`), so one model serializes the body for both calls.
/// [isDefault] is optional (dto-spec §8, `W, O`): omitted when unset so the wire
/// body carries only what the caller provided.
@JsonSerializable(createFactory: false, includeIfNull: false)
class AddressRequest {
  const AddressRequest({
    required this.recipientName,
    required this.recipientPhone,
    required this.province,
    required this.district,
    required this.ward,
    required this.streetAddress,
    this.isDefault,
  });

  final String recipientName;
  final String recipientPhone;
  final String province;
  final String district;
  final String ward;
  final String streetAddress;
  final bool? isDefault;

  Map<String, dynamic> toJson() => _$AddressRequestToJson(this);
}
