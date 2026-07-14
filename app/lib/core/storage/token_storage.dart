import 'package:shared_preferences/shared_preferences.dart';

/// The sole reader and writer of the persisted access/refresh token pair
/// (flutter-guidelines §Storage).
///
/// Widgets, providers, and repositories never touch [SharedPreferences]
/// directly — they go through this wrapper. It stores exactly the two tokens
/// and nothing else: no TTL, no expiry, and no user metadata. The access-token
/// lifetime is carried by `AuthResponse.expiresIn` (dto-spec §6) and handled by
/// the auth layer, never persisted here.
class TokenStorage {
  const TokenStorage(this._prefs);

  final SharedPreferences _prefs;

  static const String _accessTokenKey = 'auth.access_token';
  static const String _refreshTokenKey = 'auth.refresh_token';

  /// Persists the access and refresh token pair.
  Future<void> saveTokens(String accessToken, String refreshToken) async {
    await _prefs.setString(_accessTokenKey, accessToken);
    await _prefs.setString(_refreshTokenKey, refreshToken);
  }

  /// Returns the stored access token, or null when none is persisted.
  String? readAccessToken() => _prefs.getString(_accessTokenKey);

  /// Returns the stored refresh token, or null when none is persisted.
  String? readRefreshToken() => _prefs.getString(_refreshTokenKey);

  /// Removes both tokens from storage.
  Future<void> clearTokens() async {
    await _prefs.remove(_accessTokenKey);
    await _prefs.remove(_refreshTokenKey);
  }
}
