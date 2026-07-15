import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/router/app_routes.dart';
import '../providers/cart_provider.dart';

/// The catalog app-bar cart action wrapped with the server's `itemCount` badge.
///
/// It is **cart-owned** (flutter-guidelines §Feature Boundaries): the catalog
/// composes it but never touches cart state. The count is
/// [CartProvider.itemCount] — Σ quantity from the server (dto-spec §12), never
/// the distinct-line count — and the badge shows only when it is positive, which
/// happens only for a signed-in caller (the cart is reset to empty on sign-out
/// and never loaded for a guest). The action itself is always present so a guest
/// tap is routed to login by the existing guard (sprint-7-plan item 04).
class CartBadge extends StatelessWidget {
  const CartBadge({super.key});

  @override
  Widget build(BuildContext context) {
    final itemCount = context.select<CartProvider, int>((p) => p.itemCount);
    final button = IconButton(
      icon: const Icon(Icons.shopping_cart_outlined),
      tooltip: 'Cart',
      onPressed: () => context.pushNamed(AppRoute.cart),
    );
    if (itemCount == 0) {
      return button;
    }
    return Badge.count(count: itemCount, child: button);
  }
}
