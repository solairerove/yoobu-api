package com.yoobu.api.booking.dto;

import java.util.List;

public record BookingAnalytics(
        PeriodStats today,
        PeriodStats week,
        PeriodStats month,
        List<TopBuyer> topBuyers
) {
}
