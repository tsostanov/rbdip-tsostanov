package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Рассчитывает сумму: скидка на количество в позиции, тип клиента, купон,
 * ограничение снизу нулём, скидка на крупный заказ, затем округление.
 */
@Component
public class PricingCalculator {

    private static final int QUANTITY_DISCOUNT_THRESHOLD = 10;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal QUANTITY_DISCOUNT_MULTIPLIER = new BigDecimal("0.95");
    private static final BigDecimal VIP_DISCOUNT_MULTIPLIER = new BigDecimal("0.9");
    private static final BigDecimal WHOLESALE_DISCOUNT_MULTIPLIER = new BigDecimal("0.85");
    private static final BigDecimal COUPON_DISCOUNT_MULTIPLIER = new BigDecimal("0.8");
    private static final BigDecimal FIXED_COUPON_DISCOUNT = BigDecimal.TEN;
    private static final BigDecimal LARGE_ORDER_THRESHOLD = BigDecimal.valueOf(1000);
    private static final BigDecimal LARGE_ORDER_DISCOUNT_MULTIPLIER = new BigDecimal("0.98");
    private static final String VIP_CUSTOMER = "vip";
    private static final String WHOLESALE_CUSTOMER = "wholesale";
    private static final String FIXED_DISCOUNT_COUPON = "SAVE10";
    private static final String PERCENT_DISCOUNT_COUPON = "SAVE20PERCENT";

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal subtotal = calculateSubtotal(items);
        BigDecimal customerTotal = applyCustomerDiscount(subtotal, customerType);
        BigDecimal couponTotal = applyCoupon(customerTotal, couponCode).max(BigDecimal.ZERO);
        return applyLargeOrderDiscount(couponTotal).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSubtotal(List<LineItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineItem item : items) {
            total = total.add(calculateLineTotal(item));
        }
        return total;
    }

    private BigDecimal calculateLineTotal(LineItem item) {
        BigDecimal total = item.price().multiply(BigDecimal.valueOf(item.quantity()));
        if (item.quantity() > QUANTITY_DISCOUNT_THRESHOLD) {
            return total.multiply(QUANTITY_DISCOUNT_MULTIPLIER);
        }
        return total;
    }

    private BigDecimal applyCustomerDiscount(BigDecimal total, String customerType) {
        if (VIP_CUSTOMER.equals(customerType)) {
            return total.multiply(VIP_DISCOUNT_MULTIPLIER);
        }
        if (WHOLESALE_CUSTOMER.equals(customerType)) {
            return total.multiply(WHOLESALE_DISCOUNT_MULTIPLIER);
        }
        return total;
    }

    private BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        if (FIXED_DISCOUNT_COUPON.equals(couponCode)) {
            return total.subtract(FIXED_COUPON_DISCOUNT);
        }
        if (PERCENT_DISCOUNT_COUPON.equals(couponCode)) {
            return total.multiply(COUPON_DISCOUNT_MULTIPLIER);
        }
        return total;
    }

    private BigDecimal applyLargeOrderDiscount(BigDecimal total) {
        if (total.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            return total.multiply(LARGE_ORDER_DISCOUNT_MULTIPLIER);
        }
        return total;
    }
}
