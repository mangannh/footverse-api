import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/shared/models/field_error.dart';
import 'package:footverse/shared/models/page_response.dart';

FieldError _fieldErrorFromJson(Object? json) =>
    FieldError.fromJson(json! as Map<String, dynamic>);

void main() {
  group('PageResponse', () {
    // PageResponse.from(...) shape (dto-spec §5).
    final json = <String, dynamic>{
      'content': <Map<String, dynamic>>[
        <String, dynamic>{'field': 'a', 'message': 'first'},
        <String, dynamic>{'field': 'b', 'message': 'second'},
      ],
      'page': 0,
      'size': 20,
      'totalElements': 2,
      'totalPages': 1,
      'last': true,
    };

    test('deserializes paging metadata and generic content', () {
      final page = PageResponse<FieldError>.fromJson(json, _fieldErrorFromJson);

      expect(page.content, hasLength(2));
      expect(page.content.first, isA<FieldError>());
      expect(page.content.first.field, 'a');
      expect(page.page, 0);
      expect(page.size, 20);
      expect(page.totalElements, 2);
      expect(page.totalPages, 1);
      expect(page.last, isTrue);
    });

    test('round-trips through toJson / fromJson', () {
      final original = PageResponse<FieldError>.fromJson(
        json,
        _fieldErrorFromJson,
      );

      final encoded =
          jsonDecode(jsonEncode(original.toJson((value) => value.toJson())))
              as Map<String, dynamic>;
      final restored = PageResponse<FieldError>.fromJson(
        encoded,
        _fieldErrorFromJson,
      );

      expect(restored.content, hasLength(2));
      expect(restored.content.last.field, 'b');
      expect(restored.totalElements, original.totalElements);
      expect(restored.last, original.last);
    });
  });
}
