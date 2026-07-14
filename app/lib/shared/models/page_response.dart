import 'package:json_annotation/json_annotation.dart';

part 'page_response.g.dart';

/// Pagination wrapper for list endpoints (dto-spec §5).
@JsonSerializable(genericArgumentFactories: true)
class PageResponse<T> {
  const PageResponse({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.last,
  });

  factory PageResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Object? json) fromJsonT,
  ) => _$PageResponseFromJson(json, fromJsonT);

  /// Page of items.
  final List<T> content;

  /// Zero-based page index.
  final int page;

  /// Page size.
  final int size;

  /// Total matching elements.
  final int totalElements;

  /// Total pages.
  final int totalPages;

  /// Whether this is the last page.
  final bool last;

  Map<String, dynamic> toJson(Object? Function(T value) toJsonT) =>
      _$PageResponseToJson(this, toJsonT);
}
