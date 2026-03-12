package com.yoobu.api.booking.dto;

import java.math.BigDecimal;

public record BookingItemResponse(
        String serviceName,
        int quantity,
        BigDecimal unitPrice
) {
}
