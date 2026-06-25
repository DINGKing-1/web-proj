package com.secondhand.mall.service;

import com.secondhand.mall.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BigDecimalTest {

    @Test
    void testBigDecimalPrecision() {
        BigDecimal a = new BigDecimal("0.1");
        BigDecimal b = new BigDecimal("0.2");
        BigDecimal sum = a.add(b);

        assertEquals(new BigDecimal("0.3"), sum);

        User user = new User();
        user.setBalance(new BigDecimal("10000.00"));
        user.setBalance(user.getBalance().subtract(new BigDecimal("9999.99")));
        assertEquals(new BigDecimal("0.01"), user.getBalance());
    }

    @Test
    void testBigDecimalComparison() {
        BigDecimal balance = new BigDecimal("100.00");
        BigDecimal price = new BigDecimal("100.00");
        assertTrue(balance.compareTo(price) >= 0);

        BigDecimal insufficient = new BigDecimal("99.99");
        assertTrue(insufficient.compareTo(price) < 0);
    }

    @Test
    void testBigDecimalAddSubtract() {
        BigDecimal buyerBalance = new BigDecimal("10000.00");
        BigDecimal sellerBalance = new BigDecimal("5000.00");
        BigDecimal price = new BigDecimal("299.99");

        buyerBalance = buyerBalance.subtract(price);
        sellerBalance = sellerBalance.add(price);

        assertEquals(new BigDecimal("9700.01"), buyerBalance);
        assertEquals(new BigDecimal("5299.99"), sellerBalance);
    }

    @Test
    void testDefaultBalance() {
        User user = new User();
        assertEquals(new BigDecimal("10000.00"), user.getBalance());
    }
}
