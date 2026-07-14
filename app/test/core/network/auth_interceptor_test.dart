import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/network/auth_interceptor.dart';
import 'package:footverse/core/storage/token_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/dio_test_helpers.dart';

const String _businessPath = '/api/v1/cart';
const String _loginPath = '/api/v1/auth/login';

Map<String, dynamic> _envelope(Map<String, dynamic> data) => <String, dynamic>{
  'success': true,
  'message': 'OK',
  'data': data,
  'timestamp': '2025-01-15T10:30:00',
};

Map<String, dynamic> _errorEnvelope(String errorCode) => <String, dynamic>{
  'success': false,
  'message': 'Unauthorized',
  'errorCode': errorCode,
  'timestamp': '2025-01-15T10:30:00',
};

Map<String, dynamic> _rotatedTokens() => <String, dynamic>{
  'accessToken': 'new-access',
  'refreshToken': 'new-refresh',
};

void main() {
  late TokenStorage tokenStorage;
  late Dio dio;
  late Dio refreshDio;
  var sessionExpired = false;

  setUp(() async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    tokenStorage = TokenStorage(await SharedPreferences.getInstance());
    sessionExpired = false;

    dio = Dio(BaseOptions(baseUrl: 'https://test'));
    refreshDio = Dio(BaseOptions(baseUrl: 'https://test'));
    dio.interceptors.add(
      AuthInterceptor(
        dio: dio,
        refreshDio: refreshDio,
        tokenStorage: tokenStorage,
        onSessionExpired: () => sessionExpired = true,
      ),
    );
  });

  test('attaches the stored bearer token to outgoing requests', () async {
    await tokenStorage.saveTokens('old-access', 'old-refresh');
    String? sentAuthorization;
    dio.httpClientAdapter = FakeHttpClientAdapter((options) async {
      sentAuthorization = options.headers['Authorization'] as String?;
      return jsonResponseBody(200, _envelope(<String, dynamic>{}));
    });

    await dio.get<dynamic>(_businessPath);

    expect(sentAuthorization, 'Bearer old-access');
  });

  test('a 401 triggers exactly one refresh, then a successful retry', () async {
    await tokenStorage.saveTokens('old-access', 'old-refresh');
    var businessCalls = 0;
    var refreshCalls = 0;
    dio.httpClientAdapter = FakeHttpClientAdapter((options) async {
      businessCalls++;
      final authorized =
          options.headers['Authorization'] == 'Bearer new-access';
      return authorized
          ? jsonResponseBody(200, _envelope(<String, dynamic>{'ok': true}))
          : jsonResponseBody(401, _errorEnvelope('UNAUTHORIZED'));
    });
    refreshDio.httpClientAdapter = FakeHttpClientAdapter((options) async {
      refreshCalls++;
      return jsonResponseBody(200, _envelope(_rotatedTokens()));
    });

    final response = await dio.get<dynamic>(_businessPath);

    expect(response.statusCode, 200);
    expect(refreshCalls, 1);
    expect(businessCalls, 2); // initial 401 + retry 200
    expect(tokenStorage.readAccessToken(), 'new-access');
    expect(tokenStorage.readRefreshToken(), 'new-refresh');
  });

  test('never refreshes for auth endpoints themselves', () async {
    await tokenStorage.saveTokens('old-access', 'old-refresh');
    var refreshCalls = 0;
    dio.httpClientAdapter = FakeHttpClientAdapter(
      (options) async =>
          jsonResponseBody(401, _errorEnvelope('INVALID_CREDENTIALS')),
    );
    refreshDio.httpClientAdapter = FakeHttpClientAdapter((options) async {
      refreshCalls++;
      return jsonResponseBody(200, _envelope(_rotatedTokens()));
    });

    await expectLater(
      dio.post<dynamic>(_loginPath),
      throwsA(isA<DioException>()),
    );
    expect(refreshCalls, 0);
  });

  test('concurrent 401s share a single refresh, then all retry once', () async {
    await tokenStorage.saveTokens('old-access', 'old-refresh');
    var businessCalls = 0;
    var refreshCalls = 0;
    dio.httpClientAdapter = FakeHttpClientAdapter((options) async {
      businessCalls++;
      final authorized =
          options.headers['Authorization'] == 'Bearer new-access';
      return authorized
          ? jsonResponseBody(200, _envelope(<String, dynamic>{'ok': true}))
          : jsonResponseBody(401, _errorEnvelope('UNAUTHORIZED'));
    });
    refreshDio.httpClientAdapter = FakeHttpClientAdapter((options) async {
      refreshCalls++;
      await Future<void>.delayed(const Duration(milliseconds: 50));
      return jsonResponseBody(200, _envelope(_rotatedTokens()));
    });

    final responses = await Future.wait(<Future<Response<dynamic>>>[
      dio.get<dynamic>(_businessPath),
      dio.get<dynamic>(_businessPath),
      dio.get<dynamic>(_businessPath),
    ]);

    expect(responses.every((r) => r.statusCode == 200), isTrue);
    expect(refreshCalls, 1); // single-flight
    expect(businessCalls, 6); // 3 initial 401 + 3 retried 200
    expect(tokenStorage.readAccessToken(), 'new-access');
  });

  test(
    'a failed refresh clears tokens, signals sign-out, and does not retry',
    () async {
      await tokenStorage.saveTokens('old-access', 'old-refresh');
      var businessCalls = 0;
      dio.httpClientAdapter = FakeHttpClientAdapter((options) async {
        businessCalls++;
        return jsonResponseBody(401, _errorEnvelope('UNAUTHORIZED'));
      });
      refreshDio.httpClientAdapter = FakeHttpClientAdapter(
        (options) async =>
            jsonResponseBody(401, _errorEnvelope('REFRESH_TOKEN_INVALID')),
      );

      await expectLater(
        dio.get<dynamic>(_businessPath),
        throwsA(isA<DioException>()),
      );
      expect(sessionExpired, isTrue);
      expect(tokenStorage.readAccessToken(), isNull);
      expect(tokenStorage.readRefreshToken(), isNull);
      expect(businessCalls, 1); // no retry after a failed refresh
    },
  );

  test('a still-401 retried request is terminal — no second refresh', () async {
    await tokenStorage.saveTokens('old-access', 'old-refresh');
    var businessCalls = 0;
    var refreshCalls = 0;
    dio.httpClientAdapter = FakeHttpClientAdapter((options) async {
      businessCalls++;
      return jsonResponseBody(401, _errorEnvelope('UNAUTHORIZED'));
    });
    refreshDio.httpClientAdapter = FakeHttpClientAdapter((options) async {
      refreshCalls++;
      return jsonResponseBody(200, _envelope(_rotatedTokens()));
    });

    await expectLater(
      dio.get<dynamic>(_businessPath),
      throwsA(isA<DioException>()),
    );
    expect(refreshCalls, 1); // refreshed once
    expect(businessCalls, 2); // initial + one retry, then terminal
  });
}
