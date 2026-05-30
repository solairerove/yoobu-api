package com.yoobu.api.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EcommerceBookingItemRequest(
        @NotNull Long variantId,
        @Min(1) int quantity
) {
}
