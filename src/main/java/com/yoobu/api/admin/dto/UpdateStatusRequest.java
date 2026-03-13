package com.yoobu.api.admin.dto;

import com.yoobu.api.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull BookingStatus status) {
}
