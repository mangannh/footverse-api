package com.footverse.product.service;

import java.util.Collection;
import java.util.Map;

import org.springframework.data.domain.Pageable;

import com.footverse.common.dto.PageResponse;
import com.footverse.product.dto.CreateProductImageRequest;
import com.footverse.product.dto.CreateProductRequest;
import com.footverse.product.dto.ProductDetailResponse;
import com.footverse.product.dto.ProductImageResponse;
import com.footverse.product.dto.ProductSummaryResponse;
import com.footverse.product.dto.UpdateProductImageRequest;
import com.footverse.product.dto.UpdateProductRequest;

/**
 * Catalog read side of the {@code product} module: public browse/search and product detail. This
 * is the {@code ProductService} half of the architecture-spec §7 split; it assembles the
 * multi-source aggregate responses ({@code ProductSummaryResponse} / {@code ProductDetailResponse})
 * from the product, its brand/category associations, its images, and {@code ProductVariantService}.
 * The Sprint 2 read side depends on nothing outside the {@code product} module.
 */
public interface ProductService {

    /**
     * Searches the non-deleted catalog by an optional case-insensitive partial name and optional
     * brand / category filters, paginated and sorted. The sort is restricted to the
     * {@code createdAt} / {@code basePrice} / {@code name} whitelist; any other sort key is
     * rejected (validation-spec §6).
     *
     * @param name       optional case-insensitive partial product name; {@code null} to skip
     * @param brandId    optional brand filter; {@code null} to skip
     * @param categoryId optional category filter; {@code null} to skip
     * @param pageable   the pagination and (whitelisted) sort request
     * @return the matching page of product summaries
     */
    PageResponse<ProductSummaryResponse> searchProducts(String name, Long brandId, Long categoryId,
            Pageable pageable);

    /**
     * Returns the summary of each non-deleted product among the given ids, keyed by product id.
     * The summaries are assembled exactly as catalog search assembles them — same primary-image and
     * availability sources — and every id is resolved in one bounded set of queries, so there is no
     * per-product N+1. An unknown or soft-deleted id is simply absent from the result, which lets a
     * caller listing rows that reference products (e.g. the wishlist) skip the ones that no longer
     * resolve.
     *
     * <p>The returned map has <strong>no defined iteration order</strong>: it is a lookup table, not
     * a listing. A caller that renders an ordered list orders its own rows and looks each product up
     * here.</p>
     *
     * @param productIds the product ids to resolve; an empty collection yields an empty result
     * @return a map from product id to its summary; keys are limited to the ids that resolve to a
     *         non-deleted product
     */
    Map<Long, ProductSummaryResponse> getSummariesByIds(Collection<Long> productIds);

    /**
     * Returns the full detail of a non-deleted product, assembled from the product, its images
     * (sorted by {@code displayOrder}), and its variants.
     *
     * @param id the product id
     * @return the assembled product detail
     */
    ProductDetailResponse getProductDetail(Long id);

    /**
     * Creates a product. The {@code categoryId} and {@code brandId} must reference existing
     * category / brand (validated via their services → {@code 404 CATEGORY_NOT_FOUND} /
     * {@code BRAND_NOT_FOUND}); the base price must be positive (Bean Validation).
     *
     * @param request the validated create payload
     * @return the created product's detail
     */
    ProductDetailResponse createProduct(CreateProductRequest request);

    /**
     * Updates a product. A changed {@code categoryId} / {@code brandId} is re-validated against the
     * category / brand service ({@code 404} when missing).
     *
     * @param id      the id of the product to update
     * @param request the validated update payload
     * @return the updated product's detail
     */
    ProductDetailResponse updateProduct(Long id, UpdateProductRequest request);

    /**
     * Soft-deletes a product by stamping {@code deletedAt}; the row remains but disappears from
     * public reads.
     *
     * @param id the id of the product to delete
     */
    void deleteProduct(Long id);

    /**
     * Creates an image for a product. When {@code isPrimary} is true the product's previous primary
     * image is cleared so exactly one primary remains (architecture-spec §13).
     *
     * @param productId the owning product id
     * @param request   the validated create payload
     * @return the created image
     */
    ProductImageResponse createImage(Long productId, CreateProductImageRequest request);

    /**
     * Updates an image of a product. The image must belong to the product; when {@code isPrimary}
     * is true the product's previous primary image is cleared so exactly one primary remains.
     *
     * @param productId the owning product id
     * @param imageId   the id of the image to update
     * @param request   the validated update payload
     * @return the updated image
     */
    ProductImageResponse updateImage(Long productId, Long imageId, UpdateProductImageRequest request);
}
