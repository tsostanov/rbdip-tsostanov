package com.rbdip.bookstore.order;

import java.math.BigDecimal;

/** Проверенная позиция с названием, ценой и количеством на момент заказа. */
public record PreparedOrderItem(String productName, BigDecimal unitPrice, int quantity) {
}
