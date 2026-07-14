import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/auth/models/auth_response.dart';
import 'package:footverse/features/auth/models/login_request.dart';
import 'package:footverse/features/auth/models/refresh_token_request.dart';
import 'package:footverse/features/auth/models/register_request.dart';
import 'package:footverse/features/auth/models/role.dart';
import 'package:footverse/features/auth/repositories/auth_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'auth_repository_test.mocks.dart';

Map<String, dynamic> _authData() => <String, dynamic>{
  'accessToken': 'access-jwt',
  'refreshToken': 'refresh-opaque',
  'expiresIn': 900,
  'tokenType': 'Bearer',
  'user': <String, dynamic>{
    'id': 1,
    'email': 'user@example.com',
    'fullName': 'Nguyen Van A',
    'phone': '0901234567',
    'avatarUrl': null,
    'role': 'CUSTOMER',
    'enabled': true,
    'createdAt': '2025-01-15T10:30:00',
    'updatedAt': '2025-01-15T10:30:00',
  },
};

Response<Map<String, dynamic>> _successResponse(String path) =>
    Response<Map<String, dynamic>>(
      requestOptions: RequestOptions(path: path),
      statusCode: 200,
      data: <String, dynamic>{
        'success': true,
        'message': 'OK',
        'data': _authData(),
        'timestamp': '2025-01-15T10:30:00',
      },
    );

DioException _errorWith(String errorCode, int statusCode) => DioException(
  requestOptions: RequestOptions(path: '/api/v1/auth'),
  error: AppException(
    message: 'error',
    statusCode: statusCode,
    errorCode: errorCode,
  ),
);

@GenerateNiceMocks([MockSpec<Dio>()])
void main() {
  late MockDio dio;
  late AuthRepository repository;

  setUp(() {
    dio = MockDio();
    repository = AuthRepository(dio);
  });

  group('register', () {
    const request = RegisterRequest(
      email: 'user@example.com',
      password: 'secret12',
      fullName: 'Nguyen Van A',
      phone: '0901234567',
    );

    test(
      'POSTs the register path with the request body, returns typed',
      () async {
        when(
          dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
        ).thenAnswer((_) async => _successResponse('/api/v1/auth/register'));

        final result = await repository.register(request);

        final captured = verify(
          dio.post<Map<String, dynamic>>(
            captureAny,
            data: captureAnyNamed('data'),
          ),
        ).captured;
        expect(captured[0], '/api/v1/auth/register');
        expect(captured[1], {
          'email': 'user@example.com',
          'password': 'secret12',
          'fullName': 'Nguyen Van A',
          'phone': '0901234567',
        });
        expect(result, isA<AuthResponse>());
        expect(result.accessToken, 'access-jwt');
        expect(result.user.role, Role.customer);
      },
    );

    test('surfaces USER_EMAIL_DUPLICATED as AppException', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenThrow(_errorWith('USER_EMAIL_DUPLICATED', 409));

      await expectLater(
        repository.register(request),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'USER_EMAIL_DUPLICATED',
          ),
        ),
      );
    });
  });

  group('login', () {
    const request = LoginRequest(
      email: 'user@example.com',
      password: 'secret12',
    );

    test('POSTs the login path with the request body, returns typed', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenAnswer((_) async => _successResponse('/api/v1/auth/login'));

      final result = await repository.login(request);

      final captured = verify(
        dio.post<Map<String, dynamic>>(
          captureAny,
          data: captureAnyNamed('data'),
        ),
      ).captured;
      expect(captured[0], '/api/v1/auth/login');
      expect(captured[1], {
        'email': 'user@example.com',
        'password': 'secret12',
      });
      expect(result.accessToken, 'access-jwt');
      expect(result.refreshToken, 'refresh-opaque');
      expect(result.expiresIn, 900);
    });

    test('surfaces INVALID_CREDENTIALS as AppException', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenThrow(_errorWith('INVALID_CREDENTIALS', 401));

      await expectLater(
        repository.login(request),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'INVALID_CREDENTIALS',
          ),
        ),
      );
    });
  });

  group('refresh', () {
    const request = RefreshTokenRequest(refreshToken: 'refresh-opaque');

    test(
      'POSTs the refresh path with the request body, returns typed',
      () async {
        when(
          dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
        ).thenAnswer((_) async => _successResponse('/api/v1/auth/refresh'));

        final result = await repository.refresh(request);

        final captured = verify(
          dio.post<Map<String, dynamic>>(
            captureAny,
            data: captureAnyNamed('data'),
          ),
        ).captured;
        expect(captured[0], '/api/v1/auth/refresh');
        expect(captured[1], {'refreshToken': 'refresh-opaque'});
        expect(result, isA<AuthResponse>());
      },
    );

    test('surfaces REFRESH_TOKEN_INVALID as AppException', () async {
      when(
        dio.post<Map<String, dynamic>>(any, data: anyNamed('data')),
      ).thenThrow(_errorWith('REFRESH_TOKEN_INVALID', 401));

      await expectLater(
        repository.refresh(request),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'REFRESH_TOKEN_INVALID',
          ),
        ),
      );
    });
  });

  group('logout', () {
    const request = RefreshTokenRequest(refreshToken: 'refresh-opaque');

    test('POSTs the logout path with the refresh token body', () async {
      when(dio.post<void>(any, data: anyNamed('data'))).thenAnswer(
        (_) async => Response<void>(
          requestOptions: RequestOptions(path: '/api/v1/auth/logout'),
          statusCode: 200,
        ),
      );

      await repository.logout(request);

      final captured = verify(
        dio.post<void>(captureAny, data: captureAnyNamed('data')),
      ).captured;
      expect(captured[0], '/api/v1/auth/logout');
      expect(captured[1], {'refreshToken': 'refresh-opaque'});
    });
  });
}
