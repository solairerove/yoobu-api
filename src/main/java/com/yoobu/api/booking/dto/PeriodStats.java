package com.yoobu.api.booking.dto;

import java.math.BigDecimal;

public record PeriodStats(long orderCount, BigDecimal revenue, String currency) {
}
