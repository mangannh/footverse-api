import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'core/theme/app_colors.dart';
import 'core/theme/app_typography.dart';
import 'features/auth/providers/auth_provider.dart';
import 'features/cart/providers/cart_provider.dart';
import 'features/wishlist/providers/wishlist_provider.dart';

/// Root widget of the FootVerse application.
///
/// Provides the application-scoped [AuthProvider], [CartProvider], and
/// [WishlistProvider] to the widget tree and drives navigation through
/// [MaterialApp.router] with the [GoRouter] built in the composition root
/// (`main.dart`). The cart is application-scoped because its `itemCount` feeds
/// the catalog app-bar badge; the wishlist because the product-detail toggle
/// reads its membership across screens (sprint-7-plan items 06 / 08). The
/// Material 3 light theme is wired here (flutter-guidelines §Theme / §Routing).
class FootVerseApp extends StatelessWidget {
  const FootVerseApp({
    super.key,
    required this.authProvider,
    required this.cartProvider,
    required this.wishlistProvider,
    required this.router,
  });

  final AuthProvider authProvider;
  final CartProvider cartProvider;
  final WishlistProvider wishlistProvider;
  final GoRouter router;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthProvider>.value(value: authProvider),
        ChangeNotifierProvider<CartProvider>.value(value: cartProvider),
        ChangeNotifierProvider<WishlistProvider>.value(value: wishlistProvider),
      ],
      child: MaterialApp.router(
        title: 'FootVerse',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(seedColor: AppColors.seed),
          textTheme: AppTypography.textTheme,
        ),
        routerConfig: router,
      ),
    );
  }
}
