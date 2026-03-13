package com.yoobu.api.tenant.dto;

import com.yoobu.api.tenant.TenantType;
import java.time.OffsetDateTime;

public record TenantSummaryResponse(
        Long id,
        String slug,
        String name,
        TenantType type,
        boolean active,
        String timezone,
        OffsetDateTime createdAt
) {
}
