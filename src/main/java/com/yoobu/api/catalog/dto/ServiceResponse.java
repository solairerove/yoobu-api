package com.yoobu.api.catalog.dto;

import java.math.BigDecimal;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Integer durationMinutes,
        int sortOrder,
        boolean active
) {
}
