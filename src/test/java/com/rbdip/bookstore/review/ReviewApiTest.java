package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class ReviewApiTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    void storesReviewForPurchasedProductAndListsIt() {
        Long productId = createProduct("Refactoring");
        var order = new CreateOrderRequest("Anna", "Moscow", null, null, null,
                List.of(new CreateOrderRequest.Item(productId, 1)));
        assertThat(restTemplate.postForEntity("/orders", order, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        Long reviewId = postReview(productId,
                Map.of("authorName", "Anna", "rating", 5, "comment", "Useful examples"));

        Review stored = reviewRepository.findById(reviewId).orElseThrow();
        assertThat(stored.getId()).isEqualTo(reviewId);
        assertThat(stored.getProductId()).isEqualTo(productId);
        assertThat(stored.getAuthorName()).isEqualTo("Anna");
        assertThat(stored.getRating()).isEqualTo(5);
        assertThat(stored.getComment()).isEqualTo("Useful examples");
        assertThat(listReviews(productId))
                .containsExactly(new ListedReview("Anna", 5, "Useful examples"));
    }

    @Test
    void defaultsMissingAuthorAndCommentWithoutRequiringPurchase() {
        Long productId = createProduct("Refactoring");

        Long reviewId = postReview(productId, Map.of("rating", 4));

        Review stored = reviewRepository.findById(reviewId).orElseThrow();
        assertThat(stored.getAuthorName()).isEqualTo("anonymous");
        assertThat(stored.getComment()).isNull();
        assertThat(listReviews(productId)).containsExactly(new ListedReview("anonymous", 4, ""));
    }

    @Test
    void keepsReviewsForDifferentProductsSeparate() {
        Long firstProduct = createProduct("Refactoring");
        Long secondProduct = createProduct("Clean Code");
        postReview(firstProduct, Map.of("authorName", "Anna", "rating", 5, "comment", "First book"));
        postReview(secondProduct, Map.of("authorName", "Ivan", "rating", 3, "comment", "Second book"));

        assertThat(listReviews(firstProduct)).containsExactly(new ListedReview("Anna", 5, "First book"));
        assertThat(listReviews(secondProduct)).containsExactly(new ListedReview("Ivan", 3, "Second book"));
    }

    @Test
    void listsNoReviewsForNewProduct() {
        assertThat(listReviews(createProduct("Refactoring"))).isEmpty();
    }

    @Test
    void acceptsReviewWhenLegacyOrderHasNoItems() {
        Long productId = createProduct("Refactoring");
        Long customerId = jdbcTemplate.queryForObject(
                "INSERT INTO customers (full_name) VALUES ('Anna') RETURNING id", Long.class);
        jdbcTemplate.update("INSERT INTO orders (customer_id) VALUES (?)", customerId);

        postReview(productId, Map.of("authorName", "Anna", "rating", 4));

        assertThat(listReviews(productId)).containsExactly(new ListedReview("Anna", 4, ""));
    }

    private Long createProduct(String name) {
        return productRepository.save(new Product(name, new BigDecimal("100.00"), null)).getId();
    }

    private Long postReview(Long productId, Map<String, Object> body) {
        var response = restTemplate.postForEntity(
                "/products/" + productId + "/reviews", body, CreatedReview.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isPositive();
        return response.getBody().id();
    }

    private ListedReview[] listReviews(Long productId) {
        var response = restTemplate.getForEntity(
                "/products/" + productId + "/reviews", ListedReview[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    record CreatedReview(Long id) {
    }

    record ListedReview(String authorName, Integer rating, String comment) {
    }
}
