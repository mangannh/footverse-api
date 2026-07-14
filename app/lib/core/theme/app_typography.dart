import 'package:flutter/material.dart';

/// Typography for FootVerse, applied on top of the Material 3 baseline.
///
/// Uses the Material 3 (2021) English-like geometry; colors are resolved by the
/// active [ThemeData] (flutter-guidelines §Theme).
class AppTypography {
  const AppTypography._();

  /// Base text theme applied to the application.
  static const TextTheme textTheme = Typography.englishLike2021;
}
