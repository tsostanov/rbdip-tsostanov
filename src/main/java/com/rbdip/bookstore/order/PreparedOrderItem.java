package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import java.math.BigDecimal;

/** Проверенная позиция со ссылкой на товар и количеством. */
public record PreparedOrderItem(Product product, int quantity) {

    public BigDecimal unitPrice() {
        return product.getPrice();
    }
}
