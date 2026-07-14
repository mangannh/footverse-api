/// Application configuration resolved at build/run time.
///
/// The API base URL is supplied per run via `--dart-define=API_BASE_URL=...`
/// (flutter-guidelines §Configuration); no URL is hardcoded in the app.
class AppConfig {
  const AppConfig._();

  /// Base URL of the FootVerse backend API, injected via `--dart-define`.
  static const String apiBaseUrl = String.fromEnvironment('API_BASE_URL');
}
