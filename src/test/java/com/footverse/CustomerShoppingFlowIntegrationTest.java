package com.footverse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.footverse.common.security.JwtUtil;
import com.footverse.support.AuthFixtures;
import com.footverse.user.repository.UserRepository;
import com.footverse.wishlist.repository.WishlistRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end customer shopping flow through the real filter chain, services, and database (no mocks,
 * sprint-3-plan item 10): an ADMIN seeds a category, brand, product, ACTIVE in-stock variant, and
 * primary image; then a CUSTOMER creates two addresses (asserting the exactly-one-default-per-user
 * invariant), adds the variant to their cart twice (asserting the quantity merge and the
 * server-computed {@code subtotal} / {@code itemCount} / availability), and wishlists the product
 * twice (asserting the idempotent duplicate add inserts no second row).
 *
 * <p>The flow spans the address, cart, and wishlist modules at once, so it belongs to none of them
 * and lives in the root test package alongside the context test, mirroring how the single-module
 * flows live in their own module package.</p>
 *
 * <p>ADMIN accounts are provisioned outside the API (user management is Future), so the fixtures
 * persist both users directly. Runs in a rolled-back transaction so no state leaks.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerShoppingFlowIntegrationTest {

    private static final String ADMIN_EMAIL = "shopping-admin@example.com";
    private static final String ADMIN_PHONE = "0900000060";
    private static final String CUSTOMER_EMAIL = "shopping-customer@example.com";
    private static final String CUSTOMER_PHONE = "0900000061";

    private static final String FIRST_ADDRESS = """
            {"recipientName":"Nguyen Van A","recipientPhone":"0912345678","province":"Ha Noi",
             "district":"Cau Giay","ward":"Dich Vong","streetAddress":"1 Pham Van Bach"}
            """;
    private static final String SECOND_DEFAULT_ADDRESS = """
            {"recipientName":"Tran Thi B","recipientPhone":"0987654321","province":"Da Nang",
             "district":"Hai Chau","ward":"Thanh Binh","streetAddress":"2 Bach Dang","isDefault":true}
            """;

    private final MockMvc mockMvc;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final WishlistRepository wishlistRepository;

    private Long customerId;

    CustomerShoppingFlowIntegrationTest(@Autowired MockMvc mockMvc, @Autowired JwtUtil jwtUtil,
            @Autowired UserRepository userRepository, @Autowired WishlistRepository wishlistRepository) {
        this.mockMvc = mockMvc;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.wishlistRepository = wishlistRepository;
    }

    @BeforeEach
    void persistUsers() {
        userRepository.save(AuthFixtures.admin(ADMIN_EMAIL, ADMIN_PHONE));
        customerId = userRepository.save(AuthFixtures.customer(CUSTOMER_EMAIL, CUSTOMER_PHONE)).getId();
    }

    private String adminToken() {
        return "Bearer " + jwtUtil.createAccessToken(ADMIN_EMAIL);
    }

    private String customerToken() {
        return "Bearer " + jwtUtil.createAccessToken(CUSTOMER_EMAIL);
    }

    /**
     * Performs an authenticated {@code POST} that must answer {@code 201 Created} and yields the id
     * of the created resource.
     *
     * @param path  the endpoint path
     * @param token the {@code Authorization} header value
     * @param json  the request body
     * @return the created resource's id
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

    /**
     * Performs an authenticated cart add of the given variant and quantity.
     *
     * @param variantId the product variant to add
     * @param quantity  the quantity to add
     * @return the result actions, ready to assert the returned cart against
     */
    private org.springframework.test.web.servlet.ResultActions addToCart(long variantId, int quantity)
            throws Exception {
        return mockMvc.perform(post("/api/v1/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productVariantId\":" + variantId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    /**
     * Walks the whole customer shopping flow over a catalog an ADMIN has just seeded: the
     * single-default address invariant, the cart's quantity merge and server-computed aggregates, and
     * the wishlist's idempotent add.
     */
    @Test
    void customerShopsTheCatalogAnAdminSeeded() throws Exception {
        // 1. ADMIN seeds the catalog: category, brand, product, ACTIVE in-stock variant, primary image.
        long categoryId = create("/api/v1/categories", adminToken(),
                "{\"name\":\"Shopping Sneakers\",\"description\":\"flow category\"}");
        long brandId = create("/api/v1/brands", adminToken(), "{\"name\":\"Shopping Nike\"}");
        long productId = create("/api/v1/products", adminToken(),
                "{\"name\":\"FV Shopping Runner\",\"description\":\"flow product\",\"basePrice\":150.00,"
                        + "\"categoryId\":" + categoryId + ",\"brandId\":" + brandId + "}");
        long variantId = create("/api/v1/products/" + productId + "/variants", adminToken(),
                "{\"size\":\"42\",\"stockQuantity\":5,\"sku\":\"FV-SHOP-42\",\"status\":\"ACTIVE\"}");
        create("/api/v1/products/" + productId + "/images", adminToken(),
                "{\"imageUrl\":\"http://img/shop-primary.png\",\"displayOrder\":0,\"isPrimary\":true}");

        // 2. The customer's first address becomes the default although the request never asked for it.
        mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FIRST_ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        // 3. A second address asking to be the default takes the flag over.
        mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SECOND_DEFAULT_ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        // 4. Exactly one default remains, and it is the second address: the filtered path yields a
        // single match, so a second default would fail this assertion rather than pass unnoticed.
        // The listing has no defined order, so the addresses are identified by name, not by index.
        mockMvc.perform(get("/api/v1/addresses").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.isDefault == true)].recipientName").value("Tran Thi B"))
                .andExpect(jsonPath("$.data[?(@.recipientName == 'Nguyen Van A')].isDefault").value(false));

        // 5. The first cart add creates the cart and the line.
        addToCart(variantId, 2)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.itemCount").value(2));

        // 6. Adding the same variant again merges into that line instead of duplicating it, and the
        // server recomputes the money: 5 x 150.00 = 750.00.
        addToCart(variantId, 3)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productVariantId").value((int) variantId))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(150.00))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(750.00))
                .andExpect(jsonPath("$.data.items[0].productImageUrl").value("http://img/shop-primary.png"))
                .andExpect(jsonPath("$.data.itemCount").value(5))
                .andExpect(jsonPath("$.data.subtotal").value(750.00));

        // 7. Re-reading the cart recomputes the same aggregates and flags the line purchasable
        // (the variant is ACTIVE and still in stock).
        mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].available").value(true))
                .andExpect(jsonPath("$.data.itemCount").value(5))
                .andExpect(jsonPath("$.data.subtotal").value(750.00));

        // 8. Wishlisting the product creates the line, assembled from the catalog summary.
        String addBody = mockMvc.perform(post("/api/v1/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productName").value("FV Shopping Runner"))
                .andExpect(jsonPath("$.data.primaryImageUrl").value("http://img/shop-primary.png"))
                .andExpect(jsonPath("$.data.basePrice").value(150.00))
                .andExpect(jsonPath("$.data.available").value(true))
                .andReturn().getResponse().getContentAsString();
        long wishlistItemId = ((Number) JsonPath.read(addBody, "$.data.id")).longValue();

        // 9. Wishlisting it again is an idempotent no-op: 200 OK carrying the very same row.
        mockMvc.perform(post("/api/v1/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value((int) wishlistItemId));

        // 10. The duplicate add inserted no second row.
        assertThat(wishlistRepository.findByUserIdOrderByCreatedAtDesc(customerId)).hasSize(1);
    }
}
