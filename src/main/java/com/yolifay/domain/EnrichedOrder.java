package com.yolifay.domain;

public record EnrichedOrder(
        String orderId,
        String customerId,
        double amount,
        String currency,
        double amountIdr,
        boolean highValue,
        String processedAt
) {
}
