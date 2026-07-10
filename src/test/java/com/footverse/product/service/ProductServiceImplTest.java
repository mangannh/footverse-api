package com.footverse.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.footverse.product.entity.ProductVariantStatus;
import com.footverse.product.mapper.ProductImageMapper;
import com.footverse.product.repository.ProductImageRepository;
import com.footverse.product.repository.ProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Unit tests for {@link ProductServiceImpl}: catalog search (filters, pagination, sort whitelist),
 * product-detail assembly, and admin write (product create/update/soft-delete, image
 * create/update with the exactly-one-primary invariant).
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ProductImageMapper productImageMapper;

    @Mock
    private ProductVariantService productVariantService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private BrandService brandService;

    @Mock
    private EntityManager entityManager;

    private ProductServiceImpl service;

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        validatorFactory.close();
    }

    private void init() {
        service = new ProductServiceImpl(productRepository, productImageRepository, productImageMapper,
                productVariantService, categoryService, brandService, entityManager);
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Air Force 1");
        product.setDescription("Classic sneaker");
        product.setBasePrice(new BigDecimal("100.00"));
        Brand brand = new Brand();
        brand.setId(5L);
        brand.setName("Nike");
        product.setBrand(brand);
        Category category = new Category();
        category.setId(3L);
        category.setName("Sneakers");
        product.setCategory(category);
        return product;
    }

    private ProductImage image(Long id, String url, int order, boolean primary) {
        ProductImage image = new ProductImage();
        image.setId(id);
        image.setImageUrl(url);
        image.setDisplayOrder(order);
        image.setPrimary(primary);
        return image;
    }

    // ----- Search -----

    /**
     * Search delegates the filters to the repository and maps each product to a summary, preserving
     * the page metadata.
     */
    @Test
    void searchProductsMapsPageAndPreservesMetadata() {
        init();
        Product product = product(1L);
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
        ProductImage primary = image(9L, "primary.png", 0, true);
        primary.setProduct(product);
        when(productRepository.search("air", 5L, 3L, pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));
        when(productImageRepository.findPrimaryByProductIdIn(List.of(1L)))
                .thenReturn(List.of(primary));
        when(productVariantService.getPurchasableStateByProductIds(List.of(1L)))
                .thenReturn(Map.of(1L, true));

        PageResponse<ProductSummaryResponse> result = service.searchProducts("air", 5L, 3L, pageable);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.last()).isTrue();
        assertThat(result.content()).hasSize(1);
        ProductSummaryResponse summary = result.content().get(0);
        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.name()).isEqualTo("Air Force 1");
        assertThat(summary.basePrice()).isEqualByComparingTo("100.00");
        assertThat(summary.brandName()).isEqualTo("Nike");
        assertThat(summary.categoryName()).isEqualTo("Sneakers");
        assertThat(summary.primaryImageUrl()).isEqualTo("primary.png");
        assertThat(summary.averageRating()).isEqualByComparingTo("0.00");
        assertThat(summary.available()).isTrue();
    }

    /**
     * A product with no primary image yields a {@code null} {@code primaryImageUrl} rather than
     * failing.
     */
    @Test
    void searchProductWithoutPrimaryImageReturnsNullUrl() {
        init();
        Product product = product(1L);
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.search(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));
        when(productImageRepository.findPrimaryByProductIdIn(List.of(1L))).thenReturn(List.of());
        when(productVariantService.getPurchasableStateByProductIds(List.of(1L))).thenReturn(Map.of());

        PageResponse<ProductSummaryResponse> result = service.searchProducts(null, null, null, pageable);

        assertThat(result.content().get(0).primaryImageUrl()).isNull();
        assertThat(result.content().get(0).available()).isFalse();
    }

    /**
     * Every whitelisted sort key (and an unsorted request) is accepted and reaches the repository.
     */
    @Test
    void searchAcceptsWhitelistedSortKeys() {
        init();
        when(productRepository.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        for (String key : List.of("createdAt", "basePrice", "name")) {
            Pageable pageable = PageRequest.of(0, 20, Sort.by(key));
            assertThat(service.searchProducts(null, null, null, pageable)).isNotNull();
        }
        assertThat(service.searchProducts(null, null, null, PageRequest.of(0, 20))).isNotNull();
    }

    /**
     * A non-whitelisted sort key is rejected with an enveloped {@code 400}, and the repository is
     * never queried.
     */
    @Test
    void searchRejectsNonWhitelistedSortKey() {
        init();
        Pageable pageable = PageRequest.of(0, 20, Sort.by("price"));

        assertThatThrownBy(() -> service.searchProducts(null, null, null, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_SORT_INVALID")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
        verify(productRepository, never()).search(any(), any(), any(), any());
    }

    // ----- Summary by ids -----

    /**
     * The summary-by-ids read keys each resolved product by its id and assembles the same fields as
     * catalog search — name, base price, brand/category name, primary image URL, availability — from
     * one batched primary-image lookup and one batched availability lookup.
     */
    @Test
    void getSummariesByIdsAssemblesSummariesKeyedByProductId() {
        init();
        Product first = product(1L);
        Product second = product(2L);
        second.setName("Air Max");
        second.setBasePrice(new BigDecimal("150.00"));
        ProductImage primary = image(9L, "primary.png", 0, true);
        primary.setProduct(first);
        List<Long> ids = List.of(1L, 2L);
        when(productRepository.findByIdInAndDeletedAtIsNull(ids)).thenReturn(List.of(first, second));
        when(productImageRepository.findPrimaryByProductIdIn(ids)).thenReturn(List.of(primary));
        when(productVariantService.getPurchasableStateByProductIds(ids)).thenReturn(Map.of(1L, true, 2L, false));

        Map<Long, ProductSummaryResponse> result = service.getSummariesByIds(ids);

        assertThat(result).containsOnlyKeys(1L, 2L);
        ProductSummaryResponse firstSummary = result.get(1L);
        assertThat(firstSummary.id()).isEqualTo(1L);
        assertThat(firstSummary.name()).isEqualTo("Air Force 1");
        assertThat(firstSummary.basePrice()).isEqualByComparingTo("100.00");
        assertThat(firstSummary.brandName()).isEqualTo("Nike");
        assertThat(firstSummary.categoryName()).isEqualTo("Sneakers");
        assertThat(firstSummary.primaryImageUrl()).isEqualTo("primary.png");
        assertThat(firstSummary.averageRating()).isEqualByComparingTo("0.00");
        assertThat(firstSummary.available()).isTrue();
        ProductSummaryResponse secondSummary = result.get(2L);
        assertThat(secondSummary.name()).isEqualTo("Air Max");
        assertThat(secondSummary.basePrice()).isEqualByComparingTo("150.00");
        assertThat(secondSummary.primaryImageUrl()).isNull();
        assertThat(secondSummary.available()).isFalse();
    }

    /**
     * A soft-deleted (or unknown) id is absent from the soft-delete-aware read, so it is absent from
     * the result; the batched lookups are narrowed to the ids that actually resolved.
     */
    @Test
    void getSummariesByIdsSkipsSoftDeletedAndUnknownIds() {
        init();
        Product product = product(1L);
        when(productRepository.findByIdInAndDeletedAtIsNull(List.of(1L, 2L, 9L))).thenReturn(List.of(product));
        when(productImageRepository.findPrimaryByProductIdIn(List.of(1L))).thenReturn(List.of());
        when(productVariantService.getPurchasableStateByProductIds(List.of(1L))).thenReturn(Map.of());

        Map<Long, ProductSummaryResponse> result = service.getSummariesByIds(List.of(1L, 2L, 9L));

        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L).available()).isFalse();
    }

    /**
     * When no id resolves, the result is empty and neither the image nor the variant lookup runs.
     */
    @Test
    void getSummariesByIdsWithNoResolvingProductReturnsEmptyMap() {
        init();
        when(productRepository.findByIdInAndDeletedAtIsNull(List.of(9L))).thenReturn(List.of());

        assertThat(service.getSummariesByIds(List.of(9L))).isEmpty();
        verifyNoInteractions(productImageRepository, productVariantService);
    }

    /**
     * An empty id collection short-circuits: no query is issued at all.
     */
    @Test
    void getSummariesByIdsWithEmptyCollectionQueriesNothing() {
        init();

        assertThat(service.getSummariesByIds(List.of())).isEmpty();
        verifyNoInteractions(productRepository, productImageRepository, productVariantService);
    }

    // ----- Detail -----

    /**
     * Product detail assembles every field: own fields, brand/category associations, images in the
     * repository-provided order, variants from the variant service, derived availability, the
     * creation timestamp, and the rating/review placeholders.
     */
    @Test
    void getProductDetailAssemblesAllFields() {
        init();
        Product product = product(1L);
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        ReflectionTestUtils.setField(product, "createdAt", createdAt);
        ProductImage first = image(11L, "a.png", 0, true);
        ProductImage second = image(12L, "b.png", 1, false);
        ProductImageResponse firstResp = new ProductImageResponse(11L, "a.png", 0, true);
        ProductImageResponse secondResp = new ProductImageResponse(12L, "b.png", 1, false);
        ProductVariantResponse variant = new ProductVariantResponse(21L, "42", new BigDecimal("100.00"),
                5, ProductVariantStatus.ACTIVE, "SKU-42");
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(productImageRepository.findByProductIdOrderByDisplayOrderAsc(1L))
                .thenReturn(List.of(first, second));
        when(productImageMapper.toResponse(first)).thenReturn(firstResp);
        when(productImageMapper.toResponse(second)).thenReturn(secondResp);
        when(productVariantService.getVariantsByProduct(1L)).thenReturn(List.of(variant));
        when(productVariantService.hasPurchasableVariant(1L)).thenReturn(true);

        ProductDetailResponse result = service.getProductDetail(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Air Force 1");
        assertThat(result.description()).isEqualTo("Classic sneaker");
        assertThat(result.basePrice()).isEqualByComparingTo("100.00");
        assertThat(result.brandId()).isEqualTo(5L);
        assertThat(result.brandName()).isEqualTo("Nike");
        assertThat(result.categoryId()).isEqualTo(3L);
        assertThat(result.categoryName()).isEqualTo("Sneakers");
        assertThat(result.images()).containsExactly(firstResp, secondResp);
        assertThat(result.variants()).containsExactly(variant);
        assertThat(result.averageRating()).isEqualByComparingTo("0.00");
        assertThat(result.reviewCount()).isZero();
        assertThat(result.available()).isTrue();
        assertThat(result.createdAt()).isEqualTo(createdAt);
    }

    /**
     * A product with no purchasable variant is reported as unavailable in its detail.
     */
    @Test
    void getProductDetailReportsUnavailableWhenNoPurchasableVariant() {
        init();
        Product product = product(1L);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(productImageRepository.findByProductIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());
        when(productVariantService.getVariantsByProduct(1L)).thenReturn(List.of());
        when(productVariantService.hasPurchasableVariant(1L)).thenReturn(false);

        ProductDetailResponse result = service.getProductDetail(1L);

        assertThat(result.available()).isFalse();
        assertThat(result.images()).isEmpty();
        assertThat(result.variants()).isEmpty();
    }

    /**
     * A missing or soft-deleted product (absent from the soft-delete-aware read) is a {@code 404};
     * no variant or image work is attempted.
     */
    @Test
    void getProductDetailOnMissingOrSoftDeletedProductThrowsNotFound() {
        init();
        when(productRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProductDetail(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
        verifyNoInteractions(productImageRepository, productImageMapper, productVariantService);
    }

    // ----- Write: create product -----

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private Brand brand(Long id, String name) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        return brand;
    }

    /**
     * Creating a product validates category/brand via their services, binds the associations by id,
     * persists, and returns the assembled detail.
     */
    @Test
    void createProductPersistsAndReturnsDetail() {
        init();
        CreateProductRequest request = new CreateProductRequest("Air Max", "desc", new BigDecimal("150.00"), 3L, 5L);
        Category category = category(3L, "Sneakers");
        Brand brand = brand(5L, "Nike");
        when(categoryService.existsById(3L)).thenReturn(true);
        when(brandService.existsById(5L)).thenReturn(true);
        when(entityManager.getReference(Category.class, 3L)).thenReturn(category);
        when(entityManager.getReference(Brand.class, 5L)).thenReturn(brand);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(productImageRepository.findByProductIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());
        when(productVariantService.getVariantsByProduct(1L)).thenReturn(List.of());
        when(productVariantService.hasPurchasableVariant(1L)).thenReturn(false);

        ProductDetailResponse result = service.createProduct(request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Air Max");
        assertThat(result.basePrice()).isEqualByComparingTo("150.00");
        assertThat(result.categoryId()).isEqualTo(3L);
        assertThat(result.brandId()).isEqualTo(5L);
        assertThat(result.available()).isFalse();
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Air Max");
        assertThat(saved.getBasePrice()).isEqualByComparingTo("150.00");
        assertThat(saved.getCategory()).isSameAs(category);
        assertThat(saved.getBrand()).isSameAs(brand);
    }

    /**
     * An unknown category is a 404 {@code CATEGORY_NOT_FOUND}; nothing is persisted.
     */
    @Test
    void createProductWithMissingCategoryThrowsNotFound() {
        init();
        CreateProductRequest request = new CreateProductRequest("Air", "d", new BigDecimal("10.00"), 99L, 5L);
        when(categoryService.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CATEGORY_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
        verify(productRepository, never()).save(any());
    }

    /**
     * An unknown brand is a 404 {@code BRAND_NOT_FOUND}; nothing is persisted.
     */
    @Test
    void createProductWithMissingBrandThrowsNotFound() {
        init();
        CreateProductRequest request = new CreateProductRequest("Air", "d", new BigDecimal("10.00"), 3L, 99L);
        when(categoryService.existsById(3L)).thenReturn(true);
        when(brandService.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "BRAND_NOT_FOUND");
        verify(productRepository, never()).save(any());
    }

    /**
     * A non-positive base price is rejected at validation, not by a business exception.
     */
    @Test
    void nonPositiveBasePriceFailsBeanValidation() {
        CreateProductRequest request = new CreateProductRequest("Air", "d", BigDecimal.ZERO, 3L, 5L);

        Set<ConstraintViolation<CreateProductRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("basePrice"));
    }

    /**
     * A missing base price is rejected (required).
     */
    @Test
    void missingBasePriceFailsBeanValidation() {
        CreateProductRequest request = new CreateProductRequest("Air", "d", null, 3L, 5L);

        Set<ConstraintViolation<CreateProductRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("basePrice"));
    }

    // ----- Write: update product -----

    /**
     * A changed category is re-validated; an unknown one is a 404 and nothing is persisted. The
     * unchanged brand is not re-validated.
     */
    @Test
    void updateProductRevalidatesChangedCategory() {
        init();
        Product product = product(1L);
        UpdateProductRequest request = new UpdateProductRequest("New", "d", new BigDecimal("120.00"), 7L, 5L);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(categoryService.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateProduct(1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CATEGORY_NOT_FOUND");
        verify(productRepository, never()).save(any());
        verify(brandService, never()).existsById(any());
    }

    /**
     * A changed brand is re-validated; an unknown one is a 404 and nothing is persisted. The
     * unchanged category is not re-validated.
     */
    @Test
    void updateProductRevalidatesChangedBrand() {
        init();
        Product product = product(1L);
        UpdateProductRequest request = new UpdateProductRequest("New", "d", new BigDecimal("120.00"), 3L, 99L);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(brandService.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.updateProduct(1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "BRAND_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
        verify(productRepository, never()).save(any());
        verify(categoryService, never()).existsById(any());
    }

    /**
     * Changing both associations to existing ones rebinds each by reference and persists the product.
     */
    @Test
    void updateProductRebindsChangedCategoryAndBrand() {
        init();
        Product product = product(1L);
        Category newCategory = category(7L, "Boots");
        Brand newBrand = brand(9L, "Adidas");
        UpdateProductRequest request = new UpdateProductRequest("New", "d", new BigDecimal("120.00"), 7L, 9L);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(categoryService.existsById(7L)).thenReturn(true);
        when(brandService.existsById(9L)).thenReturn(true);
        when(entityManager.getReference(Category.class, 7L)).thenReturn(newCategory);
        when(entityManager.getReference(Brand.class, 9L)).thenReturn(newBrand);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productImageRepository.findByProductIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());
        when(productVariantService.getVariantsByProduct(1L)).thenReturn(List.of());
        when(productVariantService.hasPurchasableVariant(1L)).thenReturn(false);

        ProductDetailResponse result = service.updateProduct(1L, request);

        assertThat(result.categoryId()).isEqualTo(7L);
        assertThat(result.brandId()).isEqualTo(9L);
        assertThat(product.getCategory()).isSameAs(newCategory);
        assertThat(product.getBrand()).isSameAs(newBrand);
    }

    /**
     * Updating a missing (or already soft-deleted) product is a 404; the associations are never even
     * inspected.
     */
    @Test
    void updateMissingProductThrowsNotFound() {
        init();
        UpdateProductRequest request = new UpdateProductRequest("New", "d", new BigDecimal("120.00"), 3L, 5L);
        when(productRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProduct(9L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
        verify(productRepository, never()).save(any());
        verify(categoryService, never()).existsById(any());
        verify(brandService, never()).existsById(any());
    }

    /**
     * When neither category nor brand changes, no existence check runs; the scalar fields are still
     * updated.
     */
    @Test
    void updateProductSkipsValidationWhenAssociationsUnchanged() {
        init();
        Product product = product(1L);
        UpdateProductRequest request = new UpdateProductRequest("Renamed", "newdesc", new BigDecimal("200.00"), 3L, 5L);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productImageRepository.findByProductIdOrderByDisplayOrderAsc(1L)).thenReturn(List.of());
        when(productVariantService.getVariantsByProduct(1L)).thenReturn(List.of());
        when(productVariantService.hasPurchasableVariant(1L)).thenReturn(false);

        ProductDetailResponse result = service.updateProduct(1L, request);

        assertThat(result.name()).isEqualTo("Renamed");
        assertThat(result.basePrice()).isEqualByComparingTo("200.00");
        assertThat(result.categoryId()).isEqualTo(3L);
        assertThat(result.brandId()).isEqualTo(5L);
        verify(categoryService, never()).existsById(any());
        verify(brandService, never()).existsById(any());
    }

    // ----- Write: soft delete -----

    /**
     * Delete is a soft delete: it stamps {@code deletedAt} and saves the row (no physical removal).
     */
    @Test
    void deleteProductSoftDeletesByStampingDeletedAt() {
        init();
        Product product = product(1L);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteProduct(1L);

        assertThat(product.getDeletedAt()).isNotNull();
        verify(productRepository).save(product);
        verify(productRepository, never()).delete(any());
    }

    /**
     * Deleting a missing (or already soft-deleted) product is a 404.
     */
    @Test
    void deleteMissingProductThrowsNotFound() {
        init();
        when(productRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProduct(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_FOUND");
        verify(productRepository, never()).save(any());
    }

    // ----- Write: image management + one-primary invariant -----

    /**
     * Creating a primary image clears the product's previous primary, so exactly one primary
     * remains.
     */
    @Test
    void createImageAsPrimaryClearsExistingPrimary() {
        init();
        Product product = product(1L);
        ProductImage oldPrimary = image(8L, "old.png", 0, true);
        oldPrimary.setProduct(product);
        when(productRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(product));
        when(productImageRepository.findPrimaryByProductIdForUpdate(1L)).thenReturn(List.of(oldPrimary));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductImageResponse mapped = new ProductImageResponse(10L, "new.png", 1, true);
        when(productImageMapper.toResponse(any(ProductImage.class))).thenReturn(mapped);
        CreateProductImageRequest request = new CreateProductImageRequest("new.png", 1, true);

        ProductImageResponse result = service.createImage(1L, request);

        assertThat(result).isEqualTo(mapped);
        assertThat(oldPrimary.isPrimary()).isFalse();
        ArgumentCaptor<ProductImage> captor = ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageRepository).save(captor.capture());
        ProductImage saved = captor.getValue();
        assertThat(saved.getImageUrl()).isEqualTo("new.png");
        assertThat(saved.getDisplayOrder()).isEqualTo(1);
        assertThat(saved.isPrimary()).isTrue();
        assertThat(saved.getProduct()).isSameAs(product);
    }

    /**
     * Creating a non-primary image never touches the existing primary.
     */
    @Test
    void createImageNotPrimaryDoesNotClearExistingPrimary() {
        init();
        Product product = product(1L);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productImageMapper.toResponse(any(ProductImage.class)))
                .thenReturn(new ProductImageResponse(10L, "x.png", 2, false));
        CreateProductImageRequest request = new CreateProductImageRequest("x.png", 2, false);

        service.createImage(1L, request);

        verify(productImageRepository, never()).findPrimaryByProductIdForUpdate(any());
        verify(productRepository, never()).findByIdAndDeletedAtIsNullForUpdate(any());
    }

    /**
     * Creating a non-primary image on a missing (or soft-deleted) product is a 404; the plain read
     * guards that path.
     */
    @Test
    void createImageNotPrimaryOnMissingProductThrowsNotFound() {
        init();
        when(productRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());
        CreateProductImageRequest request = new CreateProductImageRequest("x.png", 2, false);

        assertThatThrownBy(() -> service.createImage(9L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
        verify(productImageRepository, never()).save(any());
    }

    /**
     * Creating a primary image on a missing (or soft-deleted) product is a 404 too: the locking read
     * guards that path, and the existing primary is never cleared.
     */
    @Test
    void createImageAsPrimaryOnMissingProductThrowsNotFound() {
        init();
        when(productRepository.findByIdAndDeletedAtIsNullForUpdate(9L)).thenReturn(Optional.empty());
        CreateProductImageRequest request = new CreateProductImageRequest("x.png", 0, true);

        assertThatThrownBy(() -> service.createImage(9L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_FOUND");
        verify(productImageRepository, never()).findPrimaryByProductIdForUpdate(any());
        verify(productImageRepository, never()).save(any());
    }

    /**
     * Updating an image to primary clears the product's previous primary and marks this one primary.
     */
    @Test
    void updateImageAsPrimaryClearsPreviousPrimary() {
        init();
        Product product = product(1L);
        ProductImage target = image(20L, "t.png", 0, false);
        target.setProduct(product);
        ProductImage oldPrimary = image(19L, "old.png", 1, true);
        oldPrimary.setProduct(product);
        when(productImageRepository.findById(20L)).thenReturn(Optional.of(target));
        when(productRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(product));
        when(productImageRepository.findPrimaryByProductIdForUpdate(1L)).thenReturn(List.of(oldPrimary));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductImageResponse mapped = new ProductImageResponse(20L, "u.png", 2, true);
        when(productImageMapper.toResponse(target)).thenReturn(mapped);
        UpdateProductImageRequest request = new UpdateProductImageRequest("u.png", 2, true);

        ProductImageResponse result = service.updateImage(1L, 20L, request);

        assertThat(result).isEqualTo(mapped);
        assertThat(oldPrimary.isPrimary()).isFalse();
        assertThat(target.isPrimary()).isTrue();
        assertThat(target.getImageUrl()).isEqualTo("u.png");
        assertThat(target.getDisplayOrder()).isEqualTo(2);
    }

    /**
     * Updating an image to non-primary demotes it without locking the product or clearing any
     * primary: the one-primary invariant is only defended on the promoting path.
     */
    @Test
    void updateImageToNonPrimaryDoesNotLockOrClearPrimary() {
        init();
        Product product = product(1L);
        ProductImage target = image(20L, "t.png", 0, true);
        target.setProduct(product);
        when(productImageRepository.findById(20L)).thenReturn(Optional.of(target));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductImageResponse mapped = new ProductImageResponse(20L, "u.png", 3, false);
        when(productImageMapper.toResponse(target)).thenReturn(mapped);
        UpdateProductImageRequest request = new UpdateProductImageRequest("u.png", 3, false);

        ProductImageResponse result = service.updateImage(1L, 20L, request);

        assertThat(result).isEqualTo(mapped);
        assertThat(target.isPrimary()).isFalse();
        verify(productRepository, never()).findByIdAndDeletedAtIsNullForUpdate(any());
        verify(productImageRepository, never()).findPrimaryByProductIdForUpdate(any());
    }

    /**
     * Updating an image that does not belong to the given product is a 404.
     */
    @Test
    void updateImageNotBelongingToProductThrowsNotFound() {
        init();
        Product other = product(2L);
        ProductImage target = image(20L, "t.png", 0, false);
        target.setProduct(other);
        when(productImageRepository.findById(20L)).thenReturn(Optional.of(target));
        UpdateProductImageRequest request = new UpdateProductImageRequest("u.png", 2, true);

        assertThatThrownBy(() -> service.updateImage(1L, 20L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_IMAGE_NOT_FOUND");
        verify(productImageRepository, never()).save(any());
    }
}
