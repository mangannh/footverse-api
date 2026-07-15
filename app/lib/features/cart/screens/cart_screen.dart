import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/error/app_exception.dart';
import '../models/cart_response.dart';
import '../providers/cart_provider.dart';
import '../widgets/cart_line_tile.dart';

/// The customer's cart — the first screen whose money the server computes and the
/// client only renders. It reads the application-scoped [CartProvider] (provided
/// at the app root), refreshes on mount, and renders the loading / error / empty
/// / ready states. Mutations run through the provider and any enveloped error
/// (e.g. `PRODUCT_VARIANT_INSUFFICIENT_STOCK`) is shown via a transient
/// `SnackBar` (flutter-guidelines §Error Handling). No widget here calls Dio and
/// no money or aggregate is computed on the client (dto-spec §1).
class CartScreen extends StatefulWidget {
  const CartScreen({super.key});

  @override
  State<CartScreen> createState() => _CartScreenState();
}

class _CartScreenState extends State<CartScreen> {
  @override
  void initState() {
    super.initState();
    // Refresh on mount (the badge may already have loaded the cart on sign-in).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<CartProvider>().load();
      }
    });
  }

  /// Runs a provider mutation and renders any [AppException] as a `SnackBar`.
  Future<void> _runMutation(Future<void> Function() action) async {
    try {
      await action();
    } on AppException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<CartProvider>();
    return Scaffold(
      appBar: AppBar(title: const Text('Cart')),
      body: SafeArea(child: _buildBody(provider)),
    );
  }

  Widget _buildBody(CartProvider provider) {
    switch (provider.status) {
      case CartStatus.loading:
        return const Center(child: CircularProgressIndicator());
      case CartStatus.error:
        return _ErrorView(
          message: provider.error?.message ?? 'Something went wrong',
          onRetry: provider.retry,
        );
      case CartStatus.ready:
        final cart = provider.cart;
        if (cart == null || cart.items.isEmpty) {
          return const _EmptyView();
        }
        return _buildCart(provider, cart);
    }
  }

  Widget _buildCart(CartProvider provider, CartResponse cart) {
    final items = cart.items;
    return Column(
      children: <Widget>[
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.symmetric(vertical: 8),
            itemCount: items.length,
            itemBuilder: (context, index) {
              final item = items[index];
              return CartLineTile(
                item: item,
                enabled: !provider.isMutating,
                onIncrement: () => _runMutation(
                  () => provider.updateQuantity(item.id, item.quantity + 1),
                ),
                onDecrement: item.quantity > 1
                    ? () => _runMutation(
                        () =>
                            provider.updateQuantity(item.id, item.quantity - 1),
                      )
                    : null,
                onRemove: () =>
                    _runMutation(() => provider.removeItem(item.id)),
              );
            },
          ),
        ),
        _CartSummary(subtotal: cart.subtotal, itemCount: cart.itemCount),
      ],
    );
  }
}

/// The cart totals bar: the server's `subtotal` and `itemCount`, rendered exactly
/// as delivered (dto-spec §12 — the client never recomputes them).
class _CartSummary extends StatelessWidget {
  const _CartSummary({required this.subtotal, required this.itemCount});

  final double subtotal;
  final int itemCount;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Material(
      elevation: 3,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: SafeArea(
          top: false,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: <Widget>[
              Text('Subtotal ($itemCount)', style: textTheme.titleMedium),
              Text('$subtotal', style: textTheme.titleLarge),
            ],
          ),
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

/// The empty state shown when the caller's cart has no lines.
class _EmptyView extends StatelessWidget {
  const _EmptyView();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(24),
        child: Text('Your cart is empty.'),
      ),
    );
  }
}
