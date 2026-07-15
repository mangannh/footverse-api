import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../models/address_request.dart';
import '../models/address_response.dart';
import '../validators/address_validators.dart';

/// The single form that serves both create and edit (the write body is
/// field-identical — dto-spec §8). It is a self-contained input collector: it
/// pre-validates the fields against the frozen constraints (validation-spec §5,
/// via [AddressValidators]) and returns the assembled [AddressRequest] to the
/// address list screen through `context.pop`, which owns the mutation and its
/// error rendering. The form itself performs no networking and touches no
/// provider (flutter-guidelines §Widget Purity / §State Ownership).
///
/// [address] is null for create and the edited row for edit; `isDefault` is
/// submitted exactly as entered — the server owns the one-default invariant, so
/// the client never computes it (business-rules → Shipping Address).
class AddressFormScreen extends StatefulWidget {
  const AddressFormScreen({super.key, this.address});

  final AddressResponse? address;

  @override
  State<AddressFormScreen> createState() => _AddressFormScreenState();
}

class _AddressFormScreenState extends State<AddressFormScreen> {
  final GlobalKey<FormState> _formKey = GlobalKey<FormState>();
  late final TextEditingController _recipientNameController;
  late final TextEditingController _recipientPhoneController;
  late final TextEditingController _provinceController;
  late final TextEditingController _districtController;
  late final TextEditingController _wardController;
  late final TextEditingController _streetAddressController;
  late bool _isDefault;

  bool get _isEditing => widget.address != null;

  @override
  void initState() {
    super.initState();
    final address = widget.address;
    _recipientNameController = TextEditingController(
      text: address?.recipientName,
    );
    _recipientPhoneController = TextEditingController(
      text: address?.recipientPhone,
    );
    _provinceController = TextEditingController(text: address?.province);
    _districtController = TextEditingController(text: address?.district);
    _wardController = TextEditingController(text: address?.ward);
    _streetAddressController = TextEditingController(
      text: address?.streetAddress,
    );
    _isDefault = address?.isDefault ?? false;
  }

  @override
  void dispose() {
    _recipientNameController.dispose();
    _recipientPhoneController.dispose();
    _provinceController.dispose();
    _districtController.dispose();
    _wardController.dispose();
    _streetAddressController.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    context.pop(
      AddressRequest(
        recipientName: _recipientNameController.text.trim(),
        recipientPhone: _recipientPhoneController.text.trim(),
        province: _provinceController.text.trim(),
        district: _districtController.text.trim(),
        ward: _wardController.text.trim(),
        streetAddress: _streetAddressController.text.trim(),
        isDefault: _isDefault,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_isEditing ? 'Edit address' : 'Add address')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                TextFormField(
                  controller: _recipientNameController,
                  decoration: const InputDecoration(
                    labelText: 'Recipient name',
                  ),
                  textInputAction: TextInputAction.next,
                  autofillHints: const <String>[AutofillHints.name],
                  validator: AddressValidators.recipientName,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _recipientPhoneController,
                  decoration: const InputDecoration(
                    labelText: 'Recipient phone',
                  ),
                  keyboardType: TextInputType.phone,
                  textInputAction: TextInputAction.next,
                  autofillHints: const <String>[AutofillHints.telephoneNumber],
                  validator: AddressValidators.recipientPhone,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _provinceController,
                  decoration: const InputDecoration(labelText: 'Province'),
                  textInputAction: TextInputAction.next,
                  validator: AddressValidators.province,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _districtController,
                  decoration: const InputDecoration(labelText: 'District'),
                  textInputAction: TextInputAction.next,
                  validator: AddressValidators.district,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _wardController,
                  decoration: const InputDecoration(labelText: 'Ward'),
                  textInputAction: TextInputAction.next,
                  validator: AddressValidators.ward,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _streetAddressController,
                  decoration: const InputDecoration(
                    labelText: 'Street address',
                  ),
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  validator: AddressValidators.streetAddress,
                ),
                const SizedBox(height: 16),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Set as default address'),
                  value: _isDefault,
                  onChanged: (value) => setState(() => _isDefault = value),
                ),
                const SizedBox(height: 8),
                FilledButton(
                  onPressed: _submit,
                  child: Text(_isEditing ? 'Save changes' : 'Add address'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
