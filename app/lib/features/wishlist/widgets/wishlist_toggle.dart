import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/router/app_routes.dart';
import '../../auth/providers/auth_provider.dart';
import '../providers/wishlist_provider.dart';

/// The **wishlist-owned** add/remove affordance the product detail composes
/// (flutter-guidelines §Feature Boundaries / §State Ownership): it keeps wishlist
/// mutation inside the wishlist feature, so the product feature never touches
/// [WishlistProvider]. It takes only the primitive [productId], so it imports no
/// product model.
///
/// It renders membership from [WishlistProvider.contains] and toggles it through
/// [WishlistProvider.add] / [WishlistProvider.remove]; a duplicate add is
/// idempotent server-side and surfaces no error (business-rules → Wishlist). It
/// disables itself while a mutation is in flight (single-flight), routes a
/// signed-out caller to login and back via the existing `from` mechanism, and
/// renders any enveloped error via a `SnackBar`.
class WishlistToggle extends StatelessWidget {
  const WishlistToggle({super.key, required this.productId});

  final int productId;

  @override
  Widget build(BuildContext context) {
    final inWishlist = context.select<WishlistProvider, bool>(
      (p) => p.contains(productId),
    );
    final mutating = context.select<WishlistProvider, bool>(
      (p) => p.isMutating,
    );
    return IconButton(
      onPressed: mutating ? null : () => _toggle(context, inWishlist),
      icon: Icon(inWishlist ? Icons.favorite : Icons.favorite_border),
      tooltip: inWishlist ? 'Remove from wishlist' : 'Add to wishlist',
    );
  }

  Future<void> _toggle(BuildContext context, bool inWishlist) async {
    // Guests cannot use the wishlist: route to login and return to this page
    // after sign-in via the existing `from` mechanism (business-rules → Guest
    // capabilities); the server stays authoritative on the wishlist itself.
    final authProvider = context.read<AuthProvider>();
    if (!authProvider.isAuthenticated) {
      final location = GoRouterState.of(context).uri.toString();
      context.goNamed(
        AppRoute.login,
        queryParameters: <String, String>{'from': location},
      );
      return;
    }

    final wishlistProvider = context.read<WishlistProvider>();
    final messenger = ScaffoldMessenger.of(context);
    try {
      if (inWishlist) {
        await wishlistProvider.remove(productId);
      } else {
        await wishlistProvider.add(productId);
      }
    } on AppException catch (error) {
      messenger.showSnackBar(SnackBar(content: Text(error.message)));
    }
  }
}
