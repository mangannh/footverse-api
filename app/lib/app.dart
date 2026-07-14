import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'core/theme/app_colors.dart';
import 'core/theme/app_typography.dart';
import 'features/auth/providers/auth_provider.dart';

/// Root widget of the FootVerse application.
///
/// Provides the [AuthProvider] to the widget tree and drives navigation through
/// [MaterialApp.router] with the [GoRouter] built in the composition root
/// (`main.dart`). The Material 3 light theme is wired here
/// (flutter-guidelines §Theme / §Routing).
class FootVerseApp extends StatelessWidget {
  const FootVerseApp({
    super.key,
    required this.authProvider,
    required this.router,
  });

  final AuthProvider authProvider;
  final GoRouter router;

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<AuthProvider>.value(
      value: authProvider,
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
