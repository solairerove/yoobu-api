package com.yoobu.api.booking.dto;

import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.booking.BookingType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        BookingType type,
        BookingStatus status,
        String customerName,
        String customerPhone,
        BigDecimal totalPrice,
        String currency,
        LocalDate deliveryDate,
        String note,
        List<BookingItemResponse> items,
        OffsetDateTime createdAt
) {
}
