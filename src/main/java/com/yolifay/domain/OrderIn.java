package com.yolifay.domain;

public record OrderIn(
        String orderId,
        String customerId,
        double amount,
        String currency,
        String ts
) {
}
