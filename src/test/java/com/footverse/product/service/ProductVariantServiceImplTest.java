package com.footverse.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.footverse.common.exception.DuplicateResourceException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.product.dto.CreateProductVariantRequest;
import com.footverse.product.dto.ProductVariantPurchaseSnapshot;
import com.footverse.product.dto.ProductVariantResponse;
import com.footverse.product.dto.UpdateProductVariantRequest;
import com.footverse.product.entity.Product;
import com.footverse.product.entity.ProductImage;
import com.footverse.product.entity.ProductVariant;
import com.footverse.product.entity.ProductVariantStatus;
import com.footverse.product.mapper.ProductVariantMapper;
import com.footverse.product.repository.ProductImageRepository;
import com.footverse.product.repository.ProductRepository;
import com.footverse.product.repository.ProductVariantRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Unit tests for {@link ProductVariantServiceImpl}: variant reads (effective price passthrough),
 * the purchasability rule, the per-variant purchase snapshot, admin create/update conflicts, and
 * the Bean Validation guards.
 */
@ExtendWith(MockitoExtension.class)
class ProductVariantServiceImplTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ProductVariantMapper productVariantMapper;

    private ProductVariantServiceImpl service;

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

    @BeforeEach
    void setUp() {
        service = new ProductVariantServiceImpl(productVariantRepository, productRepository,
                productImageRepository, productVariantMapper);
    }

    private Product product(Long id, String basePrice) {
        Product product = new Product();
        product.setId(id);
        product.setBasePrice(new BigDecimal(basePrice));
        return product;
    }

    private ProductVariant variant(Long id, Product product, String size, String sku, int stock,
            BigDecimal priceOverride, ProductVariantStatus status) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setSize(size);
        variant.setSku(sku);
        variant.setStockQuantity(stock);
        variant.setPriceOverride(priceOverride);
        variant.setStatus(status);
        return variant;
    }

    // ----- Variant read + effective price -----

    /**
     * The read returns exactly what the mapper produced; the service never recomputes the price.
     */
    @Test
    void getVariantsByProductReturnsTheMapperResponsesUnchanged() {
        Product product = product(1L, "100.00");
        ProductVariant entity = variant(10L, product, "42", "SKU-42", 5,
                new BigDecimal("111.00"), ProductVariantStatus.ACTIVE);
        ProductVariantResponse mapped = new ProductVariantResponse(10L, "42", new BigDecimal("111.00"),
                5, ProductVariantStatus.ACTIVE, "SKU-42");
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(entity));
        when(productVariantMapper.toResponse(entity)).thenReturn(mapped);

        List<ProductVariantResponse> result = service.getVariantsByProduct(1L);

        assertThat(result).containsExactly(mapped);
        assertThat(result.get(0).price()).isEqualByComparingTo("111.00");
    }

    // ----- Purchasability -----

    /**
     * A product with an ACTIVE, in-stock variant is purchasable.
     */
    @Test
    void productWithActiveInStockVariantIsPurchasable() {
        Product product = product(1L, "100.00");
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(
                variant(1L, product, "42", "S1", 3, null, ProductVariantStatus.ACTIVE)));

        assertThat(service.hasPurchasableVariant(1L)).isTrue();
    }

    /**
     * A product whose variants are all inactive or out of stock is not purchasable.
     */
    @Test
    void productWithOnlyInactiveOrOutOfStockVariantsIsNotPurchasable() {
        Product product = product(1L, "100.00");
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of(
                variant(1L, product, "42", "S1", 0, null, ProductVariantStatus.ACTIVE),
                variant(2L, product, "43", "S2", 5, null, ProductVariantStatus.INACTIVE)));

        assertThat(service.hasPurchasableVariant(1L)).isFalse();
    }

    /**
     * A product with no variants at all is not purchasable.
     */
    @Test
    void productWithNoVariantsIsNotPurchasable() {
        when(productVariantRepository.findByProductId(1L)).thenReturn(List.of());

        assertThat(service.hasPurchasableVariant(1L)).isFalse();
    }

    /**
     * The batch purchasability resolves every product in one variant query and applies the same
     * rule: a product with an ACTIVE in-stock variant is {@code true}, one with only
     * inactive/out-of-stock variants is {@code false}, and one with no variants is absent.
     */
    @Test
    void getPurchasableStateByProductIdsGroupsAndAppliesRule() {
        Product p1 = product(1L, "100.00");
        Product p2 = product(2L, "100.00");
        when(productVariantRepository.findByProductIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(
                variant(1L, p1, "42", "S1", 0, null, ProductVariantStatus.ACTIVE),
                variant(2L, p1, "43", "S2", 5, null, ProductVariantStatus.ACTIVE),
                variant(3L, p2, "42", "S3", 5, null, ProductVariantStatus.INACTIVE)));

        Map<Long, Boolean> result = service.getPurchasableStateByProductIds(List.of(1L, 2L, 3L));

        assertThat(result).containsEntry(1L, true);
        assertThat(result).containsEntry(2L, false);
        assertThat(result).doesNotContainKey(3L);
    }

    // ----- Purchase snapshot -----

    private Product namedProduct(Long id, String name, String basePrice) {
        Product product = product(id, basePrice);
        product.setName(name);
        return product;
    }

    private ProductImage primaryImage(Product product, String url) {
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(url);
        image.setPrimary(true);
        return image;
    }

    /**
     * Stubs the mapper's effective-price resolution to run its real implementation, so the snapshot
     * tests exercise the one place that owns the {@code priceOverride} → {@code basePrice} rule
     * rather than a canned value.
     */
    private void withRealEffectivePrice(ProductVariant variant) {
        when(productVariantMapper.effectivePrice(variant)).thenCallRealMethod();
    }

    /**
     * The snapshot of an existing variant carries the variant's own fields, the owning product's id
     * and name, and the product's primary image URL.
     */
    @Test
    void getPurchaseSnapshotResolvesEveryFieldOfAnExistingVariant() {
        Product product = namedProduct(1L, "Air Force 1", "100.00");
        ProductVariant variant = variant(10L, product, "42", "SKU-42", 7,
                new BigDecimal("111.00"), ProductVariantStatus.ACTIVE);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(productImageRepository.findPrimaryByProductIdIn(List.of(1L)))
                .thenReturn(List.of(primaryImage(product, "primary.png")));
        withRealEffectivePrice(variant);

        ProductVariantPurchaseSnapshot snapshot = service.getPurchaseSnapshot(10L);

        assertThat(snapshot.productVariantId()).isEqualTo(10L);
        assertThat(snapshot.productId()).isEqualTo(1L);
        assertThat(snapshot.productName()).isEqualTo("Air Force 1");
        assertThat(snapshot.primaryImageUrl()).isEqualTo("primary.png");
        assertThat(snapshot.size()).isEqualTo("42");
        assertThat(snapshot.unitPrice()).isEqualByComparingTo("111.00");
        assertThat(snapshot.stockQuantity()).isEqualTo(7);
        assertThat(snapshot.active()).isTrue();
    }

    /**
     * An unknown variant reuses the existing {@code 404 PRODUCT_VARIANT_NOT_FOUND}; no new error
     * code is introduced.
     */
    @Test
    void getPurchaseSnapshotOfUnknownVariantThrowsNotFound() {
        when(productVariantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPurchaseSnapshot(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND)
                .hasMessage("Product variant not found");
        verify(productImageRepository, never()).findPrimaryByProductIdIn(any());
    }

    /**
     * The unit price is the variant's {@code priceOverride} when it is set.
     */
    @Test
    void getPurchaseSnapshotUsesPriceOverrideWhenSet() {
        Product product = namedProduct(1L, "Air Force 1", "100.00");
        ProductVariant variant = variant(10L, product, "42", "SKU-42", 5,
                new BigDecimal("120.50"), ProductVariantStatus.ACTIVE);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(productImageRepository.findPrimaryByProductIdIn(List.of(1L))).thenReturn(List.of());
        withRealEffectivePrice(variant);

        assertThat(service.getPurchaseSnapshot(10L).unitPrice()).isEqualByComparingTo("120.50");
    }

    /**
     * The unit price falls back to the owning product's {@code basePrice} when there is no override.
     */
    @Test
    void getPurchaseSnapshotFallsBackToBasePriceWhenNoOverride() {
        Product product = namedProduct(1L, "Air Force 1", "100.00");
        ProductVariant variant = variant(10L, product, "42", "SKU-42", 5, null, ProductVariantStatus.ACTIVE);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(productImageRepository.findPrimaryByProductIdIn(List.of(1L))).thenReturn(List.of());
        withRealEffectivePrice(variant);

        assertThat(service.getPurchaseSnapshot(10L).unitPrice()).isEqualByComparingTo("100.00");
    }

    /**
     * A product with no primary image yields a {@code null} URL — not an exception, not a
     * placeholder.
     */
    @Test
    void getPurchaseSnapshotReturnsNullImageUrlWhenProductHasNoPrimaryImage() {
        Product product = namedProduct(1L, "Air Force 1", "100.00");
        ProductVariant variant = variant(10L, product, "42", "SKU-42", 5, null, ProductVariantStatus.ACTIVE);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(productImageRepository.findPrimaryByProductIdIn(List.of(1L))).thenReturn(List.of());
        withRealEffectivePrice(variant);

        assertThat(service.getPurchaseSnapshot(10L).primaryImageUrl()).isNull();
    }

    /**
     * {@code active} mirrors the variant status alone: an INACTIVE variant with stock on hand is
     * still {@code active = false}, and the stock is reported unchanged.
     */
    @Test
    void getPurchaseSnapshotReportsInactiveVariantWithStock() {
        Product product = namedProduct(1L, "Air Force 1", "100.00");
        ProductVariant variant = variant(10L, product, "42", "SKU-42", 9, null, ProductVariantStatus.INACTIVE);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(productImageRepository.findPrimaryByProductIdIn(List.of(1L))).thenReturn(List.of());
        withRealEffectivePrice(variant);

        ProductVariantPurchaseSnapshot snapshot = service.getPurchaseSnapshot(10L);

        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.stockQuantity()).isEqualTo(9);
    }

    /**
     * Conversely, an ACTIVE variant that is out of stock stays {@code active = true}: the flag is
     * never derived from the stock level. Deciding availability is the caller's job.
     */
    @Test
    void getPurchaseSnapshotReportsActiveVariantWithoutStock() {
        Product product = namedProduct(1L, "Air Force 1", "100.00");
        ProductVariant variant = variant(10L, product, "42", "SKU-42", 0, null, ProductVariantStatus.ACTIVE);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));
        when(productImageRepository.findPrimaryByProductIdIn(List.of(1L))).thenReturn(List.of());
        withRealEffectivePrice(variant);

        ProductVariantPurchaseSnapshot snapshot = service.getPurchaseSnapshot(10L);

        assertThat(snapshot.active()).isTrue();
        assertThat(snapshot.stockQuantity()).isZero();
    }

    // ----- Create -----

    /**
     * The happy path persists a variant carrying every field and associated with its product.
     */
    @Test
    void createVariantPersistsAndReturnsResponse() {
        Product product = product(1L, "100.00");
        CreateProductVariantRequest request = new CreateProductVariantRequest("42", 5, "SKU-42",
                new BigDecimal("120.00"), ProductVariantStatus.ACTIVE);
        ProductVariantResponse mapped = new ProductVariantResponse(null, "42", new BigDecimal("120.00"),
                5, ProductVariantStatus.ACTIVE, "SKU-42");
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(productVariantRepository.existsByProductIdAndSize(1L, "42")).thenReturn(false);
        when(productVariantRepository.existsBySku("SKU-42")).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productVariantMapper.toResponse(any(ProductVariant.class))).thenReturn(mapped);

        ProductVariantResponse result = service.createVariant(1L, request);

        assertThat(result).isEqualTo(mapped);
        ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).save(captor.capture());
        ProductVariant saved = captor.getValue();
        assertThat(saved.getProduct()).isSameAs(product);
        assertThat(saved.getSize()).isEqualTo("42");
        assertThat(saved.getStockQuantity()).isEqualTo(5);
        assertThat(saved.getSku()).isEqualTo("SKU-42");
        assertThat(saved.getPriceOverride()).isEqualByComparingTo("120.00");
        assertThat(saved.getStatus()).isEqualTo(ProductVariantStatus.ACTIVE);
    }

    /**
     * Creating a variant for a missing (or soft-deleted) product is a 404.
     */
    @Test
    void createVariantOnMissingProductThrowsNotFound() {
        when(productRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());
        CreateProductVariantRequest request = new CreateProductVariantRequest("42", 5, "SKU",
                null, ProductVariantStatus.ACTIVE);

        assertThatThrownBy(() -> service.createVariant(9L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_NOT_FOUND");
        verify(productVariantRepository, never()).save(any());
    }

    /**
     * A duplicate {@code (product, size)} is an enveloped 409.
     */
    @Test
    void createVariantWithDuplicateSizeThrowsConflict() {
        Product product = product(1L, "100.00");
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(productVariantRepository.existsByProductIdAndSize(1L, "42")).thenReturn(true);
        CreateProductVariantRequest request = new CreateProductVariantRequest("42", 5, "SKU",
                null, ProductVariantStatus.ACTIVE);

        assertThatThrownBy(() -> service.createVariant(1L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_SIZE_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT);
        verify(productVariantRepository, never()).save(any());
    }

    /**
     * A duplicate {@code sku} is an enveloped 409.
     */
    @Test
    void createVariantWithDuplicateSkuThrowsConflict() {
        Product product = product(1L, "100.00");
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        when(productVariantRepository.existsByProductIdAndSize(1L, "42")).thenReturn(false);
        when(productVariantRepository.existsBySku("DUP")).thenReturn(true);
        CreateProductVariantRequest request = new CreateProductVariantRequest("42", 5, "DUP",
                null, ProductVariantStatus.ACTIVE);

        assertThatThrownBy(() -> service.createVariant(1L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_SKU_DUPLICATED");
        verify(productVariantRepository, never()).save(any());
    }

    // ----- Update -----

    /**
     * The happy path applies every field to the existing variant.
     */
    @Test
    void updateVariantAppliesChanges() {
        Product product = product(1L, "100.00");
        ProductVariant existing = variant(7L, product, "41", "OLD-SKU", 2, null, ProductVariantStatus.INACTIVE);
        ProductVariantResponse mapped = new ProductVariantResponse(7L, "42", new BigDecimal("100.00"),
                9, ProductVariantStatus.ACTIVE, "NEW-SKU");
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(productVariantRepository.existsByProductIdAndSize(1L, "42")).thenReturn(false);
        when(productVariantRepository.existsBySku("NEW-SKU")).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productVariantMapper.toResponse(existing)).thenReturn(mapped);
        UpdateProductVariantRequest request = new UpdateProductVariantRequest("42", 9, "NEW-SKU",
                null, ProductVariantStatus.ACTIVE);

        ProductVariantResponse result = service.updateVariant(1L, 7L, request);

        assertThat(result).isEqualTo(mapped);
        assertThat(existing.getSize()).isEqualTo("42");
        assertThat(existing.getStockQuantity()).isEqualTo(9);
        assertThat(existing.getSku()).isEqualTo("NEW-SKU");
        assertThat(existing.getStatus()).isEqualTo(ProductVariantStatus.ACTIVE);
        assertThat(existing.getPriceOverride()).isNull();
    }

    /**
     * Updating a variant that does not belong to the given product is a 404.
     */
    @Test
    void updateVariantNotBelongingToProductThrowsNotFound() {
        Product other = product(2L, "100.00");
        ProductVariant existing = variant(7L, other, "41", "SKU", 2, null, ProductVariantStatus.ACTIVE);
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(existing));
        UpdateProductVariantRequest request = new UpdateProductVariantRequest("41", 2, "SKU",
                null, ProductVariantStatus.ACTIVE);

        assertThatThrownBy(() -> service.updateVariant(1L, 7L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_NOT_FOUND");
        verify(productVariantRepository, never()).save(any());
    }

    /**
     * Updating a missing variant is a 404.
     */
    @Test
    void updateMissingVariantThrowsNotFound() {
        when(productVariantRepository.findById(7L)).thenReturn(Optional.empty());
        UpdateProductVariantRequest request = new UpdateProductVariantRequest("41", 2, "SKU",
                null, ProductVariantStatus.ACTIVE);

        assertThatThrownBy(() -> service.updateVariant(1L, 7L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_NOT_FOUND");
    }

    /**
     * Changing size to one already used by the product is an enveloped 409.
     */
    @Test
    void updateVariantToDuplicateSizeThrowsConflict() {
        Product product = product(1L, "100.00");
        ProductVariant existing = variant(7L, product, "41", "SKU", 2, null, ProductVariantStatus.ACTIVE);
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(productVariantRepository.existsByProductIdAndSize(1L, "42")).thenReturn(true);
        UpdateProductVariantRequest request = new UpdateProductVariantRequest("42", 2, "SKU",
                null, ProductVariantStatus.ACTIVE);

        assertThatThrownBy(() -> service.updateVariant(1L, 7L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_SIZE_DUPLICATED");
        verify(productVariantRepository, never()).save(any());
    }

    /**
     * Changing the SKU to one another variant already carries is an enveloped 409. The size is left
     * untouched, so the conflict can only come from the SKU uniqueness check.
     */
    @Test
    void updateVariantToDuplicateSkuThrowsConflict() {
        Product product = product(1L, "100.00");
        ProductVariant sibling = variant(8L, product, "42", "SKU-A", 3, null, ProductVariantStatus.ACTIVE);
        ProductVariant target = variant(7L, product, "41", "SKU-B", 2, null, ProductVariantStatus.ACTIVE);
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(target));
        when(productVariantRepository.existsBySku(sibling.getSku())).thenReturn(true);
        UpdateProductVariantRequest request = new UpdateProductVariantRequest("41", 2, sibling.getSku(),
                null, ProductVariantStatus.ACTIVE);

        assertThatThrownBy(() -> service.updateVariant(1L, 7L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_SKU_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT);
        assertThat(target.getSku()).isEqualTo("SKU-B");
        verify(productVariantRepository, never()).save(any());
    }

    /**
     * Keeping the same size and SKU skips the uniqueness checks entirely.
     */
    @Test
    void updateVariantKeepingSameSizeAndSkuSkipsUniquenessChecks() {
        Product product = product(1L, "100.00");
        ProductVariant existing = variant(7L, product, "41", "SKU", 2, null, ProductVariantStatus.ACTIVE);
        when(productVariantRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productVariantMapper.toResponse(existing)).thenReturn(new ProductVariantResponse(7L, "41",
                new BigDecimal("100.00"), 8, ProductVariantStatus.ACTIVE, "SKU"));
        UpdateProductVariantRequest request = new UpdateProductVariantRequest("41", 8, "SKU",
                null, ProductVariantStatus.ACTIVE);

        service.updateVariant(1L, 7L, request);

        verify(productVariantRepository, never()).existsByProductIdAndSize(anyLong(), anyString());
        verify(productVariantRepository, never()).existsBySku(anyString());
    }

    // ----- Bean Validation guards (negative stock, required status, blanks) -----

    /**
     * Negative stock is rejected at validation, not by a business exception.
     */
    @Test
    void negativeStockFailsBeanValidation() {
        CreateProductVariantRequest request = new CreateProductVariantRequest("42", -1, "SKU",
                null, ProductVariantStatus.ACTIVE);

        Set<ConstraintViolation<CreateProductVariantRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stockQuantity"));
    }

    /**
     * A missing stock quantity is rejected (required).
     */
    @Test
    void missingStockQuantityFailsBeanValidation() {
        CreateProductVariantRequest request = new CreateProductVariantRequest("42", null, "SKU",
                null, ProductVariantStatus.ACTIVE);

        Set<ConstraintViolation<CreateProductVariantRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("stockQuantity"));
    }

    /**
     * A missing status is rejected (required).
     */
    @Test
    void missingStatusFailsBeanValidation() {
        CreateProductVariantRequest request = new CreateProductVariantRequest("42", 5, "SKU", null, null);

        Set<ConstraintViolation<CreateProductVariantRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("status"));
    }

    /**
     * Blank size and SKU are rejected (required).
     */
    @Test
    void blankSizeAndSkuFailBeanValidation() {
        CreateProductVariantRequest request = new CreateProductVariantRequest(" ", 5, " ",
                null, ProductVariantStatus.ACTIVE);

        Set<ConstraintViolation<CreateProductVariantRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("size"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("sku"));
    }

    /**
     * A non-positive price override is rejected ({@code @Positive} disallows zero and negatives).
     */
    @Test
    void nonPositivePriceOverrideFailsBeanValidation() {
        CreateProductVariantRequest request = new CreateProductVariantRequest("42", 5, "SKU",
                BigDecimal.ZERO, ProductVariantStatus.ACTIVE);

        Set<ConstraintViolation<CreateProductVariantRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("priceOverride"));
    }
}
