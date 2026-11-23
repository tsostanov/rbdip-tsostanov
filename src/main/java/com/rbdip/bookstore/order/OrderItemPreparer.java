package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Разрешает товары из запроса в проверенные позиции заказа. */
@Component
public class OrderItemPreparer {

    private final ProductRepository productRepository;
    private final OrderRequestValidator validator;

    public OrderItemPreparer(ProductRepository productRepository, OrderRequestValidator validator) {
        this.productRepository = productRepository;
        this.validator = validator;
    }

    public List<PreparedOrderItem> prepare(List<CreateOrderRequest.Item> items) {
        return items.stream().map(this::prepareItem).toList();
    }

    private PreparedOrderItem prepareItem(CreateOrderRequest.Item item) {
        Product product = productRepository.findById(item.productId())
                .orElseThrow(() -> new IllegalArgumentException("product " + item.productId() + " not found"));
        int quantity = validator.resolveQuantity(item.quantity());
        return new PreparedOrderItem(product, quantity);
    }
}
