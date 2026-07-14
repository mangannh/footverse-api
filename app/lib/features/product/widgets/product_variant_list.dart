import 'package:flutter/material.dart';

import '../models/product_variant_response.dart';
import '../models/product_variant_status.dart';

/// The product's variants, read-only (sprint-6-plan item 11): each shows its
/// color, size, SKU, price, stock, and status (dto-spec §9). Add-to-cart and
/// variant selection belong to a later shopping sprint and are not offered here.
class ProductVariantList extends StatelessWidget {
  const ProductVariantList({super.key, required this.variants});

  final List<ProductVariantResponse> variants;

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
    return Column(
      children: <Widget>[
        for (final variant in variants)
          Card(
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: ListTile(
              title: Text('${variant.color} · ${variant.size}'),
              subtitle: Text(
                'SKU ${variant.sku} · Stock ${variant.stockQuantity} · '
                '${_statusLabel(variant.status)}',
              ),
              trailing: Text('${variant.price}'),
            ),
          ),
      ],
    );
  }
}
