package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerRepository;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class NormalizedOrderTest extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void reusesCustomerWithIdenticalContactsIncludingNullPhone() {
        Long productId = createProduct();

        placeOrder(productId, "Moscow", null);
        placeOrder(productId, "Moscow", null);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customers", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT customer_id) FROM orders", Long.class))
                .isEqualTo(1);
        Customer customer = customerRepository.findByFullNameAndAddressAndPhone("Anna", "Moscow", null)
                .orElseThrow();
        assertThat(jdbcTemplate.queryForList("SELECT customer_id FROM orders", Long.class))
                .containsOnly(customer.getId());
    }

    @Test
    void keepsDifferentContactsSeparateForPeopleWithSameName() {
        Long productId = createProduct();

        placeOrder(productId, "Moscow", null);
        placeOrder(productId, "SPb", null);
        placeOrder(productId, "Moscow", "+79990000000");
        placeOrder(productId, "Moscow", "+79990000000");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM customers", Long.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class)).isEqualTo(4);
    }

    @Test
    void readsCustomerAndProductThroughRelationsWithUnchangedApiFields() {
        Long productId = createProduct();
        placeOrder(productId, "Moscow", "+79990000000");

        Order order = orderRepository.findAll().getFirst();
        assertThat(order.getCustomerFullName()).isEqualTo("Anna");
        assertThat(order.getCustomerAddress()).isEqualTo("Moscow");
        assertThat(order.getCustomerPhone()).isEqualTo("+79990000000");
        assertThat(order.getCreatedAt()).isNotNull();
        OrderItem item = orderItemRepository.findByOrderId(order.getId()).getFirst();
        assertThat(item.getId()).isNotNull();
        assertThat(item.getOrderId()).isEqualTo(order.getId());
        assertThat(item.getProductName()).isEqualTo("Refactoring");
        assertThat(item.getProductPrice()).isEqualByComparingTo("100.00");
        assertThat(item.getQuantity()).isEqualTo(2);

        var response = restTemplate.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        Map<?, ?> body = (Map<?, ?>) response.getBody().getFirst();
        assertThat(body.get("customerFullName")).isEqualTo("Anna");
        assertThat(body.get("items")).isEqualTo(List.of(Map.of("productName", "Refactoring", "quantity", 2)));
    }

    private Long createProduct() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO products (name, price) VALUES ('Refactoring', 100.00) RETURNING id", Long.class);
    }

    private void placeOrder(Long productId, String address, String phone) {
        var request = new CreateOrderRequest(
                "Anna", address, phone, null, null, List.of(new CreateOrderRequest.Item(productId, 2)));
        assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }
}
