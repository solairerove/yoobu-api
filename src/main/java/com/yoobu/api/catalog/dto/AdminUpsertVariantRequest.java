package com.yoobu.api.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AdminUpsertVariantRequest(
        String size,
        String color,
        @NotNull BigDecimal price,
        @Min(0) int stock,
        Integer sortOrder
) {
}
