package com.yoobu.api.admin.dto;

import com.yoobu.api.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStatusRequest(
        @NotNull BookingStatus status,
        @Size(max = 2048) String trackingUrl
) {
}
