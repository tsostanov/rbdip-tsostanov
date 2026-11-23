package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(OutputCaptureExtension.class)
class OrderCreationTest extends AbstractIntegrationTest {

    @ParameterizedTest
    @MethodSource("invalidCustomerDetails")
    void rejectsMissingCustomerDetails(String name, String address, String error) {
        Long productId = createProduct();
        CreateOrderRequest request = new CreateOrderRequest(
                name, address, null, null, null, List.of(new CreateOrderRequest.Item(productId, 1)));

        assertRejectedWithoutSaving(request, error);
    }

    static Stream<Arguments> invalidCustomerDetails() {
        return Stream.of(
                Arguments.of(null, "Moscow", "customerFullName is required"),
                Arguments.of("", "Moscow", "customerFullName is required"),
                Arguments.of("   ", "Moscow", "customerFullName is required"),
                Arguments.of("Anna", null, "customerAddress is required"),
                Arguments.of("Anna", "", "customerAddress is required"),
                Arguments.of("Anna", "   ", "customerAddress is required"));
    }

    @Test
    void rejectsNullItems() {
        CreateOrderRequest request = new CreateOrderRequest("Anna", "Moscow", null, null, null, null);

        assertRejectedWithoutSaving(request, "order must contain at least one item");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantity(int quantity) {
        Long productId = createProduct();
        CreateOrderRequest request = requestWithItems(List.of(new CreateOrderRequest.Item(productId, quantity)));

        assertRejectedWithoutSaving(request, "quantity must be positive");
    }

    @Test
    void rejectsUnknownProductAfterValidItemWithoutSavingPartialOrder() {
        Long productId = createProduct();
        CreateOrderRequest request = requestWithItems(List.of(
                new CreateOrderRequest.Item(productId, 1), new CreateOrderRequest.Item(Long.MAX_VALUE, 1)));

        assertRejectedWithoutSaving(request, "product " + Long.MAX_VALUE + " not found");
    }

    @Test
    void preservesDuplicateProductLinesAndDefaultsMissingQuantity() {
        Long productId = createProduct();
        CreateOrderRequest request = requestWithItems(List.of(
                new CreateOrderRequest.Item(productId, null), new CreateOrderRequest.Item(productId, 2)));

        ResponseEntity<Map> response = restTemplate.postForEntity("/orders", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("status", "new");
        assertThat(jdbcTemplate.queryForList("SELECT quantity FROM order_items ORDER BY id", Integer.class))
                .containsExactly(1, 2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT p.name FROM order_items i JOIN products p ON p.id = i.product_id ORDER BY i.id", String.class))
                .containsExactly("Refactoring", "Refactoring");
        assertThat(jdbcTemplate.queryForList(
                "SELECT p.price FROM order_items i JOIN products p ON p.id = i.product_id ORDER BY i.id", String.class))
                .containsExactly("100.00", "100.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT c.full_name FROM orders o JOIN customers c ON c.id = o.customer_id", String.class))
                .isEqualTo("Anna");
    }

    @Test
    void confirmationIncludesTotalAfterQuantityCustomerAndCouponDiscounts(CapturedOutput output) {
        Long productId = createProduct();
        CreateOrderRequest request = new CreateOrderRequest(
                "Anna", "Moscow", null, "vip", "SAVE10", List.of(new CreateOrderRequest.Item(productId, 11)));

        ResponseEntity<Map> response = restTemplate.postForEntity("/orders", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(output).contains("[email] Dear Anna, your order #1 for 930.50 has been placed.");
    }

    private CreateOrderRequest requestWithItems(List<CreateOrderRequest.Item> items) {
        return new CreateOrderRequest("Anna", "Moscow", null, null, null, items);
    }

    private Long createProduct() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO products (name, price) VALUES ('Refactoring', 100.00) RETURNING id", Long.class);
    }

    private void assertRejectedWithoutSaving(CreateOrderRequest request, String error) {
        ResponseEntity<Map> response = restTemplate.postForEntity("/orders", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", error);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_items", Long.class)).isZero();
    }
}
