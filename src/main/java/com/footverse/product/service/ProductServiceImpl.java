package com.footverse.product.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.brand.entity.Brand;
import com.footverse.brand.service.BrandService;
import com.footverse.category.entity.Category;
import com.footverse.category.service.CategoryService;
import com.footverse.common.dto.PageResponse;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.product.dto.CreateProductImageRequest;
import com.footverse.product.dto.CreateProductRequest;
import com.footverse.product.dto.ProductDetailResponse;
import com.footverse.product.dto.ProductImageResponse;
import com.footverse.product.dto.ProductSummaryResponse;
import com.footverse.product.dto.ProductVariantResponse;
import com.footverse.product.dto.UpdateProductImageRequest;
import com.footverse.product.dto.UpdateProductRequest;
import com.footverse.product.entity.Product;
import com.footverse.product.entity.ProductImage;
import com.footverse.product.mapper.ProductImageMapper;
import com.footverse.product.repository.ProductImageRepository;
import com.footverse.product.repository.ProductRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link ProductService} implementation: catalog search / summary-by-ids /
 * product-detail assembly (read) and admin product & image management (write). It composes the
 * multi-source aggregate responses
 * directly (per the frozen R1 decision there is no {@code ProductMapper}), reusing
 * {@link ProductImageMapper} for image pieces and delegating all variant data — including the
 * purchasability rule — to {@link ProductVariantService}, so no business rule is duplicated and
 * the bean graph stays acyclic (architecture-spec §7/§9).
 *
 * <p>Cross-feature validation on create/update goes through {@link CategoryService} and
 * {@link BrandService} (existence only, one-way Product → Category / Brand); the associations are
 * bound by id via {@link EntityManager#getReference} without an extra query. The service enforces
 * the exactly-one-primary-image invariant (architecture-spec §13) and soft-deletes products by
 * stamping {@code deletedAt}.</p>
 *
 * <p>Until the {@code review} module lands, {@code averageRating} and {@code reviewCount} are the
 * documented placeholders {@code 0.00} / {@code 0} (sprint-2-plan item 09).</p>
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** Sort keys the catalog search accepts; anything else is rejected (validation-spec §6). */
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "basePrice", "name");

    /** Placeholder rating until the {@code review} module supplies live data. */
    private static final BigDecimal PLACEHOLDER_AVERAGE_RATING = new BigDecimal("0.00");

    /** Placeholder review count until the {@code review} module supplies live data. */
    private static final int PLACEHOLDER_REVIEW_COUNT = 0;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductImageMapper productImageMapper;
    private final ProductVariantService productVariantService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> searchProducts(String name, Long brandId, Long categoryId,
            Pageable pageable) {
        validateSort(pageable.getSort());
        Page<Product> page = productRepository.search(name, brandId, categoryId, pageable);
        List<Long> productIds = page.getContent().stream().map(Product::getId).toList();
        Map<Long, String> primaryImageUrls = productIds.isEmpty()
                ? Map.of()
                : primaryImageUrlsByProductIds(productIds);
        Map<Long, Boolean> availability = productIds.isEmpty()
                ? Map.of()
                : productVariantService.getPurchasableStateByProductIds(productIds);
        return PageResponse.from(page.map(product -> toSummary(product, primaryImageUrls, availability)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ProductSummaryResponse> getSummariesByIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productRepository.findByIdInAndDeletedAtIsNull(productIds);
        if (products.isEmpty()) {
            return Map.of();
        }
        List<Long> resolvedIds = products.stream().map(Product::getId).toList();
        Map<Long, String> primaryImageUrls = primaryImageUrlsByProductIds(resolvedIds);
        Map<Long, Boolean> availability = productVariantService.getPurchasableStateByProductIds(resolvedIds);
        return products.stream()
                .collect(Collectors.toMap(Product::getId,
                        product -> toSummary(product, primaryImageUrls, availability)));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        return assembleDetail(product);
    }

    @Override
    @Transactional
    public ProductDetailResponse createProduct(CreateProductRequest request) {
        if (!categoryService.existsById(request.categoryId())) {
            throw new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Category not found");
        }
        if (!brandService.existsById(request.brandId())) {
            throw new ResourceNotFoundException("BRAND_NOT_FOUND", "Brand not found");
        }
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setBasePrice(request.basePrice());
        product.setCategory(entityManager.getReference(Category.class, request.categoryId()));
        product.setBrand(entityManager.getReference(Brand.class, request.brandId()));
        return assembleDetail(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductDetailResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        if (!product.getCategory().getId().equals(request.categoryId())) {
            if (!categoryService.existsById(request.categoryId())) {
                throw new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Category not found");
            }
            product.setCategory(entityManager.getReference(Category.class, request.categoryId()));
        }
        if (!product.getBrand().getId().equals(request.brandId())) {
            if (!brandService.existsById(request.brandId())) {
                throw new ResourceNotFoundException("BRAND_NOT_FOUND", "Brand not found");
            }
            product.setBrand(entityManager.getReference(Brand.class, request.brandId()));
        }
        product.setName(request.name());
        product.setDescription(request.description());
        product.setBasePrice(request.basePrice());
        return assembleDetail(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    @Override
    @Transactional
    public ProductImageResponse createImage(Long productId, CreateProductImageRequest request) {
        Product product;
        if (request.isPrimary()) {
            product = lockProduct(productId);
            clearExistingPrimary(productId);
        } else {
            product = productRepository.findByIdAndDeletedAtIsNull(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        }
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(request.imageUrl());
        image.setDisplayOrder(request.displayOrder());
        image.setPrimary(request.isPrimary());
        return productImageMapper.toResponse(productImageRepository.save(image));
    }

    @Override
    @Transactional
    public ProductImageResponse updateImage(Long productId, Long imageId, UpdateProductImageRequest request) {
        ProductImage image = productImageRepository.findById(imageId)
                .filter(existing -> existing.getProduct().getId().equals(productId))
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_IMAGE_NOT_FOUND",
                        "Product image not found"));
        if (request.isPrimary()) {
            lockProduct(productId);
            clearExistingPrimary(productId);
        }
        image.setImageUrl(request.imageUrl());
        image.setDisplayOrder(request.displayOrder());
        image.setPrimary(request.isPrimary());
        return productImageMapper.toResponse(productImageRepository.save(image));
    }

    /**
     * Acquires the per-product {@code PESSIMISTIC_WRITE} row lock that serializes concurrent primary
     * changes (architecture-spec §13). Called at the start of the primary-mutating path so only one
     * transaction at a time may retarget a product's primary image; the lock is held until commit.
     *
     * @param productId the owning product id
     * @return the locked, non-deleted product
     */
    private Product lockProduct(Long productId) {
        return productRepository.findByIdAndDeletedAtIsNullForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
    }

    /**
     * Clears the {@code isPrimary} flag on the product's current primary image(s), enforcing the
     * exactly-one-primary-image invariant before a new primary is set (architecture-spec §13). Uses
     * a current (locking) read so it observes the latest committed primary even under
     * {@code REPEATABLE READ}; the caller must already hold the product row lock via
     * {@link #lockProduct(Long)}. The loaded images are managed, so the cleared flags are flushed
     * with the transaction.
     *
     * @param productId the owning product id
     */
    private void clearExistingPrimary(Long productId) {
        for (ProductImage primary : productImageRepository.findPrimaryByProductIdForUpdate(productId)) {
            primary.setPrimary(false);
        }
    }

    /**
     * Assembles the full product detail: own fields, brand/category associations, images (sorted by
     * {@code displayOrder}), variants and derived availability from {@link ProductVariantService},
     * and the rating/review placeholders.
     *
     * @param product the source product
     * @return the assembled detail response
     */
    private ProductDetailResponse assembleDetail(Product product) {
        Long id = product.getId();
        List<ProductImageResponse> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(id).stream()
                .map(productImageMapper::toResponse)
                .toList();
        List<ProductVariantResponse> variants = productVariantService.getVariantsByProduct(id);
        boolean available = productVariantService.hasPurchasableVariant(id);
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                product.getBrand().getId(),
                product.getBrand().getName(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                images,
                variants,
                PLACEHOLDER_AVERAGE_RATING,
                PLACEHOLDER_REVIEW_COUNT,
                available,
                product.getCreatedAt());
    }

    /**
     * Loads the primary image URL of each product in a single query, keyed by product id. A
     * product with no primary image is simply absent from the map.
     *
     * @param productIds the product ids to resolve
     * @return a map from product id to its primary image URL
     */
    private Map<Long, String> primaryImageUrlsByProductIds(List<Long> productIds) {
        return productImageRepository.findPrimaryByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(image -> image.getProduct().getId(), ProductImage::getImageUrl));
    }

    /**
     * Assembles a product summary from the product's own fields, its brand/category names, and the
     * pre-loaded primary-image and availability maps — never querying inside the mapping loop.
     *
     * @param product          the source product
     * @param primaryImageUrls primary image URL by product id (absent when the product has none)
     * @param availability     purchasability by product id (absent when the product has no
     *                         purchasable variant)
     * @return the summary response
     */
    private ProductSummaryResponse toSummary(Product product, Map<Long, String> primaryImageUrls,
            Map<Long, Boolean> availability) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getBasePrice(),
                product.getBrand().getName(),
                product.getCategory().getName(),
                primaryImageUrls.get(product.getId()),
                PLACEHOLDER_AVERAGE_RATING,
                availability.getOrDefault(product.getId(), false));
    }

    /**
     * Rejects any requested sort key outside the whitelist with an enveloped {@code 400}
     * (error-spec §3, input-based business rejection). An unsorted request passes untouched.
     *
     * @param sort the requested sort
     */
    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PRODUCT_SORT_INVALID",
                        "Sort is only allowed by: createdAt, basePrice, name");
            }
        }
    }
}
