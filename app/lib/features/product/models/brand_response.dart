import 'package:json_annotation/json_annotation.dart';

part 'brand_response.g.dart';

/// A product brand (dto-spec §11).
@JsonSerializable(createToJson: false)
class BrandResponse {
  const BrandResponse({
    required this.id,
    required this.name,
    this.logoUrl,
    this.description,
  });

  factory BrandResponse.fromJson(Map<String, dynamic> json) =>
      _$BrandResponseFromJson(json);

  final int id;
  final String name;
  final String? logoUrl;
  final String? description;
}
