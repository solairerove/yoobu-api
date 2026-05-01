package com.yoobu.api.booking.dto;

import java.math.BigDecimal;

public record TopBuyer(
        Long telegramUserId,
        String customerName,
        long orderCount,
        BigDecimal totalSpent,
        String currency
) {
}
