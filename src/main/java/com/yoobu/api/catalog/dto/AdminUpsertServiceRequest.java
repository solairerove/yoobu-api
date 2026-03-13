package com.yoobu.api.catalog.dto;

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
        Boolean active
) {
}
