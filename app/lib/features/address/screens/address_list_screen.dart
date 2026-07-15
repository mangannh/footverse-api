import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/router/app_routes.dart';
import '../models/address_request.dart';
import '../models/address_response.dart';
import '../providers/address_provider.dart';
import '../repositories/address_repository.dart';

/// The customer's address book — the client of the server-enforced one-default
/// invariant. It owns a single [AddressProvider] built from the injected
/// [AddressRepository] and loads the list on mount; the repository arrives from
/// the composition root so no widget constructs a `Dio`
/// (flutter-guidelines §Networking). The provider is screen-scoped: it lives and
/// dies with this route (sprint-7-plan item 05).
class AddressListScreen extends StatelessWidget {
  const AddressListScreen({super.key, required this.addressRepository});

  final AddressRepository addressRepository;

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<AddressProvider>(
      create: (_) => AddressProvider(addressRepository)..load(),
      child: const _AddressListView(),
    );
  }
}

/// Renders the address list and drives the add / edit / delete affordances. The
/// form is a routed sub-page that returns an [AddressRequest]; this view runs the
/// mutation through the provider (which reloads the server-owned list) and
/// renders any enveloped error via a transient `SnackBar`
/// (flutter-guidelines §Error Handling).
class _AddressListView extends StatefulWidget {
  const _AddressListView();

  @override
  State<_AddressListView> createState() => _AddressListViewState();
}

class _AddressListViewState extends State<_AddressListView> {
  // Guards against a second mutation while one is in flight (a double-tap on
  // delete or add would otherwise issue two calls).
  bool _busy = false;

  Future<AddressRequest?> _openForm({AddressResponse? address}) {
    return context.pushNamed<AddressRequest>(
      AppRoute.addressForm,
      extra: address,
    );
  }

  Future<void> _add() async {
    final request = await _openForm();
    // The form is a routed sub-page: guard against this view being disposed
    // while it was open before touching context in _mutate.
    if (!mounted || request == null) {
      return;
    }
    await _mutate((provider) => provider.createAddress(request));
  }

  Future<void> _edit(AddressResponse address) async {
    final request = await _openForm(address: address);
    if (!mounted || request == null) {
      return;
    }
    await _mutate((provider) => provider.updateAddress(address.id, request));
  }

  Future<void> _delete(AddressResponse address) async {
    await _mutate((provider) => provider.deleteAddress(address.id));
  }

  /// Runs a provider mutation with a single-flight guard, rendering any
  /// [AppException] (e.g. `409 ADDRESS_DEFAULT_NOT_DELETABLE`) as a `SnackBar`.
  Future<void> _mutate(Future<void> Function(AddressProvider) action) async {
    if (_busy) {
      return;
    }
    final provider = context.read<AddressProvider>();
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _busy = true);
    try {
      await action(provider);
    } on AppException catch (error) {
      messenger.showSnackBar(SnackBar(content: Text(error.message)));
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AddressProvider>();
    return Scaffold(
      appBar: AppBar(title: const Text('Addresses')),
      floatingActionButton: FloatingActionButton(
        onPressed: _busy ? null : _add,
        tooltip: 'Add address',
        child: const Icon(Icons.add),
      ),
      body: SafeArea(child: _buildBody(provider)),
    );
  }

  Widget _buildBody(AddressProvider provider) {
    switch (provider.status) {
      case AddressListStatus.loading:
        return const Center(child: CircularProgressIndicator());
      case AddressListStatus.error:
        return _ErrorView(
          message: provider.error?.message ?? 'Something went wrong',
          onRetry: provider.retry,
        );
      case AddressListStatus.ready:
        if (provider.isEmpty) {
          return const _EmptyView();
        }
        return _buildList(provider);
    }
  }

  Widget _buildList(AddressProvider provider) {
    final addresses = provider.addresses;
    return ListView.builder(
      padding: const EdgeInsets.only(top: 8, bottom: 88),
      itemCount: addresses.length,
      itemBuilder: (context, index) {
        final address = addresses[index];
        return _AddressCard(
          address: address,
          onEdit: _busy ? null : () => _edit(address),
          onDelete: _busy ? null : () => _delete(address),
        );
      },
    );
  }
}

/// One address row: recipient, full address, the server's default marker, and
/// the edit / delete affordances.
class _AddressCard extends StatelessWidget {
  const _AddressCard({
    required this.address,
    required this.onEdit,
    required this.onDelete,
  });

  final AddressResponse address;
  final VoidCallback? onEdit;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Expanded(
                  child: Text(
                    address.recipientName,
                    style: textTheme.titleMedium,
                  ),
                ),
                if (address.isDefault)
                  const Padding(
                    padding: EdgeInsets.only(left: 8),
                    child: Chip(
                      avatar: Icon(Icons.check_circle, size: 18),
                      label: Text('Default'),
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 4),
            Text(address.recipientPhone, style: textTheme.bodyMedium),
            const SizedBox(height: 4),
            Text(
              '${address.streetAddress}, ${address.ward}, '
              '${address.district}, ${address.province}',
              style: textTheme.bodyMedium,
            ),
            const SizedBox(height: 4),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: <Widget>[
                TextButton.icon(
                  onPressed: onEdit,
                  icon: const Icon(Icons.edit_outlined),
                  label: const Text('Edit'),
                ),
                TextButton.icon(
                  onPressed: onDelete,
                  icon: const Icon(Icons.delete_outline),
                  label: const Text('Delete'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// The full-screen error state with a retry affordance
/// (flutter-guidelines §Error Handling).
class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}

/// The empty state shown when the caller has no addresses yet.
class _EmptyView extends StatelessWidget {
  const _EmptyView();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(24),
        child: Text('You have no saved addresses yet.'),
      ),
    );
  }
}
