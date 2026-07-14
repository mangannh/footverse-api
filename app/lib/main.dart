import 'package:flutter/widgets.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app.dart';
import 'core/network/auth_interceptor.dart';
import 'core/network/dio_client.dart';
import 'core/router/app_router.dart';
import 'core/storage/token_storage.dart';
import 'features/auth/providers/auth_provider.dart';
import 'features/auth/repositories/auth_repository.dart';
import 'features/product/repositories/brand_repository.dart';
import 'features/product/repositories/category_repository.dart';
import 'features/product/repositories/product_repository.dart';

/// Composition root: builds the dependency graph, wires the auth interceptor
/// into the main [Dio], restores any persisted session, then starts the app.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final tokenStorage = TokenStorage(await SharedPreferences.getInstance());

  // The main client carries the auth interceptor; a second, auth-free client
  // performs the refresh call so it can never re-enter the refresh logic.
  final dio = createDio();
  final refreshDio = createDio();

  final authProvider = AuthProvider(AuthRepository(dio), tokenStorage);

  // Insert before the error interceptor so the auth interceptor sees the raw
  // 401 first (attaches the bearer, runs the single refresh-and-retry).
  dio.interceptors.insert(
    0,
    AuthInterceptor(
      dio: dio,
      refreshDio: refreshDio,
      tokenStorage: tokenStorage,
      onSessionExpired: authProvider.onSessionExpired,
    ),
  );

  authProvider.restoreSession();

  runApp(
    FootVerseApp(
      authProvider: authProvider,
      router: createAppRouter(
        authProvider,
        ProductRepository(dio),
        CategoryRepository(dio),
        BrandRepository(dio),
      ),
    ),
  );
}
