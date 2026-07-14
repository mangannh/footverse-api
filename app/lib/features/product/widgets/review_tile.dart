import 'package:flutter/material.dart';

import '../models/review_response.dart';

/// A single product review (dto-spec §16). Shows the author, rating, comment,
/// date, and an "Edited" indicator — displayed only when `updatedAt` differs
/// from `createdAt`, with no other logic.
class ReviewTile extends StatelessWidget {
  const ReviewTile({super.key, required this.review});

  final ReviewResponse review;

  static String _formatDate(DateTime date) {
    String two(int value) => value.toString().padLeft(2, '0');
    return '${date.year}-${two(date.month)}-${two(date.day)}';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final comment = review.comment;
    final isEdited = review.updatedAt != review.createdAt;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              _Avatar(url: review.userAvatarUrl),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  review.userFullName,
                  style: theme.textTheme.titleSmall,
                ),
              ),
              Icon(Icons.star, size: 16, color: theme.colorScheme.primary),
              const SizedBox(width: 4),
              Text('${review.rating}', style: theme.textTheme.bodySmall),
            ],
          ),
          if (comment != null && comment.isNotEmpty) ...<Widget>[
            const SizedBox(height: 6),
            Text(comment, style: theme.textTheme.bodyMedium),
          ],
          const SizedBox(height: 4),
          Row(
            children: <Widget>[
              Text(
                _formatDate(review.createdAt),
                style: theme.textTheme.bodySmall,
              ),
              if (isEdited) ...<Widget>[
                const SizedBox(width: 8),
                Text('Edited', style: theme.textTheme.bodySmall),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

/// The review author's avatar, or a placeholder when they have none.
class _Avatar extends StatelessWidget {
  const _Avatar({required this.url});

  final String? url;

  @override
  Widget build(BuildContext context) {
    final url = this.url;
    if (url == null || url.isEmpty) {
      return const CircleAvatar(
        radius: 16,
        child: Icon(Icons.person_outline, size: 18),
      );
    }
    return CircleAvatar(radius: 16, backgroundImage: NetworkImage(url));
  }
}
