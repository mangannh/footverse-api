import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/core/storage/token_storage.dart';
import 'package:footverse/features/auth/models/auth_response.dart';
import 'package:footverse/features/auth/models/login_request.dart';
import 'package:footverse/features/auth/models/refresh_token_request.dart';
import 'package:footverse/features/auth/models/register_request.dart';
import 'package:footverse/features/auth/models/role.dart';
import 'package:footverse/features/auth/models/user_response.dart';
import 'package:footverse/features/auth/providers/auth_provider.dart';
import 'package:footverse/features/auth/repositories/auth_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'auth_provider_test.mocks.dart';

UserResponse _user() => UserResponse(
  id: 1,
  email: 'user@example.com',
  fullName: 'Nguyen Van A',
  phone: '0901234567',
  role: Role.customer,
  enabled: true,
  createdAt: DateTime.parse('2025-01-15T10:30:00'),
  updatedAt: DateTime.parse('2025-01-15T10:30:00'),
);

AuthResponse _auth() => AuthResponse(
  accessToken: 'access-jwt',
  refreshToken: 'refresh-opaque',
  expiresIn: 900,
  tokenType: 'Bearer',
  user: _user(),
);

@GenerateNiceMocks([MockSpec<AuthRepository>()])
void main() {
  late MockAuthRepository repository;
  late TokenStorage tokenStorage;
  late AuthProvider provider;

  setUp(() async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    tokenStorage = TokenStorage(await SharedPreferences.getInstance());
    repository = MockAuthRepository();
    provider = AuthProvider(repository, tokenStorage);
  });

  group('restoreSession', () {
    test('no persisted token → unauthenticated', () {
      provider.restoreSession();

      expect(provider.status, AuthStatus.unauthenticated);
      expect(provider.isAuthenticated, isFalse);
    });

    test('persisted token → authenticated', () async {
      await tokenStorage.saveTokens('access', 'refresh');

      provider.restoreSession();

      expect(provider.status, AuthStatus.authenticated);
      expect(provider.isAuthenticated, isTrue);
    });
  });

  group('login', () {
    const request = LoginRequest(
      email: 'user@example.com',
      password: 'secret12',
    );

    test('success persists the pair and signs in', () async {
      when(repository.login(any)).thenAnswer((_) async => _auth());
      var notifications = 0;
      provider.addListener(() => notifications++);

      await provider.login(request);

      expect(provider.status, AuthStatus.authenticated);
      expect(provider.user?.email, 'user@example.com');
      expect(tokenStorage.readAccessToken(), 'access-jwt');
      expect(tokenStorage.readRefreshToken(), 'refresh-opaque');
      expect(notifications, 1);
    });

    test(
      'failure propagates AppException and leaves state untouched',
      () async {
        when(repository.login(any)).thenThrow(
          const AppException(
            message: 'Invalid credentials',
            statusCode: 401,
            errorCode: 'INVALID_CREDENTIALS',
          ),
        );

        await expectLater(
          provider.login(request),
          throwsA(isA<AppException>()),
        );
        expect(provider.isAuthenticated, isFalse);
        expect(provider.user, isNull);
        expect(tokenStorage.readAccessToken(), isNull);
      },
    );
  });

  group('register', () {
    const request = RegisterRequest(
      email: 'user@example.com',
      password: 'secret12',
      fullName: 'Nguyen Van A',
      phone: '0901234567',
    );

    test('success persists the pair and signs in', () async {
      when(repository.register(any)).thenAnswer((_) async => _auth());

      await provider.register(request);

      expect(provider.status, AuthStatus.authenticated);
      expect(provider.user?.email, 'user@example.com');
      expect(tokenStorage.readAccessToken(), 'access-jwt');
      expect(tokenStorage.readRefreshToken(), 'refresh-opaque');
    });
  });

  group('logout', () {
    test('revokes the refresh token then clears and signs out', () async {
      await tokenStorage.saveTokens('access', 'refresh-opaque');
      when(repository.logout(any)).thenAnswer((_) async {});

      await provider.logout();

      final captured =
          verify(repository.logout(captureAny)).captured.single
              as RefreshTokenRequest;
      expect(captured.refreshToken, 'refresh-opaque');
      expect(tokenStorage.readAccessToken(), isNull);
      expect(tokenStorage.readRefreshToken(), isNull);
      expect(provider.status, AuthStatus.unauthenticated);
      expect(provider.user, isNull);
    });

    test('clears local tokens even when the server call fails', () async {
      await tokenStorage.saveTokens('access', 'refresh-opaque');
      when(
        repository.logout(any),
      ).thenThrow(const AppException(message: 'Server error', statusCode: 500));

      await provider.logout();

      expect(tokenStorage.readAccessToken(), isNull);
      expect(tokenStorage.readRefreshToken(), isNull);
      expect(provider.status, AuthStatus.unauthenticated);
    });
  });

  group('onSessionExpired', () {
    test('resets in-memory state to signed-out', () {
      provider.onSessionExpired();

      expect(provider.status, AuthStatus.unauthenticated);
      expect(provider.user, isNull);
    });
  });
}
