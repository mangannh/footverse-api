import 'package:dio/dio.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/models/api_response.dart';
import '../models/address_request.dart';
import '../models/address_response.dart';

/// The typed client of the four frozen address endpoints (dto-spec §20).
///
/// It only calls the API, unwraps the [ApiResponse] envelope, and returns the
/// typed payload — throwing [AppException] on failure (the injected [Dio]'s
/// `ErrorInterceptor` has already mapped the transport error, and its
/// `AuthInterceptor` has attached the CUSTOMER bearer). It holds no business
/// logic and touches no storage or navigation.
class AddressRepository {
  const AddressRepository(this._dio);

  static const String _addressesPath = '/api/v1/addresses';

  final Dio _dio;

  /// `GET /addresses` — the caller's own addresses.
  Future<List<AddressResponse>> getAddresses() async {
    try {
      final response = await _dio.get<Map<String, dynamic>>(_addressesPath);
      final envelope = ApiResponse<List<AddressResponse>>.fromJson(
        response.data!,
        (json) => (json! as List<dynamic>)
            .map(
              (item) => AddressResponse.fromJson(item as Map<String, dynamic>),
            )
            .toList(),
      );
      return envelope.data!;
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `POST /addresses` — create an address for the caller.
  Future<AddressResponse> createAddress(AddressRequest request) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        _addressesPath,
        data: request.toJson(),
      );
      return _unwrapAddress(response);
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `PUT /addresses/{id}` — update one of the caller's addresses.
  Future<AddressResponse> updateAddress(int id, AddressRequest request) async {
    try {
      final response = await _dio.put<Map<String, dynamic>>(
        '$_addressesPath/$id',
        data: request.toJson(),
      );
      return _unwrapAddress(response);
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  /// `DELETE /addresses/{id}` — delete one of the caller's addresses.
  Future<void> deleteAddress(int id) async {
    try {
      await _dio.delete<void>('$_addressesPath/$id');
    } on DioException catch (exception) {
      throw _asAppException(exception);
    }
  }

  AddressResponse _unwrapAddress(Response<Map<String, dynamic>> response) {
    final envelope = ApiResponse<AddressResponse>.fromJson(
      response.data!,
      (json) => AddressResponse.fromJson(json! as Map<String, dynamic>),
    );
    return envelope.data!;
  }

  AppException _asAppException(DioException exception) {
    final error = exception.error;
    if (error is AppException) {
      return error;
    }
    return const AppException(message: 'An unexpected error occurred');
  }
}
