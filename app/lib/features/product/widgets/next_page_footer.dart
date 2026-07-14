import 'package:flutter/material.dart';

/// A list footer shown while an additional page loads, or a retry affordance
/// when that page failed (the existing items stay visible above it). Shared by
/// the catalog list and the product-detail review list so both use the same
/// infinite-scroll idiom (sprint-6-plan items 10 / 11).
class NextPageFooter extends StatelessWidget {
  const NextPageFooter({
    super.key,
    required this.loading,
    required this.error,
    required this.onRetry,
  });

  final bool loading;
  final String? error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    final message = error;
    if (message != null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Flexible(child: Text(message, textAlign: TextAlign.center)),
            const SizedBox(width: 8),
            TextButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      );
    }
    return const SizedBox.shrink();
  }
}
