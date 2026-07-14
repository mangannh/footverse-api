import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/features/auth/models/auth_response.dart';
import 'package:footverse/features/auth/models/login_request.dart';
import 'package:footverse/features/auth/models/refresh_token_request.dart';
import 'package:footverse/features/auth/models/register_request.dart';
import 'package:footverse/features/auth/models/role.dart';
import 'package:footverse/features/auth/models/user_response.dart';

Map<String, dynamic> _userJson() => <String, dynamic>{
  'id': 1,
  'email': 'user@example.com',
  'fullName': 'Nguyen Van A',
  'phone': '0901234567',
  'avatarUrl': null,
  'role': 'CUSTOMER',
  'enabled': true,
  'createdAt': '2025-01-15T10:30:00',
  'updatedAt': '2025-01-15T10:30:00',
};

Map<String, dynamic> _authJson() => <String, dynamic>{
  'accessToken': 'access-jwt',
  'refreshToken': 'refresh-opaque',
  'expiresIn': 900,
  'tokenType': 'Bearer',
  'user': _userJson(),
};

void main() {
  group('Auth request models serialize field-for-field (dto-spec §6)', () {
    test('RegisterRequest', () {
      const request = RegisterRequest(
        email: 'user@example.com',
        password: 'secret12',
        fullName: 'Nguyen Van A',
        phone: '0901234567',
      );

      expect(request.toJson(), {
        'email': 'user@example.com',
        'password': 'secret12',
        'fullName': 'Nguyen Van A',
        'phone': '0901234567',
      });
    });

    test('LoginRequest', () {
      const request = LoginRequest(
        email: 'user@example.com',
        password: 'secret12',
      );

      expect(request.toJson(), {
        'email': 'user@example.com',
        'password': 'secret12',
      });
    });

    test('RefreshTokenRequest', () {
      const request = RefreshTokenRequest(refreshToken: 'refresh-opaque');

      expect(request.toJson(), {'refreshToken': 'refresh-opaque'});
    });
  });

  group('Auth response models parse field-for-field (dto-spec §6/§7)', () {
    test('UserResponse maps every field, including the role enum', () {
      final user = UserResponse.fromJson(_userJson());

      expect(user.id, 1);
      expect(user.email, 'user@example.com');
      expect(user.fullName, 'Nguyen Van A');
      expect(user.phone, '0901234567');
      expect(user.avatarUrl, isNull);
      expect(user.role, Role.customer);
      expect(user.enabled, isTrue);
      expect(user.createdAt, DateTime.parse('2025-01-15T10:30:00'));
      expect(user.updatedAt, DateTime.parse('2025-01-15T10:30:00'));
    });

    test('AuthResponse maps the token pair and nested user', () {
      final auth = AuthResponse.fromJson(_authJson());

      expect(auth.accessToken, 'access-jwt');
      expect(auth.refreshToken, 'refresh-opaque');
      expect(auth.expiresIn, 900);
      expect(auth.tokenType, 'Bearer');
      expect(auth.user, isA<UserResponse>());
      expect(auth.user.role, Role.customer);
    });

    test('UserResponse maps the ADMIN role', () {
      final user = UserResponse.fromJson(_userJson()..['role'] = 'ADMIN');

      expect(user.role, Role.admin);
    });
  });
}
