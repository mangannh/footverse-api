import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/product/models/review_response.dart';
import 'package:footverse/features/product/widgets/review_tile.dart';

ReviewResponse _review({
  required DateTime createdAt,
  required DateTime updatedAt,
}) => ReviewResponse(
  id: 1,
  userFullName: 'Nguyen Van A',
  rating: 5,
  comment: 'Great shoe.',
  createdAt: createdAt,
  updatedAt: updatedAt,
);

Future<void> _pumpTile(WidgetTester tester, ReviewResponse review) {
  return tester.pumpWidget(
    MaterialApp(
      home: Scaffold(body: ReviewTile(review: review)),
    ),
  );
}

void main() {
  testWidgets('hides "Edited" when updatedAt equals createdAt', (tester) async {
    final at = DateTime.parse('2025-01-15T10:30:00');
    await _pumpTile(tester, _review(createdAt: at, updatedAt: at));

    expect(find.text('Edited'), findsNothing);
  });

  testWidgets('shows "Edited" when updatedAt differs from createdAt', (
    tester,
  ) async {
    await _pumpTile(
      tester,
      _review(
        createdAt: DateTime.parse('2025-01-15T10:30:00'),
        updatedAt: DateTime.parse('2025-01-16T08:00:00'),
      ),
    );

    expect(find.text('Edited'), findsOneWidget);
  });
}
