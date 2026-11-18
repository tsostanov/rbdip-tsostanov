package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Имитирует отправку подтверждения: почтовый транспорт в проекте не настроен. */
@Component
public class ConsoleOrderNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleOrderNotifier.class);

    public void notifyCustomer(String customerName, Long orderId, BigDecimal total) {
        LOGGER.info("[email] Dear {}, your order #{} for {} has been placed.", customerName, orderId, total);
    }
}
