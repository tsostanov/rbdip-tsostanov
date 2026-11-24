package com.rbdip.bookstore.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class ProductApiTest extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "Guide to refactoring")
    void createsProductWithOptionalDescriptionAndListsIt(String description) {
        Map<String, Object> request = new HashMap<>(Map.of("name", "Рефакторинг", "price", "19.90"));
        if (description != null) {
            request.put("description", description);
        }

        var created = restTemplate.postForEntity("/products", request, CreatedProduct.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        Long productId = created.getBody().id();
        assertThat(productId).isPositive();
        assertThat(created.getBody().name()).isEqualTo("Рефакторинг");
        Product stored = productRepository.findById(productId).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Рефакторинг");
        assertThat(stored.getPrice()).isEqualByComparingTo(new BigDecimal("19.90"));
        assertThat(stored.getDescription()).isEqualTo(description);

        var listed = restTemplate.getForEntity("/products", ListedProduct[].class);

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).containsExactly(new ListedProduct(productId, "Рефакторинг", "19.90"));
    }

    @Test
    void listsEmptyCatalog() {
        var response = restTemplate.getForEntity("/products", ListedProduct[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    record CreatedProduct(Long id, String name) {
    }

    record ListedProduct(Long id, String name, String price) {
    }
}
