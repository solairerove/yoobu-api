package com.yoobu.api.notification.event;

import com.yoobu.api.booking.Booking;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.tenant.Tenant;

public record BookingStatusChangedEvent(
        Booking booking,
        BookingStatus oldStatus,
        BookingStatus newStatus,
        Tenant tenant
) {}
