package com.yoobu.api.notification.event;

import com.yoobu.api.booking.Booking;
import com.yoobu.api.tenant.Tenant;

public record PaymentConfirmedEvent(Booking booking, String currency, Tenant tenant) {}
