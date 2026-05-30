package com.yoobu.api.catalog.dto;

import com.yoobu.api.catalog.ServiceStatus;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record AdminUpsertServiceRequest(
        @NotBlank String name,
        String description,
        BigDecimal price,
        String unit,
        Integer durationMinutes,
        Integer sortOrder,
        ServiceStatus status
) {
}
