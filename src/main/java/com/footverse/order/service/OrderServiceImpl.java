package com.footverse.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.footverse.product.dto.ProductVariantResponse;
import com.footverse.product.service.ProductVariantService;
import com.footverse.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Default {@link OrderService} implementation. It owns all business logic of the {@code order}
 * module, including the coupon concern (architecture-spec §4, §13): admin coupon CRUD and the
 * read-only checkout preview.
 *
 * <p>Cross-feature reads go through service interfaces only — the caller's selected cart lines via
 * {@link CartService} and each variant's purchase snapshot via {@link ProductVariantService}
 * (architecture-spec §7). The pricing and coupon-validation helpers are the single path checkout
 * (a later task) reuses, so the preview always matches what checkout will persist.</p>
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String COUPON_CODE_DUPLICATED_CODE = "COUPON_CODE_DUPLICATED";
    private static final String COUPON_CODE_DUPLICATED_MESSAGE = "Coupon code already exists";
    private static final String COUPON_NOT_FOUND_CODE = "COUPON_NOT_FOUND";
    private static final String COUPON_NOT_FOUND_MESSAGE = "Coupon not found";
    private static final String COUPON_INVALID_DATE_RANGE_CODE = "COUPON_INVALID_DATE_RANGE";
    private static final String COUPON_INVALID_DATE_RANGE_MESSAGE = "Coupon end date must be after start date";
    private static final String COUPON_DISABLED_CODE = "COUPON_DISABLED";
    private static final String COUPON_DISABLED_MESSAGE = "Coupon is not enabled";
    private static final String COUPON_EXPIRED_CODE = "COUPON_EXPIRED";
    private static final String COUPON_EXPIRED_MESSAGE = "Coupon is not valid at this time";
    private static final String COUPON_USAGE_LIMIT_EXCEEDED_CODE = "COUPON_USAGE_LIMIT_EXCEEDED";
    private static final String COUPON_USAGE_LIMIT_EXCEEDED_MESSAGE = "Coupon usage limit has been reached";
    private static final String COUPON_MIN_ORDER_AMOUNT_NOT_MET_CODE = "COUPON_MIN_ORDER_AMOUNT_NOT_MET";
    private static final String COUPON_MIN_ORDER_AMOUNT_NOT_MET_MESSAGE =
            "Order subtotal does not meet the coupon minimum";
    private static final String ORDER_CODE_GENERATION_FAILED_CODE = "ORDER_CODE_GENERATION_FAILED";
    private static final String ORDER_CODE_GENERATION_FAILED_MESSAGE = "Could not generate a unique order code";
    private static final String ORDER_NOT_FOUND_CODE = "ORDER_NOT_FOUND";
    private static final String ORDER_NOT_FOUND_MESSAGE = "Order not found";
    private static final String ORDER_FORBIDDEN_CODE = "ORDER_FORBIDDEN";
    private static final String ORDER_FORBIDDEN_MESSAGE = "You cannot access this order";
    private static final String ORDER_NOT_CANCELLABLE_CODE = "ORDER_NOT_CANCELLABLE";
    private static final String ORDER_NOT_CANCELLABLE_MESSAGE = "Order can only be cancelled while PENDING";
    private static final String ORDER_INVALID_STATUS_TRANSITION_CODE = "ORDER_INVALID_STATUS_TRANSITION";
    private static final String ORDER_INVALID_STATUS_TRANSITION_MESSAGE = "Order status transition is not allowed";

    /** Field the order-history list is always sorted by, most-recent-first (assumption 3). */
    private static final String ORDER_HISTORY_SORT_FIELD = "createdAt";

    /** Flat shipping fee applied to every order (business-rules → Order; sprint-4-plan assumption 5). */
    private static final BigDecimal SHIPPING_FEE = new BigDecimal("30000.00");
    private static final BigDecimal PERCENT_DIVISOR = new BigDecimal("100");
    private static final int MONEY_SCALE = 2;

    /** Order-code prefix and millisecond-precision timestamp pattern (sprint-4-plan assumption 7). */
    private static final String ORDER_CODE_PREFIX = "FV-";
    private static final DateTimeFormatter ORDER_CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * Bounded number of attempts to find a free timestamp order code; the {@code uk_orders_order_code}
     * unique constraint is the ultimate safety net for a same-millisecond collision (assumption 7).
     */
    private static final int ORDER_CODE_MAX_ATTEMPTS = 5;

    private final CouponRepository couponRepository;
    private final CouponMapper couponMapper;
    private final CartService cartService;
    private final ProductVariantService productVariantService;
    private final AddressService addressService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> getCoupons(Pageable pageable) {
        return PageResponse.from(couponRepository.findAll(pageable).map(couponMapper::toResponse));
    }

    @Override
    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request) {
        if (couponRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException(COUPON_CODE_DUPLICATED_CODE, COUPON_CODE_DUPLICATED_MESSAGE);
        }
        validateWindow(request.startAt(), request.endAt());
        Coupon coupon = new Coupon();
        coupon.setCode(request.code());
        coupon.setName(request.name());
        coupon.setDescription(request.description());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setMaxDiscountAmount(request.maxDiscountAmount());
        coupon.setStartAt(request.startAt());
        coupon.setEndAt(request.endAt());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setEnabled(request.enabled());
        return couponMapper.toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(Long id, UpdateCouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(COUPON_NOT_FOUND_CODE, COUPON_NOT_FOUND_MESSAGE));
        if (!coupon.getCode().equals(request.code()) && couponRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException(COUPON_CODE_DUPLICATED_CODE, COUPON_CODE_DUPLICATED_MESSAGE);
        }
        validateWindow(request.startAt(), request.endAt());
        coupon.setCode(request.code());
        coupon.setName(request.name());
        coupon.setDescription(request.description());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setMaxDiscountAmount(request.maxDiscountAmount());
        coupon.setStartAt(request.startAt());
        coupon.setEndAt(request.endAt());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setEnabled(request.enabled());
        return couponMapper.toResponse(couponRepository.save(coupon));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponPreviewResponse previewCoupon(CouponPreviewRequest request) {
        List<CheckoutCartLine> lines = cartService.resolvePreviewItems(request.cartItemIds());
        Pricing pricing = computePricing(priceLines(lines), request.code());
        Coupon coupon = pricing.coupon();
        return new CouponPreviewResponse(
                coupon == null ? null : coupon.getCode(),
                coupon == null ? null : coupon.getName(),
                pricing.subtotal(), pricing.discountAmount(), SHIPPING_FEE, pricing.total());
    }

    @Override
    @Transactional
    public OrderDetailResponse placeOrder(PlaceOrderRequest request) {
        User customer = currentUserProvider.getCurrentUser();

        // Lock 1 — the selected cart rows, in ascending id (CartService item 05). Ownership-checked.
        List<CheckoutCartLine> lines = cartService.resolveCheckoutItems(request.cartItemIds());
        AddressResponse address = addressService.getMyAddress(request.addressId());

        // Re-run the shared pricing / coupon-validation path so the persisted totals match the preview.
        List<PricedLine> pricedLines = priceLines(lines);
        Pricing pricing = computePricing(pricedLines, request.couponCode());

        // Lock 2 — the product variants, in ascending id (ProductVariantService item 04). Decremented.
        productVariantService.decrementStock(demandByVariant(pricedLines));

        Coupon coupon = pricing.coupon();
        if (coupon != null) {
            coupon.setUsedCount(coupon.getUsedCount() + 1);
            couponRepository.save(coupon);
        }

        Order order = saveOrderWithUniqueCode(buildOrder(request, customer, address, pricing));
        List<OrderItem> items = orderItemRepository.saveAll(buildItems(order, pricedLines));

        // Remove exactly the checked-out lines; the cart row and unselected lines remain.
        cartService.removeCheckedOutItems(request.cartItemIds());

        List<OrderItemResponse> itemResponses = items.stream().map(orderMapper::toResponse).toList();
        return orderMapper.toDetailResponse(order, itemResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getMyOrders(Pageable pageable) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        Page<Order> orders = orderRepository.findByUserId(userId, mostRecentFirst(pageable));
        Map<Long, Integer> itemCounts = itemCountsByOrder(orders.getContent());
        return PageResponse.from(orders.map(order ->
                orderMapper.toSummaryResponse(order, itemCounts.getOrDefault(order.getId(), 0))));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getMyOrder(Long id) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        Order order = orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> unresolvableOrder(id));
        List<OrderItemResponse> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(orderMapper::toResponse)
                .toList();
        return orderMapper.toDetailResponse(order, items);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDeliveredOrderForProduct(Long productId) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        List<Long> variantIds = productVariantService.getVariantsByProduct(productId).stream()
                .map(ProductVariantResponse::id)
                .toList();
        if (variantIds.isEmpty()) {
            return false;
        }
        return orderItemRepository
                .existsDeliveredOrderItemForUserAndProductVariants(userId, variantIds);
    }

    @Override
    @Transactional
    public OrderDetailResponse cancelMyOrder(Long id) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        Order order = orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> unresolvableOrder(id));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    ORDER_NOT_CANCELLABLE_CODE, ORDER_NOT_CANCELLABLE_MESSAGE);
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        applyCancellation(order, items);
        List<OrderItemResponse> itemResponses = items.stream().map(orderMapper::toResponse).toList();
        return orderMapper.toDetailResponse(order, itemResponses);
    }

    @Override
    @Transactional
    public OrderDetailResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        // Admin operation — ownership is bypassed (security-spec §7); resolve by id alone.
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ORDER_NOT_FOUND_CODE, ORDER_NOT_FOUND_MESSAGE));
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        applyStatusTransition(order, request.status(), items);
        List<OrderItemResponse> itemResponses = items.stream().map(orderMapper::toResponse).toList();
        return orderMapper.toDetailResponse(order, itemResponses);
    }

    /**
     * Applies an admin status transition to an order against the frozen state machine (business-rules
     * → Order Status Transitions): {@code PENDING→CONFIRMED}, {@code CONFIRMED→SHIPPING},
     * {@code SHIPPING→DELIVERED}, and {@code PENDING→CANCELLED}. A {@code CANCELLED} target reuses the
     * single {@link #applyCancellation(Order, List)} compensation path (there are not two cancellation
     * flows), but only from {@code PENDING} — a non-{@code PENDING} cancel is the enveloped
     * {@code 409 ORDER_NOT_CANCELLABLE}. A {@code DELIVERED} target additionally flips the payment to
     * {@code PAID} and stamps {@code deliveredAt} exactly once (business-rules → Payment). Any other
     * transition is the enveloped {@code 409 ORDER_INVALID_STATUS_TRANSITION} and mutates nothing.
     *
     * @param order  the order to advance (mutated in place)
     * @param target the requested target status
     * @param items  the order's lines, credited back to their variants when the transition cancels
     */
    private void applyStatusTransition(Order order, OrderStatus target, List<OrderItem> items) {
        OrderStatus current = order.getStatus();
        if (target == OrderStatus.CANCELLED) {
            if (current != OrderStatus.PENDING) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        ORDER_NOT_CANCELLABLE_CODE, ORDER_NOT_CANCELLABLE_MESSAGE);
            }
            applyCancellation(order, items);
            return;
        }
        if (!isLegalForwardTransition(current, target)) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    ORDER_INVALID_STATUS_TRANSITION_CODE, ORDER_INVALID_STATUS_TRANSITION_MESSAGE);
        }
        order.setStatus(target);
        if (target == OrderStatus.DELIVERED) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setDeliveredAt(LocalDateTime.now());
        }
        orderRepository.save(order);
    }

    /**
     * Reports whether {@code current → target} is one of the frozen forward transitions
     * {@code PENDING→CONFIRMED}, {@code CONFIRMED→SHIPPING}, {@code SHIPPING→DELIVERED} (business-rules
     * → Order Status Transitions). The {@code PENDING→CANCELLED} branch is handled separately by
     * {@link #applyStatusTransition(Order, OrderStatus, List)}, so it is intentionally not listed here.
     *
     * @param current the order's current status
     * @param target  the requested target status
     * @return {@code true} when the forward transition is allowed
     */
    private boolean isLegalForwardTransition(OrderStatus current, OrderStatus target) {
        return (current == OrderStatus.PENDING && target == OrderStatus.CONFIRMED)
                || (current == OrderStatus.CONFIRMED && target == OrderStatus.SHIPPING)
                || (current == OrderStatus.SHIPPING && target == OrderStatus.DELIVERED);
    }

    /**
     * Applies the full cancellation compensation to a {@code PENDING} order, in one unit inside the
     * caller's transaction (business-rules → Cancellation; database-spec §18): sets the status to
     * {@code CANCELLED} and stamps {@code cancelledAt}, restores every order item's stock via
     * {@link ProductVariantService#restoreStock} (item 04), and — only when the order applied a
     * coupon — decrements that coupon's {@code usedCount} by one. The decrement is floored at zero so
     * it can never drive the counter negative (database-spec §18); the payment status is left
     * untouched at {@code UNPAID} (business-rules → Cancellation). The caller has already verified the
     * order is {@code PENDING}; this method never reads the status again.
     *
     * <p>The whole compensation shares the caller's {@code @Transactional} boundary, so a failure of
     * the stock restore or the coupon update rolls the status change back with it — no partial state
     * (stock restored but order not cancelled, or coupon rolled back but stock not) can persist.</p>
     *
     * @param order the {@code PENDING} order to cancel (mutated in place)
     * @param items the order's lines, whose quantities are credited back to their variants
     */
    private void applyCancellation(Order order, List<OrderItem> items) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.save(order);
        productVariantService.restoreStock(restoreDemandByVariant(items));
        Coupon coupon = order.getCoupon();
        if (coupon != null) {
            coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
            couponRepository.save(coupon);
        }
    }

    /**
     * Builds the per-variant quantity credit for the stock restore, keyed by variant id. Order items
     * hold distinct variants, but quantities are merged defensively so the credit is well formed
     * regardless — mirroring {@link #demandByVariant(List)} on the checkout side.
     *
     * @param items the order's lines
     * @return the quantity to add back per variant id
     */
    private Map<Long, Integer> restoreDemandByVariant(List<OrderItem> items) {
        Map<Long, Integer> demand = new LinkedHashMap<>();
        for (OrderItem item : items) {
            demand.merge(item.getProductVariantId(), item.getQuantity(), Integer::sum);
        }
        return demand;
    }

    /**
     * Rebuilds the given pageable with its page and size preserved but its sort forced to
     * {@code createdAt} descending, so the order history is always most-recent-first regardless of
     * any client-supplied sort (assumption 3, business-rules → Order).
     *
     * @param pageable the incoming pagination request
     * @return a pagination request with the same page/size and a fixed {@code createdAt} desc sort
     */
    private Pageable mostRecentFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, ORDER_HISTORY_SORT_FIELD));
    }

    /**
     * Computes each order's {@code itemCount} (Σ order-item quantity) for a history page, reading the
     * lines of every order on the page in one batch query and grouping in memory — never a stored
     * column and never a per-order N+1 (the aggregate is the service's responsibility, not the
     * mapper's).
     *
     * @param orders the orders on the current page
     * @return a map from order id to the sum of its order-item quantities
     */
    private Map<Long, Integer> itemCountsByOrder(List<Order> orders) {
        if (orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        return orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId(),
                        Collectors.summingInt(OrderItem::getQuantity)));
    }

    /**
     * Distinguishes the two reasons a caller-scoped order read can come back empty, mirroring the
     * address / cart ownership precedent (security-spec §7): an order that exists but belongs to
     * another user is a {@code 403}, an order that does not exist at all is a {@code 404}. Ownership
     * is never hidden behind a {@code 404}.
     *
     * @param id the requested order id
     * @return the exception to throw
     */
    private RuntimeException unresolvableOrder(Long id) {
        if (orderRepository.existsById(id)) {
            return new BusinessException(HttpStatus.FORBIDDEN, ORDER_FORBIDDEN_CODE, ORDER_FORBIDDEN_MESSAGE);
        }
        return new ResourceNotFoundException(ORDER_NOT_FOUND_CODE, ORDER_NOT_FOUND_MESSAGE);
    }

    /**
     * Resolves each selected cart line to its variant purchase snapshot
     * ({@link ProductVariantService#getPurchaseSnapshot}), reading each snapshot once. The snapshot
     * carries the effective unit price (never recomputed, dto-spec §1) and the display fields checkout
     * persists onto {@code order_item}. Shared by the preview and checkout so both price identically.
     *
     * @param lines the caller's selected cart lines
     * @return the priced lines, in the requested order
     */
    private List<PricedLine> priceLines(List<CheckoutCartLine> lines) {
        List<PricedLine> priced = new ArrayList<>();
        for (CheckoutCartLine line : lines) {
            priced.add(new PricedLine(line, productVariantService.getPurchaseSnapshot(line.productVariantId())));
        }
        return priced;
    }

    /**
     * Computes the fully server-side pricing for the selected lines, optionally applying a coupon
     * (database-spec §14): sums the subtotal, validates and applies the coupon when a code is
     * supplied, adds the flat shipping fee, and computes {@code total = subtotal − discount + fee}.
     * Read-only — it never mutates the coupon; checkout increments {@code usedCount} separately after
     * this returns. This is the single pricing path the preview and checkout share.
     *
     * @param pricedLines the priced selected lines
     * @param code        the optional coupon code (blank or {@code null} means no coupon)
     * @return the computed pricing, its {@code coupon} {@code null} when none was applied
     */
    private Pricing computePricing(List<PricedLine> pricedLines, String code) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (PricedLine priced : pricedLines) {
            subtotal = subtotal.add(priced.lineTotal());
        }
        if (code == null || code.isBlank()) {
            return new Pricing(null, subtotal, BigDecimal.ZERO, subtotal.add(SHIPPING_FEE));
        }
        Coupon coupon = validateCoupon(code, subtotal);
        BigDecimal discountAmount = computeDiscount(coupon, subtotal);
        return new Pricing(coupon, subtotal, discountAmount, subtotal.subtract(discountAmount).add(SHIPPING_FEE));
    }

    /**
     * Builds the per-variant quantity demand for the stock decrement, keyed by variant id. Cart lines
     * hold distinct variants (the {@code (cart, variant)} unique constraint), but quantities are
     * merged defensively so the demand is well formed regardless.
     *
     * @param pricedLines the priced selected lines
     * @return the quantity to subtract per variant id
     */
    private Map<Long, Integer> demandByVariant(List<PricedLine> pricedLines) {
        Map<Long, Integer> demand = new LinkedHashMap<>();
        for (PricedLine priced : pricedLines) {
            demand.merge(priced.snapshot().productVariantId(), priced.line().quantity(), Integer::sum);
        }
        return demand;
    }

    /**
     * Assembles the {@link Order} aggregate from the checkout inputs: the customer, the applied coupon
     * (or {@code null}), the initial {@code PENDING}/{@code COD}/{@code UNPAID} state, the money
     * snapshot, the shipping snapshot copied from the resolved address, and the optional note. The
     * order code is stamped later by {@link #saveOrderWithUniqueCode(Order)}; the order is not yet
     * persisted.
     *
     * @param request  the checkout request (for the note)
     * @param customer the acting customer
     * @param address  the resolved shipping address to snapshot
     * @param pricing  the computed pricing to snapshot
     * @return the assembled, not-yet-persisted order
     */
    private Order buildOrder(PlaceOrderRequest request, User customer, AddressResponse address, Pricing pricing) {
        Order order = new Order();
        order.setUser(customer);
        order.setCoupon(pricing.coupon());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setSubtotal(pricing.subtotal());
        order.setDiscountAmount(pricing.discountAmount());
        order.setShippingFee(SHIPPING_FEE);
        order.setTotal(pricing.total());
        order.setShippingRecipientName(address.recipientName());
        order.setShippingRecipientPhone(address.recipientPhone());
        order.setShippingProvince(address.province());
        order.setShippingDistrict(address.district());
        order.setShippingWard(address.ward());
        order.setShippingStreetAddress(address.streetAddress());
        order.setNote(request.note());
        return order;
    }

    /**
     * Builds one {@link OrderItem} per priced line, snapshotting the product name, primary image URL,
     * color, size, and effective unit price from the variant purchase snapshot (database-spec §12) —
     * never reading the catalog again — so later catalog edits cannot alter a placed order. Each line
     * total is {@code unitPrice × quantity}.
     *
     * @param order       the owning (persisted) order
     * @param pricedLines the priced selected lines
     * @return the order items, not yet persisted
     */
    private List<OrderItem> buildItems(Order order, List<PricedLine> pricedLines) {
        List<OrderItem> items = new ArrayList<>();
        for (PricedLine priced : pricedLines) {
            ProductVariantPurchaseSnapshot snapshot = priced.snapshot();
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductVariantId(snapshot.productVariantId());
            item.setProductName(snapshot.productName());
            item.setProductImageUrl(snapshot.primaryImageUrl());
            item.setColor(snapshot.color());
            item.setSize(snapshot.size());
            item.setUnitPrice(snapshot.unitPrice());
            item.setQuantity(priced.line().quantity());
            item.setLineTotal(priced.lineTotal());
            items.add(item);
        }
        return items;
    }

    /**
     * Persists the order, letting the {@code uk_orders_order_code} unique constraint — not a
     * pre-read — be the authority on code uniqueness (assumption 7): the constraint is the only place
     * a collision can be detected atomically, so there is no time-of-check-to-time-of-use race. Each
     * attempt stamps a fresh timestamp code {@code FV-yyyyMMddHHmmssSSS} (no counter, sequence, or
     * lock) and flushes the insert; a same-millisecond collision surfaces as a
     * {@link DataIntegrityViolationException} from the constraint and is retried with a new code, up
     * to {@link #ORDER_CODE_MAX_ATTEMPTS}. A sequential checkout never collides (the clock has moved
     * on, so the first attempt succeeds); only a genuine same-millisecond concurrent insert is
     * retried, and the constraint guarantees no duplicate code is ever persisted.
     *
     * <p>The flush is forced here (rather than deferred to commit) so a collision is caught while the
     * remaining checkout work — order items, cart cleanup — has not yet run, keeping the whole
     * operation inside the caller's single transaction (no boundary change).</p>
     *
     * @param order the assembled order to persist (its code is stamped here)
     * @return the persisted order
     * @throws BusinessException {@code 500 ORDER_CODE_GENERATION_FAILED} if every attempt collides
     */
    private Order saveOrderWithUniqueCode(Order order) {
        for (int attempt = 0; attempt < ORDER_CODE_MAX_ATTEMPTS; attempt++) {
            order.setOrderCode(newOrderCode());
            try {
                return orderRepository.saveAndFlush(order);
            } catch (DataIntegrityViolationException collision) {
                // The order code lost a same-millisecond race against the unique constraint;
                // regenerate a fresh timestamp code and retry within the bounded budget.
            }
        }
        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                ORDER_CODE_GENERATION_FAILED_CODE, ORDER_CODE_GENERATION_FAILED_MESSAGE);
    }

    /**
     * Builds a human-readable timestamp order code {@code FV-yyyyMMddHHmmssSSS} from the current clock
     * (assumption 7). Uniqueness is not asserted here — that is the unique constraint's job in
     * {@link #saveOrderWithUniqueCode(Order)}.
     *
     * @return a candidate order code
     */
    private String newOrderCode() {
        return ORDER_CODE_PREFIX + LocalDateTime.now().format(ORDER_CODE_FORMATTER);
    }

    /**
     * Validates a coupon against the frozen rules (database-spec §14): the code exists, the coupon is
     * enabled, the current time is within {@code [startAt, endAt]}, the usage limit is not exceeded,
     * and the subtotal meets the minimum order amount. Each failed rule is an enveloped business
     * error (error-spec §8.10). Read-only — it never mutates the coupon. Shared with checkout.
     *
     * @param code     the coupon code
     * @param subtotal the priced subtotal to check against the minimum order amount
     * @return the validated coupon
     */
    private Coupon validateCoupon(String code, BigDecimal subtotal) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(COUPON_NOT_FOUND_CODE, COUPON_NOT_FOUND_MESSAGE));
        if (!coupon.isEnabled()) {
            throw badRequest(COUPON_DISABLED_CODE, COUPON_DISABLED_MESSAGE);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartAt()) || now.isAfter(coupon.getEndAt())) {
            throw badRequest(COUPON_EXPIRED_CODE, COUPON_EXPIRED_MESSAGE);
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw badRequest(COUPON_USAGE_LIMIT_EXCEEDED_CODE, COUPON_USAGE_LIMIT_EXCEEDED_MESSAGE);
        }
        if (subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw badRequest(COUPON_MIN_ORDER_AMOUNT_NOT_MET_CODE, COUPON_MIN_ORDER_AMOUNT_NOT_MET_MESSAGE);
        }
        return coupon;
    }

    /**
     * Computes the discount for a validated coupon (database-spec §14): a {@code PERCENT} of the
     * subtotal, capped by {@code maxDiscountAmount} when set, or a {@code FIXED} amount. The percentage
     * is rounded to the money scale (2 decimals, HALF_UP). Shared with checkout.
     *
     * <p>The result is finally clamped to the subtotal, so the discount can never exceed it and the
     * order total floors at the shipping fee rather than going negative. The frozen spec bounds
     * neither {@code discountValue} nor the percentage against the subtotal (validation-spec §9 only
     * requires {@code @Positive}), so a misconfigured coupon — a {@code FIXED} amount above the
     * subtotal or a percentage over 100 — could otherwise drive the total below zero (project
     * decision, business-rules → Coupon).</p>
     *
     * @param coupon   the validated coupon
     * @param subtotal the priced subtotal
     * @return the discount amount, never greater than the subtotal
     */
    private BigDecimal computeDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            BigDecimal raw = subtotal.multiply(coupon.getDiscountValue())
                    .divide(PERCENT_DIVISOR, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal cap = coupon.getMaxDiscountAmount();
            discount = cap != null && raw.compareTo(cap) > 0 ? cap : raw;
        } else {
            discount = coupon.getDiscountValue();
        }
        return discount.min(subtotal);
    }

    /**
     * Builds a {@code 400 Bad Request} business exception for a coupon that is not applicable to the
     * order (error-spec §3 — an input-based business rejection).
     *
     * @param code    the error code
     * @param message the user-safe message
     * @return the exception to throw
     */
    private BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * Ensures the validity window is well formed: {@code endAt} must be strictly after
     * {@code startAt} (validation-spec §12, service-enforced).
     *
     * @param startAt the validity start
     * @param endAt   the validity end
     */
    private void validateWindow(LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    COUPON_INVALID_DATE_RANGE_CODE, COUPON_INVALID_DATE_RANGE_MESSAGE);
        }
    }

    /**
     * A selected cart line paired with the variant purchase snapshot it was priced from. Internal to
     * the checkout/preview pricing path; it keeps each snapshot read to once and carries the display
     * fields checkout persists onto {@code order_item}.
     *
     * @param line     the selected cart line (variant id and quantity)
     * @param snapshot the variant purchase snapshot (effective price and display fields)
     */
    private record PricedLine(CheckoutCartLine line, ProductVariantPurchaseSnapshot snapshot) {

        /**
         * The line total: the snapshot unit price times the line quantity.
         *
         * @return {@code unitPrice × quantity}
         */
        private BigDecimal lineTotal() {
            return snapshot.unitPrice().multiply(BigDecimal.valueOf(line.quantity()));
        }
    }

    /**
     * The server-computed pricing of a selection, shared by the preview and checkout so both agree.
     *
     * @param coupon         the applied coupon, or {@code null} when none was applied
     * @param subtotal       the sum of the line totals
     * @param discountAmount the coupon discount (zero when no coupon), clamped to the subtotal
     * @param total          {@code subtotal − discountAmount + shippingFee}
     */
    private record Pricing(Coupon coupon, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal total) {
    }
}
