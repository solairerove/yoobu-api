package com.yoobu.api.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CreateBookingRequest(
        @NotBlank String customerName,
        @NotBlank String customerPhone,
        @NotBlank String deliveryAddress,
        @NotNull LocalDate deliveryDate,
        String note,
        @NotEmpty List<@Valid BookingItemRequest> items
) {
}
