import 'package:flutter/material.dart';

import '../models/product_variant_response.dart';
import '../models/product_variant_status.dart';

/// The product's variants (dto-spec §9): each shows its color, size, SKU, price,
/// stock, and status. When [onSelect] is provided the list becomes single-select
/// (sprint-7-plan item 07) — the chosen variant drives the composed add-to-cart
/// affordance; selection is screen-local UI state passed in by the parent, never
/// held here. Without [onSelect] the list stays read-only (sprint-6-plan item 11).
class ProductVariantList extends StatelessWidget {
  const ProductVariantList({
    super.key,
    required this.variants,
    this.selectedVariantId,
    this.onSelect,
  });

  final List<ProductVariantResponse> variants;

  /// The currently selected variant id (screen-local state owned by the parent).
  final int? selectedVariantId;

  /// Selection callback; when null the list is read-only.
  final ValueChanged<int>? onSelect;

  static String _statusLabel(ProductVariantStatus status) {
    switch (status) {
      case ProductVariantStatus.active:
        return 'Active';
      case ProductVariantStatus.inactive:
        return 'Inactive';
    }
  }

  @override
  Widget build(BuildContext context) {
    if (variants.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Text('No variants available.'),
      );
    }
    final selectable = onSelect != null;
    return Column(
      children: <Widget>[
        for (final variant in variants)
          Card(
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: ListTile(
              selected: selectable && variant.id == selectedVariantId,
              leading: selectable
                  ? Icon(
                      variant.id == selectedVariantId
                          ? Icons.radio_button_checked
                          : Icons.radio_button_unchecked,
                    )
                  : null,
              title: Text('${variant.color} · ${variant.size}'),
              subtitle: Text(
                'SKU ${variant.sku} · Stock ${variant.stockQuantity} · '
                '${_statusLabel(variant.status)}',
              ),
              trailing: Text('${variant.price}'),
              onTap: onSelect == null ? null : () => onSelect!(variant.id),
            ),
          ),
      ],
    );
  }
}
