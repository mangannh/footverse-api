import 'package:go_router/go_router.dart';

import '../../features/auth/providers/auth_provider.dart';
import '../../features/auth/screens/account_screen.dart';
import '../../features/auth/screens/login_screen.dart';
import '../../features/auth/screens/register_screen.dart';
import '../../features/product/repositories/brand_repository.dart';
import '../../features/product/repositories/category_repository.dart';
import '../../features/product/repositories/product_repository.dart';
import '../../features/product/screens/product_detail_screen.dart';
import '../../features/product/screens/product_list_screen.dart';
import 'app_routes.dart';

// Path definitions are private to the router: the single place any URL path
// appears. `productDetail` is a child of `catalog`, so navigating to it builds a
// catalog → detail stack and the automatic back button returns to the list.
const String _catalogPath = '/';
const String _productDetailPath = 'products/:id';
const String _loginPath = '/login';
const String _registerPath = '/register';
const String _accountPath = '/account';

/// Builds the `go_router` configuration (flutter-guidelines §Routing).
///
/// The app opens on the public catalog (assumption 4); guests reach every
/// catalog route while the signed-in-only [AppRoute.account] requires a token.
/// [authProvider] is both the redirect's auth source and the router's
/// [GoRouter.refreshListenable], so a change in auth state re-evaluates the
/// guard and the router redirects accordingly (sprint-6-plan item 07). The
/// catalog repositories are injected here from the composition root so the
/// catalog screen can build its provider without any widget touching `Dio`.
GoRouter createAppRouter(
  AuthProvider authProvider,
  ProductRepository productRepository,
  CategoryRepository categoryRepository,
  BrandRepository brandRepository,
) {
  return GoRouter(
    initialLocation: _catalogPath,
    refreshListenable: authProvider,
    redirect: (context, state) => _guard(state, authProvider),
    routes: <RouteBase>[
      GoRoute(
        path: _catalogPath,
        name: AppRoute.catalog,
        builder: (context, state) => ProductListScreen(
          productRepository: productRepository,
          categoryRepository: categoryRepository,
          brandRepository: brandRepository,
        ),
        routes: <RouteBase>[
          GoRoute(
            path: _productDetailPath,
            name: AppRoute.productDetail,
            builder: (context, state) => ProductDetailScreen(
              productId: int.parse(state.pathParameters['id']!),
              productRepository: productRepository,
            ),
          ),
        ],
      ),
      GoRoute(
        path: _loginPath,
        name: AppRoute.login,
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: _registerPath,
        name: AppRoute.register,
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
        path: _accountPath,
        name: AppRoute.account,
        builder: (context, state) => const AccountScreen(),
      ),
    ],
  );
}

/// The single redirect rule: it guards the authenticated-only area and bounces a
/// signed-in user away from the auth screens. It reads state only — no business
/// logic, no side effects (flutter-guidelines §Routing).
String? _guard(GoRouterState state, AuthProvider authProvider) {
  final authenticated = authProvider.isAuthenticated;
  final location = state.matchedLocation;

  final requiresAuth = location == _accountPath;
  final onAuthScreen = location == _loginPath || location == _registerPath;

  if (requiresAuth && !authenticated) {
    // Remember where the user was headed so login can return them there.
    return Uri(
      path: _loginPath,
      queryParameters: <String, String>{'from': location},
    ).toString();
  }
  if (onAuthScreen && authenticated) {
    final from = state.uri.queryParameters['from'];
    return (from != null && from.isNotEmpty) ? from : _accountPath;
  }
  return null;
}
