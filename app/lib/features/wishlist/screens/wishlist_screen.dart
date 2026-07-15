import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/router/app_routes.dart';
import '../models/wishlist_item_response.dart';
import '../providers/wishlist_provider.dart';

/// The caller's wishlist. It reads the application-scoped [WishlistProvider]
/// (provided at the app root), refreshes on mount, and renders the loading /
/// error / empty / ready states. Each line renders the frozen
/// [WishlistItemResponse] fields (dto-spec §13), removes through the provider,
/// and opens the product detail by `productId` via the existing named route.
/// No widget here calls Dio, and the list order is the server's — never
/// reordered on the client (business-rules → Wishlist).
class WishlistScreen extends StatefulWidget {
  const WishlistScreen({super.key});

  @override
  State<WishlistScreen> createState() => _WishlistScreenState();
}

class _WishlistScreenState extends State<WishlistScreen> {
  @override
  void initState() {
    super.initState();
    // Refresh on mount (the list may already have loaded on sign-in).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<WishlistProvider>().load();
      }
    });
  }

  Future<void> _remove(int productId) async {
    final provider = context.read<WishlistProvider>();
    try {
      await provider.remove(productId);
    } on AppException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    }
  }

  void _openProduct(int productId) {
    context.pushNamed(
      AppRoute.productDetail,
      pathParameters: <String, String>{'id': '$productId'},
    );
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<WishlistProvider>();
    return Scaffold(
      appBar: AppBar(title: const Text('Wishlist')),
      body: SafeArea(child: _buildBody(provider)),
    );
  }

  Widget _buildBody(WishlistProvider provider) {
    switch (provider.status) {
      case WishlistStatus.loading:
        return const Center(child: CircularProgressIndicator());
      case WishlistStatus.error:
        return _ErrorView(
          message: provider.error?.message ?? 'Something went wrong',
          onRetry: provider.retry,
        );
      case WishlistStatus.ready:
        if (provider.isEmpty) {
          return const _EmptyView();
        }
        final items = provider.items;
        return ListView.builder(
          padding: const EdgeInsets.symmetric(vertical: 8),
          itemCount: items.length,
          itemBuilder: (context, index) {
            final item = items[index];
            return _WishlistItemTile(
              item: item,
              enabled: !provider.isMutating,
              onOpen: () => _openProduct(item.productId),
              onRemove: () => _remove(item.productId),
            );
          },
        );
    }
  }
}

/// One wishlist line (dto-spec §13): image, name, server-owned `basePrice`,
/// availability, and a remove action. It renders only — the client computes no
/// money (dto-spec §1). [enabled] is false while a mutation is in flight so
/// removal stays single-flight.
class _WishlistItemTile extends StatelessWidget {
  const _WishlistItemTile({
    required this.item,
    required this.enabled,
    required this.onOpen,
    required this.onRemove,
  });

  final WishlistItemResponse item;
  final bool enabled;
  final VoidCallback onOpen;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: ListTile(
        onTap: onOpen,
        leading: _Thumbnail(imageUrl: item.primaryImageUrl),
        title: Text(
          item.productName,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            const SizedBox(height: 4),
            Text('${item.basePrice}', style: textTheme.bodyMedium),
            const SizedBox(height: 2),
            Text(item.available ? 'In stock' : 'Out of stock'),
          ],
        ),
        trailing: IconButton(
          onPressed: enabled ? onRemove : null,
          icon: const Icon(Icons.delete_outline),
          tooltip: 'Remove from wishlist',
        ),
      ),
    );
  }
}

/// The line thumbnail: the product image, or a placeholder when the line has no
/// image or the image fails to load.
class _Thumbnail extends StatelessWidget {
  const _Thumbnail({required this.imageUrl});

  final String? imageUrl;

  static const double _size = 56;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final placeholder = Container(
      width: _size,
      height: _size,
      color: colorScheme.surfaceContainerHighest,
      child: Icon(
        Icons.image_not_supported_outlined,
        color: colorScheme.outline,
      ),
    );

    final url = imageUrl;
    if (url == null || url.isEmpty) {
      return placeholder;
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: Image.network(
        url,
        width: _size,
        height: _size,
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) => placeholder,
        loadingBuilder: (context, child, progress) => progress == null
            ? child
            : const SizedBox(
                width: _size,
                height: _size,
                child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
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

/// The empty state shown when the caller has no wishlist items.
class _EmptyView extends StatelessWidget {
  const _EmptyView();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(24),
        child: Text('Your wishlist is empty.'),
      ),
    );
  }
}
