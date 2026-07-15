import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/router/app_routes.dart';
import '../../auth/providers/auth_provider.dart';
import '../providers/cart_provider.dart';

/// The **cart-owned** add-to-cart affordance the product detail composes
/// (flutter-guidelines §Feature Boundaries / §State Ownership): it keeps cart
/// mutation inside the cart feature, so the product feature never touches
/// [CartProvider]. It takes only primitives — the selected [productVariantId] and
/// whether that variant is [purchasable] (the product feature owns the variant
/// data and computes the pre-check) — so it imports no product model.
///
/// It drives [CartProvider.addItem] with quantity 1 (the server merges a repeated
/// variant into the existing line — business-rules → Shopping Cart), disables
/// itself while a cart mutation is in flight (single-flight), routes a signed-out
/// caller to login and back via the existing `from` mechanism, and renders any
/// enveloped rejection (`PRODUCT_VARIANT_INACTIVE` /
/// `PRODUCT_VARIANT_INSUFFICIENT_STOCK`) faithfully via a `SnackBar`. The client
/// pre-check ([purchasable]) mirrors the frozen rule for UX; the server stays
/// authoritative.
class AddToCartButton extends StatelessWidget {
  const AddToCartButton({
    super.key,
    required this.productVariantId,
    required this.purchasable,
  });

  /// The selected variant, or null when no variant is selected yet.
  final int? productVariantId;

  /// The client-side pre-check: true only when the selected variant is active
  /// and in stock. False when nothing is selected.
  final bool purchasable;

  @override
  Widget build(BuildContext context) {
    final mutating = context.select<CartProvider, bool>((p) => p.isMutating);
    final variantId = productVariantId;
    final enabled = purchasable && variantId != null && !mutating;
    return FilledButton.icon(
      onPressed: enabled ? () => _addToCart(context, variantId) : null,
      icon: const Icon(Icons.add_shopping_cart),
      label: const Text('Add to cart'),
    );
  }

  Future<void> _addToCart(BuildContext context, int variantId) async {
    // Guests cannot use the cart: route to login and return to this page after
    // sign-in via the existing `from` mechanism (business-rules → Guest
    // capabilities); the server stays authoritative on the cart itself.
    final authProvider = context.read<AuthProvider>();
    if (!authProvider.isAuthenticated) {
      final location = GoRouterState.of(context).uri.toString();
      context.goNamed(
        AppRoute.login,
        queryParameters: <String, String>{'from': location},
      );
      return;
    }

    final cartProvider = context.read<CartProvider>();
    final messenger = ScaffoldMessenger.of(context);
    try {
      await cartProvider.addItem(variantId);
      messenger.showSnackBar(const SnackBar(content: Text('Added to cart')));
    } on AppException catch (error) {
      messenger.showSnackBar(SnackBar(content: Text(error.message)));
    }
  }
}
