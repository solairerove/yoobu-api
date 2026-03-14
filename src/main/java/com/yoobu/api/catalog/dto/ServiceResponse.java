package com.yoobu.api.catalog.dto;

import com.yoobu.api.catalog.ServiceStatus;
import java.math.BigDecimal;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Integer durationMinutes,
        int sortOrder,
        ServiceStatus status
) {
}
