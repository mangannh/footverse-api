/// Client-side field validators for the address form.
///
/// They mirror the frozen constraints of validation-spec §5 **exactly** — every
/// field `@NotBlank`, `recipientPhone` the VN `@Pattern("^0\d{9}$")` — so the
/// pre-submit check never rejects input the server would accept (assumption 5).
/// The server stays authoritative; a `400 VALIDATION_ERROR` envelope is always
/// rendered faithfully by the screens. They live in the address feature (never
/// importing another feature's validators — flutter-guidelines §Feature
/// Boundaries) and follow the same validator idiom as the Sprint 6 auth forms.
/// validation-spec defines the constraints but no user-facing strings, so the
/// messages below describe those constraints and are the single source — never
/// hardcoded inline in a widget.
class AddressValidators {
  const AddressValidators._();

  // Frozen VN phone pattern (validation-spec §1/§5): 10 digits, starts with 0.
  static final RegExp _phonePattern = RegExp(r'^0\d{9}$');

  static const String _recipientNameRequired = 'Recipient name is required';
  static const String _recipientPhoneRequired = 'Recipient phone is required';
  static const String _recipientPhoneInvalid =
      'Phone must be 10 digits and start with 0';
  static const String _provinceRequired = 'Province is required';
  static const String _districtRequired = 'District is required';
  static const String _wardRequired = 'Ward is required';
  static const String _streetAddressRequired = 'Street address is required';

  /// `@NotBlank` — required recipient name.
  static String? recipientName(String? value) =>
      _required(value, _recipientNameRequired);

  /// `@NotBlank @Pattern("^0\d{9}$")` — required VN recipient phone.
  static String? recipientPhone(String? value) {
    final input = value?.trim() ?? '';
    if (input.isEmpty) {
      return _recipientPhoneRequired;
    }
    if (!_phonePattern.hasMatch(input)) {
      return _recipientPhoneInvalid;
    }
    return null;
  }

  /// `@NotBlank` — required province.
  static String? province(String? value) => _required(value, _provinceRequired);

  /// `@NotBlank` — required district.
  static String? district(String? value) => _required(value, _districtRequired);

  /// `@NotBlank` — required ward.
  static String? ward(String? value) => _required(value, _wardRequired);

  /// `@NotBlank` — required street address.
  static String? streetAddress(String? value) =>
      _required(value, _streetAddressRequired);

  static String? _required(String? value, String message) {
    if (value == null || value.trim().isEmpty) {
      return message;
    }
    return null;
  }
}
