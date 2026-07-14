import 'package:flutter/material.dart';

import '../models/brand_response.dart';
import '../models/category_response.dart';
import '../repositories/product_repository.dart';

/// The catalog's search, brand / category filter, and sort controls. It renders
/// the brand and category options from the repositories (never a hardcoded
/// list) and offers only the whitelisted sorts (validation-spec §6). It owns no
/// list state; every change is reported through its callbacks to the provider.
class ProductFilterBar extends StatefulWidget {
  const ProductFilterBar({
    super.key,
    required this.searchText,
    required this.brands,
    required this.categories,
    required this.brandId,
    required this.categoryId,
    required this.sort,
    required this.onSearch,
    required this.onBrandChanged,
    required this.onCategoryChanged,
    required this.onSortChanged,
  });

  final String searchText;
  final List<BrandResponse> brands;
  final List<CategoryResponse> categories;
  final int? brandId;
  final int? categoryId;
  final ProductSort? sort;
  final ValueChanged<String> onSearch;
  final ValueChanged<int?> onBrandChanged;
  final ValueChanged<int?> onCategoryChanged;
  final ValueChanged<ProductSort?> onSortChanged;

  @override
  State<ProductFilterBar> createState() => _ProductFilterBarState();
}

class _ProductFilterBarState extends State<ProductFilterBar> {
  late final TextEditingController _searchController = TextEditingController(
    text: widget.searchText,
  );

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  static String _sortLabel(ProductSort sort) {
    switch (sort) {
      case ProductSort.createdAt:
        return 'Newest';
      case ProductSort.basePrice:
        return 'Price';
      case ProductSort.name:
        return 'Name';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
      child: Column(
        children: <Widget>[
          TextField(
            controller: _searchController,
            textInputAction: TextInputAction.search,
            onSubmitted: widget.onSearch,
            decoration: const InputDecoration(
              labelText: 'Search products',
              prefixIcon: Icon(Icons.search),
              border: OutlineInputBorder(),
              isDense: true,
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              Expanded(
                child: DropdownButtonFormField<int?>(
                  initialValue: widget.brandId,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: 'Brand',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  items: <DropdownMenuItem<int?>>[
                    const DropdownMenuItem<int?>(child: Text('All')),
                    for (final brand in widget.brands)
                      DropdownMenuItem<int?>(
                        value: brand.id,
                        child: Text(
                          brand.name,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                  ],
                  onChanged: widget.onBrandChanged,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: DropdownButtonFormField<int?>(
                  initialValue: widget.categoryId,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: 'Category',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  items: <DropdownMenuItem<int?>>[
                    const DropdownMenuItem<int?>(child: Text('All')),
                    for (final category in widget.categories)
                      DropdownMenuItem<int?>(
                        value: category.id,
                        child: Text(
                          category.name,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                  ],
                  onChanged: widget.onCategoryChanged,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: DropdownButtonFormField<ProductSort?>(
                  initialValue: widget.sort,
                  isExpanded: true,
                  decoration: const InputDecoration(
                    labelText: 'Sort',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  items: <DropdownMenuItem<ProductSort?>>[
                    const DropdownMenuItem<ProductSort?>(
                      child: Text('Default'),
                    ),
                    for (final option in ProductSort.values)
                      DropdownMenuItem<ProductSort?>(
                        value: option,
                        child: Text(_sortLabel(option)),
                      ),
                  ],
                  onChanged: widget.onSortChanged,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
