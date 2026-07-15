import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/router/app_routes.dart';
import '../../cart/widgets/cart_badge.dart';
import '../providers/product_list_provider.dart';
import '../repositories/brand_repository.dart';
import '../repositories/category_repository.dart';
import '../repositories/product_repository.dart';
import '../widgets/next_page_footer.dart';
import '../widgets/product_card.dart';
import '../widgets/product_filter_bar.dart';

/// The anonymous catalog — the app's landing surface (assumption 4). It owns a
/// single [ProductListProvider] built from the injected repositories and loads
/// the first page on mount; the repositories arrive from the composition root so
/// no widget constructs a `Dio` (flutter-guidelines §Networking).
class ProductListScreen extends StatelessWidget {
  const ProductListScreen({
    super.key,
    required this.productRepository,
    required this.categoryRepository,
    required this.brandRepository,
  });

  final ProductRepository productRepository;
  final CategoryRepository categoryRepository;
  final BrandRepository brandRepository;

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<ProductListProvider>(
      create: (_) => ProductListProvider(
        productRepository,
        categoryRepository,
        brandRepository,
      )..loadInitial(),
      child: const _ProductListView(),
    );
  }
}

/// Renders the catalog list and drives infinite scrolling: it loads the next
/// page when the scroll position nears the bottom (the provider guards against
/// duplicate and surplus requests).
class _ProductListView extends StatefulWidget {
  const _ProductListView();

  @override
  State<_ProductListView> createState() => _ProductListViewState();
}

class _ProductListViewState extends State<_ProductListView> {
  final ScrollController _scrollController = ScrollController();

  static const double _loadMoreThreshold = 200;

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    final position = _scrollController.position;
    if (position.pixels >= position.maxScrollExtent - _loadMoreThreshold) {
      context.read<ProductListProvider>().loadNextPage();
    }
  }

  /// Loads the next page when the current list is too short to scroll: a page
  /// smaller than the viewport would otherwise never fire [_onScroll]. Runs
  /// after layout so the scroll extent is known; the provider stops once the
  /// last page is reached.
  void _autoLoadIfNotScrollable() {
    if (!mounted || !_scrollController.hasClients) {
      return;
    }
    if (_scrollController.position.maxScrollExtent == 0) {
      context.read<ProductListProvider>().loadNextPage();
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ProductListProvider>();
    if (provider.status == ProductListStatus.ready && !provider.isEmpty) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _autoLoadIfNotScrollable(),
      );
    }
    return Scaffold(
      appBar: AppBar(
        title: const Text('FootVerse'),
        actions: <Widget>[
          const CartBadge(),
          IconButton(
            icon: const Icon(Icons.person_outline),
            tooltip: 'Account',
            onPressed: () => context.goNamed(AppRoute.account),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: <Widget>[
            ProductFilterBar(
              searchText: provider.searchText,
              brands: provider.brands,
              categories: provider.categories,
              brandId: provider.brandId,
              categoryId: provider.categoryId,
              sort: provider.sort,
              onSearch: context.read<ProductListProvider>().setSearchText,
              onBrandChanged: context.read<ProductListProvider>().setBrand,
              onCategoryChanged: context
                  .read<ProductListProvider>()
                  .setCategory,
              onSortChanged: context.read<ProductListProvider>().setSort,
            ),
            Expanded(child: _buildBody(context, provider)),
          ],
        ),
      ),
    );
  }

  Widget _buildBody(BuildContext context, ProductListProvider provider) {
    switch (provider.status) {
      case ProductListStatus.loading:
        return const Center(child: CircularProgressIndicator());
      case ProductListStatus.error:
        return _ErrorView(
          message: provider.error?.message ?? 'Something went wrong',
          onRetry: context.read<ProductListProvider>().retry,
        );
      case ProductListStatus.ready:
        if (provider.isEmpty) {
          return const _EmptyView();
        }
        return _buildList(context, provider);
    }
  }

  Widget _buildList(BuildContext context, ProductListProvider provider) {
    final products = provider.products;
    final hasFooter =
        provider.loadingNextPage || provider.nextPageError != null;
    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.only(top: 12, bottom: 12),
      itemCount: products.length + (hasFooter ? 1 : 0),
      itemBuilder: (context, index) {
        if (index >= products.length) {
          return NextPageFooter(
            loading: provider.loadingNextPage,
            error: provider.nextPageError?.message,
            onRetry: context.read<ProductListProvider>().loadNextPage,
          );
        }
        final product = products[index];
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          child: ProductCard(
            product: product,
            onTap: () => context.goNamed(
              AppRoute.productDetail,
              pathParameters: <String, String>{'id': '${product.id}'},
            ),
          ),
        );
      },
    );
  }
}

/// The full-screen error state with a retry affordance
/// (flutter-guidelines §Error Handling).
class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

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

/// The empty state shown when the search / filters match no products.
class _EmptyView extends StatelessWidget {
  const _EmptyView();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Padding(
        padding: EdgeInsets.all(24),
        child: Text('No products found.'),
      ),
    );
  }
}
