package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Координирует оформление заказа в одной транзакции. */
@Service
public class OrderService {

    private final OrderRequestValidator validator;
    private final OrderItemPreparer itemPreparer;
    private final PricingCalculator pricingCalculator;
    private final OrderWriter orderWriter;
    private final ConsoleOrderNotifier notifier;

    public OrderService(
            OrderRequestValidator validator,
            OrderItemPreparer itemPreparer,
            PricingCalculator pricingCalculator,
            OrderWriter orderWriter,
            ConsoleOrderNotifier notifier) {
        this.validator = validator;
        this.itemPreparer = itemPreparer;
        this.pricingCalculator = pricingCalculator;
        this.orderWriter = orderWriter;
        this.notifier = notifier;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validator.validate(request);
        List<PreparedOrderItem> items = itemPreparer.prepare(request.items());
        BigDecimal total = calculateTotal(items, request);
        Order order = orderWriter.save(request, items);
        notifier.notifyCustomer(request.customerFullName(), order.getId(), total);
        return order;
    }

    private BigDecimal calculateTotal(List<PreparedOrderItem> items, CreateOrderRequest request) {
        List<PricingCalculator.LineItem> lineItems = items.stream()
                .map(item -> new PricingCalculator.LineItem(item.unitPrice(), item.quantity()))
                .toList();
        return pricingCalculator.calculateOrderTotal(lineItems, request.customerType(), request.couponCode());
    }
}
