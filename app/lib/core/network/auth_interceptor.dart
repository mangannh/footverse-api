import 'package:dio/dio.dart';

import '../../shared/models/api_response.dart';
import '../storage/token_storage.dart';

/// Attaches the bearer token and performs the frozen single refresh-and-retry
/// on a `401` (flutter-guidelines §Networking; security-spec §2).
///
/// On every request it attaches `Authorization: Bearer <accessToken>` from
/// [TokenStorage]. On a `401` response it refreshes the token pair **once**
/// through a dedicated bare [Dio] ([_refreshDio], which carries no auth logic of
/// its own), persists the rotated pair, and retries the original request
/// **once** through the main [Dio]. A failed refresh clears the tokens and
/// signals the signed-out state via [_onSessionExpired] (the router redirects).
///
/// It lives in `core`, so it depends on no feature: it parses only the shared
/// [ApiResponse] envelope for the rotated tokens and reports the signed-out
/// state through an injected callback rather than referencing `AuthProvider`.
///
/// The refresh is **single-flight**: concurrent `401`s share one in-flight
/// refresh via [_ongoingRefresh]; the others await its result instead of
/// starting their own. No refresh loop can occur — the refresh runs on a
/// separate [Dio], the auth endpoints are never refreshed, and a request that
/// has already been retried is terminal.
class AuthInterceptor extends Interceptor {
  AuthInterceptor({
    required Dio dio,
    required Dio refreshDio,
    required TokenStorage tokenStorage,
    required void Function() onSessionExpired,
  }) : _dio = dio,
       _refreshDio = refreshDio,
       _tokenStorage = tokenStorage,
       _onSessionExpired = onSessionExpired;

  /// The main client, used to retry the original request.
  final Dio _dio;

  /// A bare client carrying no [AuthInterceptor], used only for the refresh
  /// call so the refresh can never re-enter this interceptor's logic.
  final Dio _refreshDio;

  final TokenStorage _tokenStorage;
  final void Function() _onSessionExpired;

  static const String _authorizationHeader = 'Authorization';
  static const String _refreshPath = '/api/v1/auth/refresh';
  static const String _retriedKey = 'auth.retried';

  /// The single in-flight refresh; resolves to the new access token, or null on
  /// failure. Null when no refresh is running.
  Future<String?>? _ongoingRefresh;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final accessToken = _tokenStorage.readAccessToken();
    if (accessToken != null) {
      options.headers[_authorizationHeader] = 'Bearer $accessToken';
    }
    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    if (!_shouldRefresh(err)) {
      handler.next(err);
      return;
    }

    final newAccessToken = await _refreshSession();
    if (newAccessToken == null) {
      // Refresh failed: tokens are already cleared and the signed-out state is
      // signalled. Propagate the original error; never retry.
      handler.next(err);
      return;
    }

    try {
      handler.resolve(await _retry(err.requestOptions, newAccessToken));
    } on DioException catch (retryError) {
      // A `401` on the retried request is terminal — it never schedules another
      // refresh (the retried request is flagged).
      handler.next(retryError);
    }
  }

  /// Whether [err] is a refreshable `401`: it must not be an auth endpoint and
  /// must not already be a retry (no refresh loop).
  bool _shouldRefresh(DioException err) {
    if (err.response?.statusCode != 401) {
      return false;
    }
    final options = err.requestOptions;
    if (options.extra[_retriedKey] == true) {
      return false;
    }
    return !_isAuthEndpoint(options.path);
  }

  bool _isAuthEndpoint(String path) =>
      path.endsWith('/auth/login') ||
      path.endsWith('/auth/register') ||
      path.endsWith('/auth/logout') ||
      path.endsWith('/auth/refresh');

  /// Returns the shared in-flight refresh, starting one only if none is running
  /// (single-flight). The slot is cleared on completion so a later, genuinely
  /// separate cycle can refresh again.
  Future<String?> _refreshSession() {
    final ongoing = _ongoingRefresh;
    if (ongoing != null) {
      return ongoing;
    }
    final refresh = _performRefresh();
    _ongoingRefresh = refresh;
    refresh.whenComplete(() => _ongoingRefresh = null);
    return refresh;
  }

  Future<String?> _performRefresh() async {
    final refreshToken = _tokenStorage.readRefreshToken();
    if (refreshToken == null) {
      await _signOut();
      return null;
    }
    try {
      final response = await _refreshDio.post<Map<String, dynamic>>(
        _refreshPath,
        data: <String, dynamic>{'refreshToken': refreshToken},
      );
      final envelope = ApiResponse<Map<String, dynamic>>.fromJson(
        response.data!,
        (json) => json! as Map<String, dynamic>,
      );
      final data = envelope.data!;
      final newAccessToken = data['accessToken'] as String;
      final newRefreshToken = data['refreshToken'] as String;
      await _tokenStorage.saveTokens(newAccessToken, newRefreshToken);
      return newAccessToken;
    } on DioException {
      await _signOut();
      return null;
    }
  }

  Future<Response<dynamic>> _retry(RequestOptions options, String accessToken) {
    options.headers[_authorizationHeader] = 'Bearer $accessToken';
    options.extra[_retriedKey] = true;
    return _dio.fetch<dynamic>(options);
  }

  Future<void> _signOut() async {
    await _tokenStorage.clearTokens();
    _onSessionExpired();
  }
}
