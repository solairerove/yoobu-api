package com.yoobu.api.notification.event;

import com.yoobu.api.booking.Booking;
import com.yoobu.api.tenant.Tenant;
import java.util.List;

public record BookingCreatedEvent(
        Booking booking,
        List<OrderItem> items,
        Tenant tenant
) {
    public record OrderItem(String serviceName, int quantity) {}
}
