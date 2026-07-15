import 'package:flutter/material.dart';

import '../models/cart_item_response.dart';

/// One cart line (dto-spec §12): image, name, colour / size, server-computed
/// `unitPrice` and `lineTotal`, a quantity stepper, availability, and a separate
/// remove action. It renders only — every money value is displayed exactly as
/// the server delivered it and the client computes nothing (dto-spec §1).
///
/// [enabled] is false while any mutation is in flight, disabling every affordance
/// so mutations stay single-flight. [onDecrement] is null at quantity 1 (the
/// `quantity ≥ 1` lower bound — validation-spec §7), which disables the button so
/// decrementing then does nothing; removing a line is only the [onRemove] action.
class CartLineTile extends StatelessWidget {
  const CartLineTile({
    super.key,
    required this.item,
    required this.enabled,
    required this.onIncrement,
    required this.onDecrement,
    required this.onRemove,
  });

  final CartItemResponse item;
  final bool enabled;
  final VoidCallback onIncrement;
  final VoidCallback? onDecrement;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                _Thumbnail(imageUrl: item.productImageUrl),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        item.productName,
                        style: textTheme.titleMedium,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${item.color} · ${item.size}',
                        style: textTheme.bodySmall,
                      ),
                      const SizedBox(height: 4),
                      Text('${item.unitPrice}', style: textTheme.bodyMedium),
                      if (!item.available) ...<Widget>[
                        const SizedBox(height: 6),
                        const _UnavailableFlag(),
                      ],
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: <Widget>[
                _QuantityStepper(
                  quantity: item.quantity,
                  onIncrement: enabled ? onIncrement : null,
                  onDecrement: enabled ? onDecrement : null,
                ),
                const Spacer(),
                Text('${item.lineTotal}', style: textTheme.titleMedium),
              ],
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: enabled ? onRemove : null,
                icon: const Icon(Icons.delete_outline),
                label: const Text('Remove'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A +/- quantity control. The decrement is disabled when [onDecrement] is null
/// (at quantity 1 or while a mutation is in flight), so it never drives quantity
/// below 1 and never removes a line.
class _QuantityStepper extends StatelessWidget {
  const _QuantityStepper({
    required this.quantity,
    required this.onIncrement,
    required this.onDecrement,
  });

  final int quantity;
  final VoidCallback? onIncrement;
  final VoidCallback? onDecrement;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        IconButton(
          onPressed: onDecrement,
          icon: const Icon(Icons.remove),
          tooltip: 'Decrease quantity',
          visualDensity: VisualDensity.compact,
        ),
        Text('$quantity', style: Theme.of(context).textTheme.titleMedium),
        IconButton(
          onPressed: onIncrement,
          icon: const Icon(Icons.add),
          tooltip: 'Increase quantity',
          visualDensity: VisualDensity.compact,
        ),
      ],
    );
  }
}

/// The unavailable marker for a line whose variant is inactive or out of stock.
/// It pairs an icon with text so state is not conveyed by colour alone
/// (flutter-guidelines §Accessibility).
class _UnavailableFlag extends StatelessWidget {
  const _UnavailableFlag();

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Icon(Icons.error_outline, size: 16, color: colorScheme.error),
        const SizedBox(width: 4),
        Text(
          'Unavailable',
          style: Theme.of(
            context,
          ).textTheme.bodySmall?.copyWith(color: colorScheme.error),
        ),
      ],
    );
  }
}

/// The line thumbnail: the product image, or a placeholder when the line has no
/// image or the image fails to load.
class _Thumbnail extends StatelessWidget {
  const _Thumbnail({required this.imageUrl});

  final String? imageUrl;

  static const double _size = 64;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final placeholder = Container(
      width: _size,
      height: _size,
      color: colorScheme.surfaceContainerHighest,
      child: Icon(
        Icons.image_not_supported_outlined,
        color: colorScheme.outline,
      ),
    );

    final url = imageUrl;
    if (url == null || url.isEmpty) {
      return placeholder;
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: Image.network(
        url,
        width: _size,
        height: _size,
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) => placeholder,
        loadingBuilder: (context, child, progress) => progress == null
            ? child
            : const SizedBox(
                width: _size,
                height: _size,
                child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
              ),
      ),
    );
  }
}
