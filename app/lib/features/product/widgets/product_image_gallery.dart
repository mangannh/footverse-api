import 'package:flutter/material.dart';

import '../models/product_image_response.dart';

/// A horizontal gallery of the product's images. The caller passes them already
/// ordered by `displayOrder` (dto-spec §9); a product with no image shows a
/// placeholder.
class ProductImageGallery extends StatelessWidget {
  const ProductImageGallery({super.key, required this.images});

  final List<ProductImageResponse> images;

  static const double _height = 220;

  @override
  Widget build(BuildContext context) {
    if (images.isEmpty) {
      return _GalleryImage.placeholder(context, width: double.infinity);
    }
    return SizedBox(
      height: _height,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        itemCount: images.length,
        separatorBuilder: (context, index) => const SizedBox(width: 8),
        itemBuilder: (context, index) =>
            _GalleryImage(url: images[index].imageUrl),
      ),
    );
  }
}

/// One gallery image with loading and error fallbacks.
class _GalleryImage extends StatelessWidget {
  const _GalleryImage({required this.url});

  final String url;

  static const double _width = 300;

  static Widget placeholder(BuildContext context, {double width = _width}) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      width: width,
      height: ProductImageGallery._height,
      color: colorScheme.surfaceContainerHighest,
      child: Icon(
        Icons.image_not_supported_outlined,
        color: colorScheme.outline,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Image.network(
        url,
        width: _width,
        height: ProductImageGallery._height,
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) =>
            placeholder(context, width: _width),
        loadingBuilder: (context, child, progress) => progress == null
            ? child
            : const SizedBox(
                width: _width,
                height: ProductImageGallery._height,
                child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
              ),
      ),
    );
  }
}
