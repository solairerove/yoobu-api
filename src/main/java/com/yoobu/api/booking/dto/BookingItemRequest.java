package com.yoobu.api.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingItemRequest(
        @NotNull Long serviceId,
        @Min(1) int quantity
) {
}
