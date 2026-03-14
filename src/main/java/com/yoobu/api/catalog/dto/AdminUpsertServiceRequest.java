package com.yoobu.api.catalog.dto;

import com.yoobu.api.catalog.ServiceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AdminUpsertServiceRequest(
        @NotBlank String name,
        String description,
        @NotNull BigDecimal price,
        String unit,
        Integer durationMinutes,
        Integer sortOrder,
        ServiceStatus status
) {
}
