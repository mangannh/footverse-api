import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/storage/token_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late SharedPreferences prefs;
  late TokenStorage storage;

  setUp(() async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    prefs = await SharedPreferences.getInstance();
    storage = TokenStorage(prefs);
  });

  group('TokenStorage', () {
    test('saveTokens persists the access token', () async {
      await storage.saveTokens('access-1', 'refresh-1');

      expect(storage.readAccessToken(), 'access-1');
    });

    test('saveTokens persists the refresh token', () async {
      await storage.saveTokens('access-1', 'refresh-1');

      expect(storage.readRefreshToken(), 'refresh-1');
    });

    test('reads return null before anything is saved', () {
      expect(storage.readAccessToken(), isNull);
      expect(storage.readRefreshToken(), isNull);
    });

    test('clearTokens removes both tokens', () async {
      await storage.saveTokens('access-1', 'refresh-1');

      await storage.clearTokens();

      expect(storage.readAccessToken(), isNull);
      expect(storage.readRefreshToken(), isNull);
    });

    test('saveTokens overwrites a previously stored pair', () async {
      await storage.saveTokens('access-1', 'refresh-1');

      await storage.saveTokens('access-2', 'refresh-2');

      expect(storage.readAccessToken(), 'access-2');
      expect(storage.readRefreshToken(), 'refresh-2');
    });

    test('supports a save -> read -> clear -> read round-trip', () async {
      await storage.saveTokens('access-1', 'refresh-1');
      expect(storage.readAccessToken(), 'access-1');
      expect(storage.readRefreshToken(), 'refresh-1');

      await storage.clearTokens();
      expect(storage.readAccessToken(), isNull);
      expect(storage.readRefreshToken(), isNull);
    });

    test('stores nothing beyond the two tokens', () async {
      await storage.saveTokens('access-1', 'refresh-1');

      expect(prefs.getKeys(), hasLength(2));
    });
  });
}
