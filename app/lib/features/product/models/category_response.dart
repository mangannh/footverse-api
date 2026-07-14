import 'package:json_annotation/json_annotation.dart';

part 'category_response.g.dart';

/// A product category (dto-spec §10).
@JsonSerializable(createToJson: false)
class CategoryResponse {
  const CategoryResponse({
    required this.id,
    required this.name,
    this.description,
  });

  factory CategoryResponse.fromJson(Map<String, dynamic> json) =>
      _$CategoryResponseFromJson(json);

  final int id;
  final String name;
  final String? description;
}
