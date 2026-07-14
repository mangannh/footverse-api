import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/product_detail_response.dart';
import '../models/product_image_response.dart';
import '../providers/product_detail_provider.dart';
import '../repositories/product_repository.dart';
import '../widgets/next_page_footer.dart';
import '../widgets/product_image_gallery.dart';
import '../widgets/product_variant_list.dart';
import '../widgets/review_tile.dart';

/// The read-only product detail (sprint-6-plan item 11): images, the §9 detail
/// fields, the read-only variants, the server's live rating / review count, and
/// the paginated public reviews. It owns a single [ProductDetailProvider] built
/// from the injected repository and loads on mount; every call it makes is a
/// public `GET` — nothing is mutated.
class ProductDetailScreen extends StatelessWidget {
  const ProductDetailScreen({
    super.key,
    required this.productId,
    required this.productRepository,
  });

  final int productId;
  final ProductRepository productRepository;

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<ProductDetailProvider>(
      create: (_) =>
          ProductDetailProvider(productRepository, productId)..load(),
      child: const _ProductDetailView(),
    );
  }
}

/// Renders the detail and drives the review list's infinite scrolling: it loads
/// the next review page when the scroll position nears the bottom, and also when
/// a short first page cannot scroll (the provider guards duplicate / surplus
/// requests and stops at the last page).
class _ProductDetailView extends StatefulWidget {
  const _ProductDetailView();

  @override
  State<_ProductDetailView> createState() => _ProductDetailViewState();
}

class _ProductDetailViewState extends State<_ProductDetailView> {
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
      context.read<ProductDetailProvider>().loadNextReviews();
    }
  }

  void _autoLoadIfNotScrollable() {
    if (!mounted || !_scrollController.hasClients) {
      return;
    }
    if (_scrollController.position.maxScrollExtent == 0) {
      context.read<ProductDetailProvider>().loadNextReviews();
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<ProductDetailProvider>();
    if (provider.status == ProductDetailStatus.ready &&
        provider.reviewsStatus == ReviewsStatus.ready &&
        !provider.isReviewsEmpty) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _autoLoadIfNotScrollable(),
      );
    }
    return Scaffold(
      appBar: AppBar(title: Text(provider.detail?.name ?? 'Product')),
      body: SafeArea(child: _buildBody(context, provider)),
    );
  }

  Widget _buildBody(BuildContext context, ProductDetailProvider provider) {
    switch (provider.status) {
      case ProductDetailStatus.loading:
        return const Center(child: CircularProgressIndicator());
      case ProductDetailStatus.error:
        if (provider.isNotFound) {
          return _MessageView(
            message: provider.error?.message ?? 'Product not found',
          );
        }
        return _MessageView(
          message: provider.error?.message ?? 'Something went wrong',
          onRetry: context.read<ProductDetailProvider>().retry,
        );
      case ProductDetailStatus.ready:
        return CustomScrollView(
          controller: _scrollController,
          slivers: <Widget>[
            SliverToBoxAdapter(
              child: _DetailBody(
                detail: provider.detail!,
                images: provider.images,
              ),
            ),
            ..._reviewSlivers(context, provider),
          ],
        );
    }
  }

  List<Widget> _reviewSlivers(
    BuildContext context,
    ProductDetailProvider provider,
  ) {
    switch (provider.reviewsStatus) {
      case ReviewsStatus.loading:
        return const <Widget>[
          SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            ),
          ),
        ];
      case ReviewsStatus.error:
        return <Widget>[
          SliverToBoxAdapter(
            child: NextPageFooter(
              loading: false,
              error: provider.reviewsError?.message ?? 'Could not load reviews',
              onRetry: context.read<ProductDetailProvider>().retryReviews,
            ),
          ),
        ];
      case ReviewsStatus.ready:
        if (provider.isReviewsEmpty) {
          return const <Widget>[
            SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Text('No reviews yet.'),
              ),
            ),
          ];
        }
        final reviews = provider.reviews;
        final hasFooter =
            provider.loadingNextReviews || provider.nextReviewsError != null;
        return <Widget>[
          SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) => ReviewTile(review: reviews[index]),
              childCount: reviews.length,
            ),
          ),
          if (hasFooter)
            SliverToBoxAdapter(
              child: NextPageFooter(
                loading: provider.loadingNextReviews,
                error: provider.nextReviewsError?.message,
                onRetry: context.read<ProductDetailProvider>().loadNextReviews,
              ),
            ),
        ];
    }
  }
}

/// The product's own information plus its read-only variants and the reviews
/// header — everything above the review list.
class _DetailBody extends StatelessWidget {
  const _DetailBody({required this.detail, required this.images});

  final ProductDetailResponse detail;
  final List<ProductImageResponse> images;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final description = detail.description;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        const SizedBox(height: 12),
        ProductImageGallery(images: images),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(detail.name, style: theme.textTheme.headlineSmall),
              const SizedBox(height: 4),
              Text(
                '${detail.brandName} · ${detail.categoryName}',
                style: theme.textTheme.bodyMedium,
              ),
              const SizedBox(height: 8),
              Text('${detail.basePrice}', style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Row(
                children: <Widget>[
                  Icon(Icons.star, size: 18, color: theme.colorScheme.primary),
                  const SizedBox(width: 4),
                  Text('${detail.averageRating}'),
                  const SizedBox(width: 4),
                  Text(
                    '(${detail.reviewCount})',
                    style: theme.textTheme.bodySmall,
                  ),
                  const SizedBox(width: 16),
                  Text(detail.available ? 'In stock' : 'Out of stock'),
                ],
              ),
              if (description != null && description.isNotEmpty) ...<Widget>[
                const SizedBox(height: 16),
                Text(description, style: theme.textTheme.bodyMedium),
              ],
              const SizedBox(height: 16),
              Text('Variants', style: theme.textTheme.titleMedium),
            ],
          ),
        ),
        const SizedBox(height: 8),
        ProductVariantList(variants: detail.variants),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text('Reviews', style: theme.textTheme.titleMedium),
        ),
      ],
    );
  }
}

/// A centered message state, optionally with a retry affordance. Used for both
/// the generic error (with retry) and the not-found state (no retry — retrying a
/// missing product cannot help; the back button returns to the list).
class _MessageView extends StatelessWidget {
  const _MessageView({required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final onRetry = this.onRetry;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(message, textAlign: TextAlign.center),
            if (onRetry != null) ...<Widget>[
              const SizedBox(height: 16),
              FilledButton(onPressed: onRetry, child: const Text('Retry')),
            ],
          ],
        ),
      ),
    );
  }
}
