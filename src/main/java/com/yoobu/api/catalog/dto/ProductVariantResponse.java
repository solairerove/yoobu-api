package com.yoobu.api.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductVariantResponse(
        Long id,
        String size,
        String color,
        BigDecimal price,
        int stock,
        int sortOrder,
        String imageUrl,
        List<String> imageUrls
) {
}
