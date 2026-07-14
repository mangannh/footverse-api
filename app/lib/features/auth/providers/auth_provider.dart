import 'package:flutter/foundation.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/storage/token_storage.dart';
import '../models/auth_response.dart';
import '../models/login_request.dart';
import '../models/refresh_token_request.dart';
import '../models/register_request.dart';
import '../models/user_response.dart';
import '../repositories/auth_repository.dart';

/// The three states of the client's authentication session.
enum AuthStatus {
  /// The session has not been restored from storage yet (startup default).
  unknown,

  /// A token pair is present; the user is treated as signed in.
  authenticated,

  /// No token pair is present; the user is signed out.
  unauthenticated,
}

/// Owns the signed-in state machine (flutter-guidelines §State Management).
///
/// It exposes the current [status] and [user], and drives register / login /
/// logout through the [AuthRepository], persisting the token pair through the
/// [TokenStorage] (never touching `SharedPreferences` directly). It exposes
/// state only — it builds no widgets and performs no navigation; the router
/// reads [status] to redirect (sprint-6-plan item 06).
class AuthProvider extends ChangeNotifier {
  AuthProvider(this._authRepository, this._tokenStorage);

  final AuthRepository _authRepository;
  final TokenStorage _tokenStorage;

  AuthStatus _status = AuthStatus.unknown;
  UserResponse? _user;

  /// The current authentication status.
  AuthStatus get status => _status;

  /// The authenticated user's profile, or null when signed out or not yet
  /// hydrated (a restored session carries only the token pair, not the user;
  /// dto-spec §6 delivers the user only on register / login / refresh).
  UserResponse? get user => _user;

  /// Convenience flag for the router redirect (flutter-guidelines §Routing).
  bool get isAuthenticated => _status == AuthStatus.authenticated;

  /// Restores the session at startup from the persisted token pair.
  ///
  /// It only reads whether a token exists — it never decodes the JWT, checks
  /// expiry, or refreshes (security-spec §2 makes those a per-request concern of
  /// the `AuthInterceptor`, not startup). The user profile is not persisted, so
  /// it stays null until the next register / login / refresh.
  void restoreSession() {
    final hasSession = _tokenStorage.readAccessToken() != null;
    _status = hasSession
        ? AuthStatus.authenticated
        : AuthStatus.unauthenticated;
    notifyListeners();
  }

  /// Registers a new account and signs the user in with the returned pair.
  ///
  /// Throws [AppException] on failure (the state is left unchanged); the caller
  /// renders the error (flutter-guidelines §Error Handling).
  Future<void> register(RegisterRequest request) async {
    final auth = await _authRepository.register(request);
    await _startSession(auth);
  }

  /// Logs in and signs the user in with the returned pair.
  ///
  /// Throws [AppException] on failure (the state is left unchanged); the caller
  /// renders the error (flutter-guidelines §Error Handling).
  Future<void> login(LoginRequest request) async {
    final auth = await _authRepository.login(request);
    await _startSession(auth);
  }

  /// Logs out: revokes the refresh token server-side, then always clears the
  /// local tokens and signs out — **even if the server call fails**
  /// (flutter-guidelines §Storage).
  Future<void> logout() async {
    final refreshToken = _tokenStorage.readRefreshToken();
    try {
      if (refreshToken != null) {
        await _authRepository.logout(
          RefreshTokenRequest(refreshToken: refreshToken),
        );
      }
    } on AppException {
      // Server-side revocation is best-effort; local sign-out always proceeds.
    } finally {
      await _tokenStorage.clearTokens();
      _clearSession();
    }
  }

  /// Signals a signed-out session after the `AuthInterceptor` failed to refresh
  /// and already cleared the persisted tokens. It resets the in-memory state so
  /// the router redirects to login (security-spec §2).
  void onSessionExpired() => _clearSession();

  Future<void> _startSession(AuthResponse auth) async {
    await _tokenStorage.saveTokens(auth.accessToken, auth.refreshToken);
    _user = auth.user;
    _status = AuthStatus.authenticated;
    notifyListeners();
  }

  void _clearSession() {
    _user = null;
    _status = AuthStatus.unauthenticated;
    notifyListeners();
  }
}
