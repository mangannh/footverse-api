package com.footverse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.common.security.JwtUtil;
import com.footverse.order.repository.CouponRepository;
import com.footverse.product.repository.ProductVariantRepository;
import com.footverse.support.AuthFixtures;
import com.footverse.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end checkout flow through the real filter chain, services, and database (no mocks,
 * sprint-4-plan item 14). An ADMIN seeds a category, brand, product, two ACTIVE in-stock variants, a
 * primary image, and a coupon; then a CUSTOMER creates an address, carts both variants, previews and
 * places a <em>partial</em> checkout, and the test asserts the snapshots, totals, stock decrement,
 * coupon consumption, partial cart removal, and the generated order code.
 *
 * <p>It then resubmits the identical checkout request and asserts the enveloped
 * {@code 404 CART_ITEM_NOT_FOUND}, that no second order exists, and that stock and {@code usedCount}
 * did not change again — the <strong>sequential</strong> duplicate case of Checkout Concurrency
 * Protection. A genuinely concurrent duplicate (two in-flight transactions racing for the same cart
 * rows) is <em>not</em> demonstrated here — it cannot be under MockMvc inside one rolled-back test
 * transaction; the concurrent case rests on the lock-ordering design of sprint-4-plan items 05/09,
 * not on this test (sprint-4-plan item 14 testing-limitation design note).</p>
 *
 * <p>Finally a second order is placed and cancelled — asserting stock and {@code usedCount} are
 * restored — and an ADMIN walks the first order {@code CONFIRMED → SHIPPING → DELIVERED}, asserting
 * the payment flips to {@code PAID} and {@code deliveredAt} is recorded. The flow spans the catalog,
 * cart, address, coupon, and order modules, so it lives in the root test package alongside the other
 * cross-module flow. ADMIN accounts are provisioned outside the API (user management is Future), so
 * the fixtures persist both users directly. Runs in a rolled-back transaction so no state leaks.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CheckoutFlowIntegrationTest {

    private static final String ADMIN_EMAIL = "checkout-admin@example.com";
    private static final String ADMIN_PHONE = "0900000070";
    private static final String CUSTOMER_EMAIL = "checkout-customer@example.com";
    private static final String CUSTOMER_PHONE = "0900000071";

    private static final String COUPON_CODE = "FVCHECKOUT";
    private static final String COUPON_BODY = """
            {"code":"FVCHECKOUT","name":"Checkout Save","discountType":"FIXED","discountValue":50000,
             "minOrderAmount":0,"startAt":"2020-01-01T00:00:00","endAt":"2030-01-01T00:00:00","enabled":true}
            """;
    private static final String ADDRESS_BODY = """
            {"recipientName":"Jane Doe","recipientPhone":"0912345678","province":"HCM",
             "district":"D1","ward":"W1","streetAddress":"1 Nguyen Hue"}
            """;

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final ProductVariantRepository productVariantRepository;

    CheckoutFlowIntegrationTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil,
            @Autowired UserRepository userRepository, @Autowired CouponRepository couponRepository,
            @Autowired ProductVariantRepository productVariantRepository) {
        this.mockMvc = mockMvc;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.couponRepository = couponRepository;
        this.productVariantRepository = productVariantRepository;
    }

    @BeforeEach
    void persistUsers() {
        userRepository.save(AuthFixtures.admin(ADMIN_EMAIL, ADMIN_PHONE));
        userRepository.save(AuthFixtures.customer(CUSTOMER_EMAIL, CUSTOMER_PHONE));
    }

    private String adminToken() {
        return "Bearer " + jwtUtil.createAccessToken(ADMIN_EMAIL);
    }

    private String customerToken() {
        return "Bearer " + jwtUtil.createAccessToken(CUSTOMER_EMAIL);
    }

    /**
     * Performs an authenticated {@code POST} that must answer {@code 201 Created} and yields the
     * created resource's id.
     */
    private long create(String path, String token, String json) throws Exception {
        String body = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** Adds the given variant and quantity to the customer's cart. */
    private void addToCart(long variantId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productVariantId\":" + variantId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    /** Reads the caller's cart and returns the cart-line id holding the given variant. */
    private long cartItemIdForVariant(long variantId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Object> ids = JsonPath.read(body, "$.data.items[?(@.productVariantId == " + variantId + ")].id");
        return ((Number) ids.get(0)).longValue();
    }

    /** Returns a variant's current persisted stock. */
    private int stockOf(long variantId) {
        return productVariantRepository.findById(variantId).orElseThrow().getStockQuantity();
    }

    /** Returns the coupon's current persisted usage count. */
    private int couponUsedCount() {
        return couponRepository.findByCode(COUPON_CODE).orElseThrow().getUsedCount();
    }

    /** Advances an order's status as ADMIN, asserting {@code 200 OK}. */
    private ResultActions patchStatus(long orderId, String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    /**
     * Walks the whole checkout domain: a partial checkout with a coupon, the sequential duplicate
     * rejection, a cancellation that restores stock and coupon usage, and the admin status walk to
     * {@code DELIVERED}.
     */
    @Test
    void customerChecksOutCancelsAndAdminDeliversOverASeededCatalog() throws Exception {
        // 1. ADMIN seeds the catalog: category, brand, product, two ACTIVE in-stock variants, image.
        long categoryId = create("/api/v1/categories", adminToken(),
                "{\"name\":\"Checkout Sneakers\",\"description\":\"flow category\"}");
        long brandId = create("/api/v1/brands", adminToken(), "{\"name\":\"Checkout Nike\"}");
        long productId = create("/api/v1/products", adminToken(),
                "{\"name\":\"FV Checkout Runner\",\"description\":\"flow product\",\"basePrice\":200000.00,"
                        + "\"categoryId\":" + categoryId + ",\"brandId\":" + brandId + "}");
        long variantA = create("/api/v1/products/" + productId + "/variants", adminToken(),
                "{\"color\":\"Black\",\"size\":\"42\",\"stockQuantity\":10,\"sku\":\"FV-CO-A\",\"status\":\"ACTIVE\"}");
        long variantB = create("/api/v1/products/" + productId + "/variants", adminToken(),
                "{\"color\":\"White\",\"size\":\"43\",\"stockQuantity\":10,\"sku\":\"FV-CO-B\",\"status\":\"ACTIVE\"}");
        create("/api/v1/products/" + productId + "/images", adminToken(),
                "{\"imageUrl\":\"http://img/checkout-primary.png\",\"displayOrder\":0,\"isPrimary\":true}");

        // ADMIN seeds a coupon (the coupon concern lives in the order module).
        create("/api/v1/coupons", adminToken(), COUPON_BODY);

        // 2. The CUSTOMER creates a shipping address and carts both variants.
        long addressId = create("/api/v1/addresses", customerToken(), ADDRESS_BODY);
        addToCart(variantA, 2);
        addToCart(variantB, 1);
        long cartItemA = cartItemIdForVariant(variantA);
        long cartItemB = cartItemIdForVariant(variantB);

        // 3. Preview the partial checkout of variant A with the coupon: 2 x 200000 = 400000 subtotal,
        // 50000 fixed discount, 30000 shipping, 380000 total — all server-computed.
        mockMvc.perform(post("/api/v1/coupons/validate")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + COUPON_CODE + "\",\"cartItemIds\":[" + cartItemA + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(COUPON_CODE))
                .andExpect(jsonPath("$.data.subtotal").value(400000.00))
                .andExpect(jsonPath("$.data.discountAmount").value(50000.00))
                .andExpect(jsonPath("$.data.shippingFee").value(30000.00))
                .andExpect(jsonPath("$.data.total").value(380000.00));

        // 4. Place the partial checkout of variant A only, applying the coupon.
        String checkoutBody = "{\"cartItemIds\":[" + cartItemA + "],\"addressId\":" + addressId
                + ",\"couponCode\":\"" + COUPON_CODE + "\",\"note\":\"leave at door\"}";
        String orderJson = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.subtotal").value(400000.00))
                .andExpect(jsonPath("$.data.discountAmount").value(50000.00))
                .andExpect(jsonPath("$.data.shippingFee").value(30000.00))
                .andExpect(jsonPath("$.data.total").value(380000.00))
                .andExpect(jsonPath("$.data.couponCode").value(COUPON_CODE))
                .andExpect(jsonPath("$.data.shippingRecipientName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.shippingProvince").value("HCM"))
                .andExpect(jsonPath("$.data.shippingStreetAddress").value("1 Nguyen Hue"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productVariantId").value((int) variantA))
                .andExpect(jsonPath("$.data.items[0].color").value("Black"))
                .andExpect(jsonPath("$.data.items[0].size").value("42"))
                .andExpect(jsonPath("$.data.items[0].productImageUrl").value("http://img/checkout-primary.png"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(200000.00))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(400000.00))
                .andReturn().getResponse().getContentAsString();
        long orderId = ((Number) JsonPath.read(orderJson, "$.data.id")).longValue();
        String orderCode = JsonPath.read(orderJson, "$.data.orderCode");

        // 5. The generated order code is human-readable and timestamp-based.
        assertThat(orderCode).startsWith("FV-").hasSize(20);

        // 6. Stock of A dropped by 2 (10 -> 8); B is untouched; the coupon was consumed once.
        assertThat(stockOf(variantA)).isEqualTo(8);
        assertThat(stockOf(variantB)).isEqualTo(10);
        assertThat(couponUsedCount()).isEqualTo(1);

        // 7. Only the selected line (A) was removed; the unselected line (B) remains in the cart.
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productVariantId").value((int) variantB));

        // 8. Resubmitting the identical checkout fails with 404 CART_ITEM_NOT_FOUND — line A is gone
        // (Checkout Concurrency Protection, sequential duplicate case).
        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CART_ITEM_NOT_FOUND"));

        // 9. No second order was created (the caller still has exactly one), and no state changed a
        // second time.
        mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value((int) orderId))
                .andExpect(jsonPath("$.data.content[0].orderCode").value(orderCode))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(2));
        assertThat(stockOf(variantA)).isEqualTo(8);
        assertThat(couponUsedCount()).isEqualTo(1);

        // 10. The customer reads that order's detail.
        mockMvc.perform(get("/api/v1/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCode").value(orderCode))
                .andExpect(jsonPath("$.data.total").value(380000.00));

        // 11. The customer places a second order (variant B, with the coupon) then cancels it.
        String secondCheckout = "{\"cartItemIds\":[" + cartItemB + "],\"addressId\":" + addressId
                + ",\"couponCode\":\"" + COUPON_CODE + "\"}";
        String secondJson = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondCheckout))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long secondOrderId = ((Number) JsonPath.read(secondJson, "$.data.id")).longValue();
        assertThat(stockOf(variantB)).isEqualTo(9);
        assertThat(couponUsedCount()).isEqualTo(2);

        // 12. Cancelling the second order restores its stock and coupon usage.
        mockMvc.perform(post("/api/v1/orders/" + secondOrderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"));
        assertThat(stockOf(variantB)).isEqualTo(10);
        assertThat(couponUsedCount()).isEqualTo(1);

        // 13. An ADMIN walks the first order through the status machine to DELIVERED.
        patchStatus(orderId, "CONFIRMED").andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        patchStatus(orderId, "SHIPPING").andExpect(jsonPath("$.data.status").value("SHIPPING"));
        patchStatus(orderId, "DELIVERED")
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());
    }
}
