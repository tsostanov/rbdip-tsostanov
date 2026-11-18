package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
        "10.00, 10, regular, , 100.00",
        "10.00, 11, regular, , 104.50",
        "100.00, 1, vip, , 90.00",
        "100.00, 1, wholesale, , 85.00",
        "100.00, 1, regular, SAVE10, 90.00",
        "100.00, 1, regular, SAVE20PERCENT, 80.00",
        "5.00, 1, regular, SAVE10, 0.00",
        "10.00, 1, regular, SAVE10, 0.00",
        "1000.00, 1, regular, , 1000.00",
        "1000.01, 1, regular, , 980.01",
        "100.00, 11, vip, SAVE10, 930.50",
        "100.00, 20, wholesale, SAVE20PERCENT, 1266.16",
        "1250.00, 1, regular, SAVE20PERCENT, 1000.00",
        "1009.00, 1, regular, SAVE10, 999.00",
        "0.005, 1, regular, , 0.01",
        "100.006, 1, vip, , 90.01",
        "100.00, 1, unknown, UNKNOWN, 100.00",
        "100.00, 1, VIP, save10, 100.00",
        "100.00, 1, , , 100.00"
    })
    void preservesDiscountOrderBoundariesAndRounding(
            String price, int quantity, String customerType, String couponCode, String expected) {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal(price), quantity)), customerType, couponCode);

        assertThat(total).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void appliesQuantityDiscountToEachLineIndependently() {
        List<PricingCalculator.LineItem> items = List.of(
                new PricingCalculator.LineItem(new BigDecimal("10.00"), 11),
                new PricingCalculator.LineItem(new BigDecimal("5.00"), 2));

        assertThat(calculator.calculateOrderTotal(items, "regular", null))
                .isEqualTo(new BigDecimal("114.50"));
    }

    @Test
    void emptyOrderWithFixedCouponStillHasZeroTotal() {
        assertThat(calculator.calculateOrderTotal(List.of(), "regular", "SAVE10"))
                .isEqualTo(new BigDecimal("0.00"));
    }
}
