package com.footverse.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.footverse.address.dto.AddressResponse;
import com.footverse.address.service.AddressService;
import com.footverse.cart.dto.CheckoutCartLine;
import com.footverse.cart.service.CartService;
import com.footverse.common.dto.PageResponse;
import com.footverse.common.exception.BusinessException;
import com.footverse.common.exception.DuplicateResourceException;
import com.footverse.common.exception.ResourceNotFoundException;
import com.footverse.common.security.CurrentUserProvider;
import com.footverse.order.dto.CouponPreviewRequest;
import com.footverse.order.dto.CouponPreviewResponse;
import com.footverse.order.dto.CouponResponse;
import com.footverse.order.dto.CreateCouponRequest;
import com.footverse.order.dto.OrderDetailResponse;
import com.footverse.order.dto.OrderItemResponse;
import com.footverse.order.dto.OrderSummaryResponse;
import com.footverse.order.dto.PlaceOrderRequest;
import com.footverse.order.dto.UpdateCouponRequest;
import com.footverse.order.dto.UpdateOrderStatusRequest;
import com.footverse.order.entity.Coupon;
import com.footverse.order.entity.DiscountType;
import com.footverse.order.entity.Order;
import com.footverse.order.entity.OrderItem;
import com.footverse.order.entity.OrderStatus;
import com.footverse.order.entity.PaymentMethod;
import com.footverse.order.entity.PaymentStatus;
import com.footverse.order.mapper.CouponMapper;
import com.footverse.order.mapper.OrderMapper;
import com.footverse.order.repository.CouponRepository;
import com.footverse.order.repository.OrderItemRepository;
import com.footverse.order.repository.OrderRepository;
import com.footverse.product.dto.ProductVariantPurchaseSnapshot;
import com.footverse.product.service.ProductVariantService;
import com.footverse.user.entity.User;

/**
 * Unit tests for {@link OrderServiceImpl}, the single service of the {@code order} module. They cover
 * the admin coupon CRUD, the read-only checkout preview ({@code previewCoupon}) — server-computed
 * pricing, the coupon-validation matrix, the {@code PERCENT}/{@code FIXED} discount computation and
 * cap, and its no-mutation guarantee — the transactional checkout ({@code placeOrder}) composition /
 * rollback / order-code retry / coupon consumption, the caller-scoped order queries, the customer
 * cancellation compensation, and the admin order-status machine. Collaborators are mocked, so the
 * {@code @Transactional} rollback itself is exercised by the integration test; here the "no partial
 * write" guarantee is proven by asserting the downstream writes never run once an earlier step fails.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Long CART_ITEM_ID = 10L;
    private static final Long VARIANT_ID = 7L;
    private static final Long ADDRESS_ID = 3L;
    private static final BigDecimal SHIPPING_FEE = new BigDecimal("30000.00");

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponMapper couponMapper;

    @Mock
    private CartService cartService;

    @Mock
    private ProductVariantService productVariantService;

    @Mock
    private AddressService addressService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private OrderServiceImpl service;

    private void init() {
        service = new OrderServiceImpl(couponRepository, couponMapper, cartService, productVariantService,
                addressService, orderRepository, orderItemRepository, orderMapper, currentUserProvider);
    }

    private CouponPreviewRequest request(String code) {
        return new CouponPreviewRequest(code, List.of(CART_ITEM_ID));
    }

    /**
     * Stubs the selected cart line ({@code quantity}) and its variant snapshot ({@code unitPrice}) so
     * the subtotal is {@code unitPrice × quantity}.
     */
    private void withLine(int quantity, String unitPrice) {
        when(cartService.resolvePreviewItems(List.of(CART_ITEM_ID)))
                .thenReturn(List.of(new CheckoutCartLine(CART_ITEM_ID, VARIANT_ID, quantity)));
        when(productVariantService.getPurchaseSnapshot(VARIANT_ID)).thenReturn(
                new ProductVariantPurchaseSnapshot(VARIANT_ID, 100L, "Air Force 1", "img.png", "Black", "42",
                        new BigDecimal(unitPrice), 50, true));
    }

    private Coupon coupon(DiscountType type, String discountValue, String minOrder, String maxDiscount,
            boolean enabled, LocalDateTime start, LocalDateTime end, Integer usageLimit, int usedCount) {
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE");
        coupon.setName("Save Now");
        coupon.setDiscountType(type);
        coupon.setDiscountValue(new BigDecimal(discountValue));
        coupon.setMinOrderAmount(new BigDecimal(minOrder));
        coupon.setMaxDiscountAmount(maxDiscount == null ? null : new BigDecimal(maxDiscount));
        coupon.setStartAt(start);
        coupon.setEndAt(end);
        coupon.setUsageLimit(usageLimit);
        coupon.setUsedCount(usedCount);
        coupon.setEnabled(enabled);
        return coupon;
    }

    private Coupon activeCoupon(DiscountType type, String discountValue, String minOrder, String maxDiscount) {
        return coupon(type, discountValue, minOrder, maxDiscount, true,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), null, 0);
    }

    private void assertNoMutation() {
        verify(couponRepository, never()).save(any());
        verify(cartService, never()).resolveCheckoutItems(any());
        verify(cartService, never()).removeCheckedOutItems(any());
        verify(productVariantService, never()).decrementStock(any());
    }

    /**
     * A preview without a coupon returns the plain totals: {@code subtotal}, zero discount, the flat
     * shipping fee, and {@code total = subtotal + shippingFee}; {@code code} / {@code name} are null.
     */
    @Test
    void previewWithoutCouponReturnsPlainTotals() {
        init();
        withLine(2, "100.00");

        CouponPreviewResponse response = service.previewCoupon(request(null));

        assertThat(response.code()).isNull();
        assertThat(response.name()).isNull();
        assertThat(response.subtotal()).isEqualByComparingTo("200.00");
        assertThat(response.discountAmount()).isEqualByComparingTo("0");
        assertThat(response.shippingFee()).isEqualByComparingTo(SHIPPING_FEE);
        assertThat(response.total()).isEqualByComparingTo("30200.00");
        verify(cartService).resolvePreviewItems(List.of(CART_ITEM_ID));
        verify(couponRepository, never()).findByCode(any());
        assertNoMutation();
    }

    /**
     * A blank coupon code is treated as no coupon: no lookup, plain totals.
     */
    @Test
    void previewWithBlankCodeIsTreatedAsNoCoupon() {
        init();
        withLine(1, "150.00");

        CouponPreviewResponse response = service.previewCoupon(request("   "));

        assertThat(response.code()).isNull();
        assertThat(response.discountAmount()).isEqualByComparingTo("0");
        assertThat(response.total()).isEqualByComparingTo("30150.00");
        verify(couponRepository, never()).findByCode(any());
    }

    /**
     * A valid PERCENT coupon returns its code/name and a discount of {@code subtotal × percent}.
     */
    @Test
    void previewWithValidPercentCouponComputesDiscount() {
        init();
        withLine(2, "100.00");
        when(couponRepository.findByCode("SAVE"))
                .thenReturn(Optional.of(activeCoupon(DiscountType.PERCENT, "10", "0", null)));

        CouponPreviewResponse response = service.previewCoupon(request("SAVE"));

        assertThat(response.code()).isEqualTo("SAVE");
        assertThat(response.name()).isEqualTo("Save Now");
        assertThat(response.subtotal()).isEqualByComparingTo("200.00");
        assertThat(response.discountAmount()).isEqualByComparingTo("20.00");
        assertThat(response.total()).isEqualByComparingTo("30180.00");
        assertNoMutation();
    }

    /**
     * A PERCENT coupon's discount is capped by {@code maxDiscountAmount} when the raw percentage
     * exceeds it.
     */
    @Test
    void previewWithPercentCouponAppliesMaxDiscountCap() {
        init();
        withLine(2, "100.00");
        when(couponRepository.findByCode("SAVE"))
                .thenReturn(Optional.of(activeCoupon(DiscountType.PERCENT, "10", "0", "15")));

        CouponPreviewResponse response = service.previewCoupon(request("SAVE"));

        assertThat(response.discountAmount()).isEqualByComparingTo("15.00");
        assertThat(response.total()).isEqualByComparingTo("30185.00");
    }

    /**
     * A valid FIXED coupon discounts exactly its {@code discountValue}.
     */
    @Test
    void previewWithValidFixedCouponComputesDiscount() {
        init();
        withLine(2, "100.00");
        when(couponRepository.findByCode("SAVE"))
                .thenReturn(Optional.of(activeCoupon(DiscountType.FIXED, "50", "0", null)));

        CouponPreviewResponse response = service.previewCoupon(request("SAVE"));

        assertThat(response.discountAmount()).isEqualByComparingTo("50");
        assertThat(response.total()).isEqualByComparingTo("30150.00");
    }

    /**
     * A FIXED discount larger than the subtotal is clamped to the subtotal, so the total floors at
     * the shipping fee and never goes negative (project decision — the spec bounds neither
     * {@code discountValue} nor the percentage against the subtotal).
     */
    @Test
    void previewClampsFixedDiscountToSubtotal() {
        init();
        withLine(2, "100.00"); // subtotal 200
        when(couponRepository.findByCode("SAVE"))
                .thenReturn(Optional.of(activeCoupon(DiscountType.FIXED, "500", "0", null)));

        CouponPreviewResponse response = service.previewCoupon(request("SAVE"));

        assertThat(response.discountAmount()).isEqualByComparingTo("200.00");
        assertThat(response.total()).isEqualByComparingTo(SHIPPING_FEE);
    }

    /**
     * A percentage over 100 is likewise clamped to the subtotal before the total is computed.
     */
    @Test
    void previewClampsOverHundredPercentDiscountToSubtotal() {
        init();
        withLine(2, "100.00"); // subtotal 200
        when(couponRepository.findByCode("SAVE"))
                .thenReturn(Optional.of(activeCoupon(DiscountType.PERCENT, "200", "0", null)));

        CouponPreviewResponse response = service.previewCoupon(request("SAVE"));

        assertThat(response.discountAmount()).isEqualByComparingTo("200.00");
        assertThat(response.total()).isEqualByComparingTo(SHIPPING_FEE);
    }

    /**
     * An unknown coupon code reuses the existing {@code 404 COUPON_NOT_FOUND}.
     */
    @Test
    void previewWithUnknownCouponIsNotFound() {
        init();
        withLine(1, "100.00");
        when(couponRepository.findByCode("SAVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewCoupon(request("SAVE")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
        assertNoMutation();
    }

    /**
     * A disabled coupon is rejected with {@code 400 COUPON_DISABLED}.
     */
    @Test
    void previewWithDisabledCouponIsRejected() {
        init();
        withLine(1, "100.00");
        Coupon disabled = coupon(DiscountType.FIXED, "10", "0", null, false,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), null, 0);
        when(couponRepository.findByCode("SAVE")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.previewCoupon(request("SAVE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_DISABLED")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
        assertNoMutation();
    }

    /**
     * A coupon outside its {@code [startAt, endAt]} window is rejected with {@code 400 COUPON_EXPIRED}.
     */
    @Test
    void previewWithExpiredCouponIsRejected() {
        init();
        withLine(1, "100.00");
        Coupon expired = coupon(DiscountType.FIXED, "10", "0", null, true,
                LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(1), null, 0);
        when(couponRepository.findByCode("SAVE")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.previewCoupon(request("SAVE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_EXPIRED")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
    }

    /**
     * A coupon whose usage limit is reached is rejected with {@code 400 COUPON_USAGE_LIMIT_EXCEEDED}.
     */
    @Test
    void previewWithUsageLimitExceededIsRejected() {
        init();
        withLine(1, "100.00");
        Coupon exhausted = coupon(DiscountType.FIXED, "10", "0", null, true,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 5, 5);
        when(couponRepository.findByCode("SAVE")).thenReturn(Optional.of(exhausted));

        assertThatThrownBy(() -> service.previewCoupon(request("SAVE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_USAGE_LIMIT_EXCEEDED")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
    }

    /**
     * A subtotal below the coupon's minimum order amount is rejected with
     * {@code 400 COUPON_MIN_ORDER_AMOUNT_NOT_MET}.
     */
    @Test
    void previewWithSubtotalBelowMinimumIsRejected() {
        init();
        withLine(2, "100.00"); // subtotal 200
        when(couponRepository.findByCode("SAVE"))
                .thenReturn(Optional.of(activeCoupon(DiscountType.FIXED, "10", "500", null)));

        assertThatThrownBy(() -> service.previewCoupon(request("SAVE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_MIN_ORDER_AMOUNT_NOT_MET")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
        assertNoMutation();
    }

    /**
     * A cart item that belongs to another user surfaces the cart service's {@code 403
     * CART_ITEM_FORBIDDEN}; no coupon lookup or mutation happens.
     */
    @Test
    void previewWithForeignCartItemIsForbidden() {
        init();
        when(cartService.resolvePreviewItems(List.of(CART_ITEM_ID)))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "CART_ITEM_FORBIDDEN",
                        "You cannot access this cart item"));

        assertThatThrownBy(() -> service.previewCoupon(request("SAVE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CART_ITEM_FORBIDDEN")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.FORBIDDEN);
        verify(couponRepository, never()).findByCode(any());
        assertNoMutation();
    }

    /**
     * A cart item that does not exist surfaces the cart service's {@code 404 CART_ITEM_NOT_FOUND}.
     */
    @Test
    void previewWithUnknownCartItemIsNotFound() {
        init();
        when(cartService.resolvePreviewItems(List.of(CART_ITEM_ID)))
                .thenThrow(new ResourceNotFoundException("CART_ITEM_NOT_FOUND", "Cart item not found"));

        assertThatThrownBy(() -> service.previewCoupon(request(null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CART_ITEM_NOT_FOUND");
        assertNoMutation();
    }

    // ----- Customer order queries (getMyOrders / getMyOrder) -----

    private static final Long USER_ID = 1L;

    private User caller() {
        User user = new User();
        user.setId(USER_ID);
        return user;
    }

    private Order order(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setOrderCode("FV-ORDER-" + id);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setTotal(new BigDecimal("30200.00"));
        return order;
    }

    private OrderItem orderLine(Order order, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setQuantity(quantity);
        return item;
    }

    private OrderSummaryResponse summary(int itemCount) {
        return new OrderSummaryResponse(5L, "FV-ORDER-5", OrderStatus.PENDING, PaymentStatus.UNPAID,
                new BigDecimal("30200.00"), itemCount, LocalDateTime.now());
    }

    /**
     * The order history is caller-scoped: the list is read for the authenticated user's id resolved
     * through {@link CurrentUserProvider}, never a client-supplied id.
     */
    @Test
    void getMyOrdersReadsOnlyTheCallersOrders() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        Order order = order(5L);
        when(orderRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderItemRepository.findByOrderIdIn(List.of(5L)))
                .thenReturn(List.of(orderLine(order, 2), orderLine(order, 3)));
        when(orderMapper.toSummaryResponse(eq(order), anyInt())).thenReturn(summary(5));

        PageResponse<OrderSummaryResponse> page = service.getMyOrders(PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        verify(orderRepository).findByUserId(eq(USER_ID), any(Pageable.class));
    }

    /**
     * {@code itemCount} is the sum of the order-item quantities (Σ quantity), computed by the service
     * from a single batch read — not a stored column.
     */
    @Test
    void getMyOrdersComputesItemCountAsSumOfQuantities() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        Order order = order(5L);
        when(orderRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderItemRepository.findByOrderIdIn(List.of(5L)))
                .thenReturn(List.of(orderLine(order, 2), orderLine(order, 3)));
        when(orderMapper.toSummaryResponse(eq(order), anyInt())).thenReturn(summary(5));

        service.getMyOrders(PageRequest.of(0, 20));

        ArgumentCaptor<Integer> itemCount = ArgumentCaptor.forClass(Integer.class);
        verify(orderMapper).toSummaryResponse(eq(order), itemCount.capture());
        assertThat(itemCount.getValue()).isEqualTo(5);
    }

    /**
     * The list is always ordered {@code createdAt} descending: the service preserves the client's
     * page and size but overrides any client-supplied sort (assumption 3).
     */
    @Test
    void getMyOrdersForcesCreatedAtDescendingSort() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        when(orderRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getMyOrders(PageRequest.of(2, 15, Sort.by(Sort.Direction.ASC, "total")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findByUserId(eq(USER_ID), pageable.capture());
        Pageable used = pageable.getValue();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(15);
        Sort.Order createdAt = used.getSort().getOrderFor("createdAt");
        assertThat(createdAt).isNotNull();
        assertThat(createdAt.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(used.getSort().getOrderFor("total")).isNull();
    }

    /**
     * With no orders on the page, the service skips the item-count batch query entirely.
     */
    @Test
    void getMyOrdersWithNoOrdersSkipsItemCountQuery() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        when(orderRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<OrderSummaryResponse> page = service.getMyOrders(PageRequest.of(0, 20));

        assertThat(page.content()).isEmpty();
        verify(orderItemRepository, never()).findByOrderIdIn(any());
    }

    /**
     * The detail is assembled entirely from the persisted order and its item snapshots (including the
     * post-migration {@code color}); the service reads the caller-scoped order and maps its lines.
     */
    @Test
    void getMyOrderReturnsDetailAssembledFromSnapshots() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        Order order = order(9L);
        OrderItem line = orderLine(order, 2);
        when(orderRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(9L)).thenReturn(List.of(line));
        OrderItemResponse itemResponse = new OrderItemResponse(1L, 7L, "Air Force 1", "img.png", "Black",
                "42", new BigDecimal("100.00"), 2, new BigDecimal("200.00"));
        when(orderMapper.toResponse(line)).thenReturn(itemResponse);
        OrderDetailResponse detail = new OrderDetailResponse(9L, "FV-ORDER-9", OrderStatus.PENDING,
                PaymentMethod.COD, PaymentStatus.UNPAID, new BigDecimal("200.00"), BigDecimal.ZERO,
                new BigDecimal("30000.00"), new BigDecimal("30200.00"), null, "Jane", "0900000000", "HCM",
                "D1", "W1", "1 Street", null, List.of(itemResponse), LocalDateTime.now(), null, null);
        when(orderMapper.toDetailResponse(order, List.of(itemResponse))).thenReturn(detail);

        OrderDetailResponse result = service.getMyOrder(9L);

        assertThat(result).isSameAs(detail);
        assertThat(result.items()).singleElement()
                .satisfies(item -> assertThat(item.color()).isEqualTo("Black"));
        verify(orderMapper).toResponse(line);
    }

    /**
     * An order that exists but belongs to another user is the ownership {@code 403 ORDER_FORBIDDEN} —
     * never hidden behind a {@code 404} (address / cart precedent).
     */
    @Test
    void getMyOrderOfAnotherUserIsForbidden() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        when(orderRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.empty());
        when(orderRepository.existsById(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.getMyOrder(9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_FORBIDDEN")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.FORBIDDEN);
    }

    /**
     * An order that does not exist at all is the {@code 404 ORDER_NOT_FOUND}.
     */
    @Test
    void getMyOrderThatDoesNotExistIsNotFound() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        when(orderRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.empty());
        when(orderRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.getMyOrder(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
    }

    // ----- Checkout (placeOrder) -----

    private PlaceOrderRequest placeRequest(String couponCode) {
        return new PlaceOrderRequest(List.of(CART_ITEM_ID), ADDRESS_ID, couponCode, "note");
    }

    private AddressResponse address() {
        return new AddressResponse(ADDRESS_ID, "Jane", "0900000000", "HCM", "D1", "W1", "1 Street", true);
    }

    private OrderItemResponse itemResponse() {
        return new OrderItemResponse(1L, VARIANT_ID, "Air Force 1", "img.png", "Black", "42",
                new BigDecimal("100.00"), 2, new BigDecimal("200.00"));
    }

    private OrderDetailResponse detailResponse() {
        return new OrderDetailResponse(9L, "FV-ORDER-9", OrderStatus.PENDING, PaymentMethod.COD,
                PaymentStatus.UNPAID, new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("30000.00"),
                new BigDecimal("30200.00"), null, "Jane", "0900000000", "HCM", "D1", "W1", "1 Street",
                null, List.of(itemResponse()), LocalDateTime.now(), null, null);
    }

    /** Stubs the checkout cart resolution (locked read) and its variant snapshot. */
    private void withCheckoutLine(int quantity, String unitPrice) {
        when(cartService.resolveCheckoutItems(List.of(CART_ITEM_ID)))
                .thenReturn(List.of(new CheckoutCartLine(CART_ITEM_ID, VARIANT_ID, quantity)));
        when(productVariantService.getPurchaseSnapshot(VARIANT_ID)).thenReturn(
                new ProductVariantPurchaseSnapshot(VARIANT_ID, 100L, "Air Force 1", "img.png", "Black", "42",
                        new BigDecimal(unitPrice), 50, true));
    }

    /** Stubs the persistence collaborators of a successful checkout (order flush, item save, mapping). */
    private void withSuccessfulPersistence() {
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any(OrderItem.class))).thenReturn(itemResponse());
        when(orderMapper.toDetailResponse(any(Order.class), anyList())).thenReturn(detailResponse());
    }

    /**
     * A checkout without a coupon builds a {@code PENDING}/{@code COD}/{@code UNPAID} order snapshotting
     * the address and the server-computed money, decrements exactly the demanded stock, stamps a
     * timestamp order code, removes only the selected cart lines (partial checkout), and never touches a
     * coupon.
     */
    @Test
    void placeOrderWithoutCouponBuildsPendingCodOrderAndClearsSelectedLines() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        withCheckoutLine(2, "100.00"); // subtotal 200.00
        when(addressService.getMyAddress(ADDRESS_ID)).thenReturn(address());
        withSuccessfulPersistence();

        OrderDetailResponse result = service.placeOrder(placeRequest(null));

        assertThat(result).isNotNull();
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(orderCaptor.capture());
        Order saved = orderCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(saved.getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(saved.getShippingFee()).isEqualByComparingTo(SHIPPING_FEE);
        assertThat(saved.getTotal()).isEqualByComparingTo("30200.00");
        assertThat(saved.getShippingRecipientName()).isEqualTo("Jane");
        assertThat(saved.getShippingProvince()).isEqualTo("HCM");
        assertThat(saved.getShippingStreetAddress()).isEqualTo("1 Street");
        assertThat(saved.getOrderCode()).startsWith("FV-");
        assertThat(saved.getCoupon()).isNull();
        verify(productVariantService).decrementStock(Map.of(VARIANT_ID, 2));
        verify(cartService).removeCheckedOutItems(List.of(CART_ITEM_ID));
        verify(couponRepository, never()).save(any());
    }

    /**
     * A checkout item snapshots every display field from the variant purchase snapshot (color / size /
     * image / unit price) and its line total onto the persisted {@code order_item}.
     */
    @Test
    void placeOrderSnapshotsItemFieldsFromTheVariantSnapshot() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        withCheckoutLine(2, "100.00");
        when(addressService.getMyAddress(ADDRESS_ID)).thenReturn(address());
        withSuccessfulPersistence();

        service.placeOrder(placeRequest(null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).singleElement().satisfies(item -> {
            assertThat(item.getProductVariantId()).isEqualTo(VARIANT_ID);
            assertThat(item.getProductName()).isEqualTo("Air Force 1");
            assertThat(item.getColor()).isEqualTo("Black");
            assertThat(item.getSize()).isEqualTo("42");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("100.00");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getLineTotal()).isEqualByComparingTo("200.00");
        });
    }

    /**
     * A checkout that applies a coupon consumes it exactly once ({@code usedCount + 1}) inside the same
     * transaction, and stamps the computed discount and the coupon reference onto the order.
     */
    @Test
    void placeOrderWithCouponIncrementsUsedCountByOne() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        withCheckoutLine(2, "100.00");
        when(addressService.getMyAddress(ADDRESS_ID)).thenReturn(address());
        Coupon coupon = activeCoupon(DiscountType.FIXED, "50", "0", null);
        coupon.setUsedCount(3);
        when(couponRepository.findByCode("SAVE")).thenReturn(Optional.of(coupon));
        withSuccessfulPersistence();

        service.placeOrder(placeRequest("SAVE"));

        assertThat(coupon.getUsedCount()).isEqualTo(4);
        verify(couponRepository).save(coupon);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getCoupon()).isSameAs(coupon);
        assertThat(orderCaptor.getValue().getDiscountAmount()).isEqualByComparingTo("50");
        assertThat(orderCaptor.getValue().getTotal()).isEqualByComparingTo("30150.00");
    }

    /**
     * When the stock decrement fails, the whole checkout aborts with no partial write: no order is
     * flushed, no items are saved, no cart line is removed, and no coupon is consumed.
     */
    @Test
    void placeOrderAbortsWithoutSideEffectsWhenStockIsInsufficient() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        withCheckoutLine(2, "100.00");
        when(addressService.getMyAddress(ADDRESS_ID)).thenReturn(address());
        doThrow(new BusinessException(HttpStatus.BAD_REQUEST, "PRODUCT_VARIANT_INSUFFICIENT_STOCK",
                "Not enough stock")).when(productVariantService).decrementStock(any());

        assertThatThrownBy(() -> service.placeOrder(placeRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PRODUCT_VARIANT_INSUFFICIENT_STOCK");

        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderItemRepository, never()).saveAll(anyList());
        verify(cartService, never()).removeCheckedOutItems(any());
        verify(couponRepository, never()).save(any());
    }

    /**
     * A cart line owned by another user surfaces the cart service's {@code 403 CART_ITEM_FORBIDDEN}
     * before any stock or order write happens.
     */
    @Test
    void placeOrderWithForeignCartItemPropagatesForbiddenAndWritesNothing() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        when(cartService.resolveCheckoutItems(List.of(CART_ITEM_ID)))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "CART_ITEM_FORBIDDEN",
                        "You cannot access this cart item"));

        assertThatThrownBy(() -> service.placeOrder(placeRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CART_ITEM_FORBIDDEN");

        verify(productVariantService, never()).decrementStock(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    /**
     * An address owned by another user surfaces the address service's {@code 403 ADDRESS_FORBIDDEN}
     * before any stock or order write happens.
     */
    @Test
    void placeOrderWithForeignAddressPropagatesForbidden() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        when(cartService.resolveCheckoutItems(List.of(CART_ITEM_ID)))
                .thenReturn(List.of(new CheckoutCartLine(CART_ITEM_ID, VARIANT_ID, 2)));
        when(addressService.getMyAddress(ADDRESS_ID))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "ADDRESS_FORBIDDEN",
                        "You cannot access this address"));

        assertThatThrownBy(() -> service.placeOrder(placeRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ADDRESS_FORBIDDEN");

        verify(productVariantService, never()).decrementStock(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    /**
     * A same-millisecond order-code collision surfaces as a {@link DataIntegrityViolationException} and
     * is retried with a fresh code within the bounded budget; the checkout then succeeds.
     */
    @Test
    void placeOrderRetriesOrderCodeOnUniqueCollisionThenSucceeds() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        withCheckoutLine(1, "100.00");
        when(addressService.getMyAddress(ADDRESS_ID)).thenReturn(address());
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate order code"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any(OrderItem.class))).thenReturn(itemResponse());
        when(orderMapper.toDetailResponse(any(Order.class), anyList())).thenReturn(detailResponse());

        OrderDetailResponse result = service.placeOrder(placeRequest(null));

        assertThat(result).isNotNull();
        verify(orderRepository, times(2)).saveAndFlush(any(Order.class));
        verify(cartService).removeCheckedOutItems(List.of(CART_ITEM_ID));
    }

    /**
     * When every attempt collides, the checkout fails with {@code 500 ORDER_CODE_GENERATION_FAILED}
     * after exhausting the bounded retry, and nothing downstream (items, cart cleanup) runs.
     */
    @Test
    void placeOrderFailsAfterExhaustingOrderCodeAttempts() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        withCheckoutLine(1, "100.00");
        when(addressService.getMyAddress(ADDRESS_ID)).thenReturn(address());
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate order code"));

        assertThatThrownBy(() -> service.placeOrder(placeRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_CODE_GENERATION_FAILED")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.INTERNAL_SERVER_ERROR);

        verify(orderRepository, times(5)).saveAndFlush(any(Order.class));
        verify(orderItemRepository, never()).saveAll(anyList());
        verify(cartService, never()).removeCheckedOutItems(any());
    }

    // ----- Customer cancellation (cancelMyOrder) -----

    private OrderItem orderLineWithVariant(Order order, Long variantId, int quantity) {
        OrderItem item = orderLine(order, quantity);
        item.setProductVariantId(variantId);
        return item;
    }

    private Order pendingOrderOwnedByCaller(Long id, Coupon coupon) {
        Order order = order(id); // PENDING / COD / UNPAID
        order.setCoupon(coupon);
        when(orderRepository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.of(order));
        return order;
    }

    /**
     * Cancelling a {@code PENDING} order with a coupon restores every item's stock, records
     * {@code cancelledAt}, decrements the coupon's {@code usedCount} exactly once, and leaves the
     * payment {@code UNPAID}.
     */
    @Test
    void cancelMyOrderRestoresStockAndDecrementsUsedCount() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        Coupon coupon = activeCoupon(DiscountType.FIXED, "50", "0", null);
        coupon.setUsedCount(4);
        Order order = pendingOrderOwnedByCaller(9L, coupon);
        OrderItem line = orderLineWithVariant(order, VARIANT_ID, 3);
        when(orderItemRepository.findByOrderId(9L)).thenReturn(List.of(line));
        when(orderMapper.toResponse(line)).thenReturn(itemResponse());
        when(orderMapper.toDetailResponse(eq(order), anyList())).thenReturn(detailResponse());

        service.cancelMyOrder(9L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(coupon.getUsedCount()).isEqualTo(3);
        verify(productVariantService).restoreStock(Map.of(VARIANT_ID, 3));
        verify(couponRepository).save(coupon);
        verify(orderRepository).save(order);
    }

    /**
     * Cancelling a {@code PENDING} order that has no coupon restores stock but never touches a coupon.
     */
    @Test
    void cancelMyOrderWithoutCouponRestoresStockOnly() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        Order order = pendingOrderOwnedByCaller(9L, null);
        OrderItem line = orderLineWithVariant(order, VARIANT_ID, 2);
        when(orderItemRepository.findByOrderId(9L)).thenReturn(List.of(line));
        when(orderMapper.toResponse(line)).thenReturn(itemResponse());
        when(orderMapper.toDetailResponse(eq(order), anyList())).thenReturn(detailResponse());

        service.cancelMyOrder(9L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productVariantService).restoreStock(Map.of(VARIANT_ID, 2));
        verify(couponRepository, never()).save(any());
    }

    /**
     * The coupon {@code usedCount} decrement is floored at zero, so a defensive cancellation can never
     * drive it negative (database-spec §18).
     */
    @Test
    void cancelMyOrderNeverDrivesUsedCountNegative() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        Coupon coupon = activeCoupon(DiscountType.FIXED, "50", "0", null);
        coupon.setUsedCount(0);
        Order order = pendingOrderOwnedByCaller(9L, coupon);
        OrderItem line = orderLineWithVariant(order, VARIANT_ID, 1);
        when(orderItemRepository.findByOrderId(9L)).thenReturn(List.of(line));
        when(orderMapper.toResponse(line)).thenReturn(itemResponse());
        when(orderMapper.toDetailResponse(eq(order), anyList())).thenReturn(detailResponse());

        service.cancelMyOrder(9L);

        assertThat(coupon.getUsedCount()).isEqualTo(0);
    }

    /**
     * Cancelling a non-{@code PENDING} order is the {@code 409 ORDER_NOT_CANCELLABLE} and mutates
     * nothing — the item read and every compensation write are skipped.
     */
    @Test
    void cancelMyOrderThatIsNotPendingIsConflictAndChangesNothing() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        Order order = order(9L);
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelMyOrder(9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_NOT_CANCELLABLE")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT);

        verify(orderItemRepository, never()).findByOrderId(any());
        verify(productVariantService, never()).restoreStock(any());
        verify(orderRepository, never()).save(any());
    }

    /**
     * Cancelling an order that belongs to another user is the ownership {@code 403 ORDER_FORBIDDEN}.
     */
    @Test
    void cancelMyOrderOfAnotherUserIsForbidden() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        when(orderRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.empty());
        when(orderRepository.existsById(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.cancelMyOrder(9L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_FORBIDDEN")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.FORBIDDEN);

        verify(productVariantService, never()).restoreStock(any());
    }

    /**
     * Cancelling an order that does not exist is the {@code 404 ORDER_NOT_FOUND}.
     */
    @Test
    void cancelMyOrderThatDoesNotExistIsNotFound() {
        init();
        when(currentUserProvider.getCurrentUser()).thenReturn(caller());
        when(orderRepository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.empty());
        when(orderRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.cancelMyOrder(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_NOT_FOUND");
    }

    // ----- Admin status machine (updateOrderStatus) -----

    private UpdateOrderStatusRequest statusRequest(OrderStatus status) {
        return new UpdateOrderStatusRequest(status);
    }

    private Order adminOrder(Long id, OrderStatus status) {
        Order order = order(id);
        order.setStatus(status);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));
        return order;
    }

    private void withStatusResponseMapping(Order order, OrderItem line) {
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of(line));
        when(orderMapper.toResponse(line)).thenReturn(itemResponse());
        when(orderMapper.toDetailResponse(eq(order), anyList())).thenReturn(detailResponse());
    }

    /**
     * A legal {@code PENDING → CONFIRMED} transition advances the status and leaves the payment
     * {@code UNPAID} with no delivery timestamp.
     */
    @Test
    void updateOrderStatusPendingToConfirmedAdvancesStatus() {
        init();
        Order order = adminOrder(9L, OrderStatus.PENDING);
        withStatusResponseMapping(order, orderLine(order, 2));

        service.updateOrderStatus(9L, statusRequest(OrderStatus.CONFIRMED));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getDeliveredAt()).isNull();
        verify(orderRepository).save(order);
    }

    /**
     * A legal {@code CONFIRMED → SHIPPING} transition advances the status.
     */
    @Test
    void updateOrderStatusConfirmedToShippingAdvancesStatus() {
        init();
        Order order = adminOrder(9L, OrderStatus.CONFIRMED);
        withStatusResponseMapping(order, orderLine(order, 2));

        service.updateOrderStatus(9L, statusRequest(OrderStatus.SHIPPING));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        verify(orderRepository).save(order);
    }

    /**
     * Marking a {@code SHIPPING} order {@code DELIVERED} flips the payment to {@code PAID} and records
     * {@code deliveredAt} (business-rules → Payment).
     */
    @Test
    void updateOrderStatusShippingToDeliveredMarksPaidAndDelivered() {
        init();
        Order order = adminOrder(9L, OrderStatus.SHIPPING);
        withStatusResponseMapping(order, orderLine(order, 2));

        service.updateOrderStatus(9L, statusRequest(OrderStatus.DELIVERED));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getDeliveredAt()).isNotNull();
        verify(orderRepository).save(order);
    }

    /**
     * An admin {@code PENDING → CANCELLED} runs the same compensation path as a customer cancellation
     * — stock restore and coupon {@code usedCount} decrement — not a second flow.
     */
    @Test
    void updateOrderStatusPendingToCancelledRunsTheSharedCompensation() {
        init();
        Coupon coupon = activeCoupon(DiscountType.FIXED, "50", "0", null);
        coupon.setUsedCount(2);
        Order order = adminOrder(9L, OrderStatus.PENDING);
        order.setCoupon(coupon);
        withStatusResponseMapping(order, orderLineWithVariant(order, VARIANT_ID, 3));

        service.updateOrderStatus(9L, statusRequest(OrderStatus.CANCELLED));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(coupon.getUsedCount()).isEqualTo(1);
        verify(productVariantService).restoreStock(Map.of(VARIANT_ID, 3));
        verify(couponRepository).save(coupon);
    }

    /**
     * Trying to cancel a non-{@code PENDING} order via the admin status update is the
     * {@code 409 ORDER_NOT_CANCELLABLE}, reusing the cancel-specific code and mutating nothing.
     */
    @Test
    void updateOrderStatusToCancelledFromNonPendingIsNotCancellable() {
        init();
        Order order = adminOrder(9L, OrderStatus.SHIPPING);

        assertThatThrownBy(() -> service.updateOrderStatus(9L, statusRequest(OrderStatus.CANCELLED)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_NOT_CANCELLABLE")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT);

        verify(productVariantService, never()).restoreStock(any());
        verify(orderRepository, never()).save(any());
    }

    /**
     * A forbidden non-cancel transition (a skip, a step backward, or a step from a terminal state) is
     * the {@code 409 ORDER_INVALID_STATUS_TRANSITION} and mutates nothing.
     */
    @Test
    void updateOrderStatusWithForbiddenTransitionIsInvalidStatusTransition() {
        init();
        Order order = adminOrder(9L, OrderStatus.PENDING);

        assertThatThrownBy(() -> service.updateOrderStatus(9L, statusRequest(OrderStatus.DELIVERED)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_INVALID_STATUS_TRANSITION")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.CONFLICT);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
    }

    /**
     * A status update for an unknown order id is the {@code 404 ORDER_NOT_FOUND}; the admin path
     * bypasses ownership entirely.
     */
    @Test
    void updateOrderStatusForUnknownOrderIsNotFound() {
        init();
        when(orderRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateOrderStatus(9L, statusRequest(OrderStatus.CONFIRMED)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ORDER_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);
    }

    // ----- Admin coupon CRUD (getCoupons / createCoupon / updateCoupon) -----

    private CouponResponse couponResponse() {
        return new CouponResponse(1L, "SAVE", "Save Now", null, DiscountType.FIXED, new BigDecimal("50"),
                new BigDecimal("0"), null, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                null, 0, true);
    }

    private CreateCouponRequest createCouponRequest(LocalDateTime start, LocalDateTime end) {
        return new CreateCouponRequest("SAVE", "Save Now", null, DiscountType.FIXED, new BigDecimal("50"),
                new BigDecimal("0"), null, start, end, null, true);
    }

    private CreateCouponRequest createCouponRequest() {
        return createCouponRequest(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
    }

    private UpdateCouponRequest updateCouponRequest(String code, LocalDateTime start, LocalDateTime end) {
        return new UpdateCouponRequest(code, "Save More", null, DiscountType.FIXED, new BigDecimal("60"),
                new BigDecimal("0"), null, start, end, null, true);
    }

    private UpdateCouponRequest updateCouponRequest(String code) {
        return updateCouponRequest(code, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
    }

    /**
     * The coupon listing maps each persisted coupon to its response through the mapper.
     */
    @Test
    void getCouponsReturnsMappedPage() {
        init();
        Coupon coupon = activeCoupon(DiscountType.FIXED, "50", "0", null);
        when(couponRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(coupon)));
        CouponResponse response = couponResponse();
        when(couponMapper.toResponse(coupon)).thenReturn(response);

        PageResponse<CouponResponse> page = service.getCoupons(PageRequest.of(0, 20));

        assertThat(page.content()).containsExactly(response);
    }

    /**
     * Creating a coupon with a free code persists every field and returns the mapped response.
     */
    @Test
    void createCouponPersistsAndReturnsMapped() {
        init();
        when(couponRepository.existsByCode("SAVE")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(couponMapper.toResponse(any(Coupon.class))).thenReturn(couponResponse());

        CouponResponse response = service.createCoupon(createCouponRequest());

        assertThat(response).isNotNull();
        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("SAVE");
        assertThat(captor.getValue().getDiscountType()).isEqualTo(DiscountType.FIXED);
        assertThat(captor.getValue().isEnabled()).isTrue();
    }

    /**
     * A duplicate coupon code is the {@code 409 COUPON_CODE_DUPLICATED}; nothing is persisted.
     */
    @Test
    void createCouponWithDuplicateCodeIsConflict() {
        init();
        when(couponRepository.existsByCode("SAVE")).thenReturn(true);

        assertThatThrownBy(() -> service.createCoupon(createCouponRequest()))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_CODE_DUPLICATED");

        verify(couponRepository, never()).save(any());
    }

    /**
     * A validity window whose {@code endAt} is not after {@code startAt} is the
     * {@code 400 COUPON_INVALID_DATE_RANGE}; nothing is persisted.
     */
    @Test
    void createCouponWithEndBeforeStartIsRejected() {
        init();
        when(couponRepository.existsByCode("SAVE")).thenReturn(false);
        LocalDateTime start = LocalDateTime.now();

        assertThatThrownBy(() -> service.createCoupon(createCouponRequest(start, start.minusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_INVALID_DATE_RANGE")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);

        verify(couponRepository, never()).save(any());
    }

    /**
     * Updating a coupon while keeping its own code skips the uniqueness check and persists the changes.
     */
    @Test
    void updateCouponWithSameCodePersistsChanges() {
        init();
        Coupon existing = activeCoupon(DiscountType.FIXED, "50", "0", null);
        existing.setCode("SAVE");
        when(couponRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(couponMapper.toResponse(any(Coupon.class))).thenReturn(couponResponse());

        service.updateCoupon(1L, updateCouponRequest("SAVE"));

        verify(couponRepository).save(existing);
        assertThat(existing.getName()).isEqualTo("Save More");
        assertThat(existing.getDiscountValue()).isEqualByComparingTo("60");
    }

    /**
     * Updating an unknown coupon id is the {@code 404 COUPON_NOT_FOUND}.
     */
    @Test
    void updateCouponOfUnknownIdIsNotFound() {
        init();
        when(couponRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCoupon(1L, updateCouponRequest("SAVE")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.NOT_FOUND);

        verify(couponRepository, never()).save(any());
    }

    /**
     * Changing a coupon's code to one another coupon already uses is the {@code 409
     * COUPON_CODE_DUPLICATED}.
     */
    @Test
    void updateCouponToAnAlreadyTakenCodeIsConflict() {
        init();
        Coupon existing = activeCoupon(DiscountType.FIXED, "50", "0", null);
        existing.setCode("OLD");
        when(couponRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(couponRepository.existsByCode("NEW")).thenReturn(true);

        assertThatThrownBy(() -> service.updateCoupon(1L, updateCouponRequest("NEW")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_CODE_DUPLICATED");

        verify(couponRepository, never()).save(any());
    }

    /**
     * An update with a validity window whose {@code endAt} is not after {@code startAt} is the
     * {@code 400 COUPON_INVALID_DATE_RANGE}.
     */
    @Test
    void updateCouponWithEndBeforeStartIsRejected() {
        init();
        Coupon existing = activeCoupon(DiscountType.FIXED, "50", "0", null);
        existing.setCode("SAVE");
        when(couponRepository.findById(1L)).thenReturn(Optional.of(existing));
        LocalDateTime start = LocalDateTime.now();

        assertThatThrownBy(() -> service.updateCoupon(1L, updateCouponRequest("SAVE", start, start.minusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "COUPON_INVALID_DATE_RANGE")
                .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);

        verify(couponRepository, never()).save(any());
    }
}
