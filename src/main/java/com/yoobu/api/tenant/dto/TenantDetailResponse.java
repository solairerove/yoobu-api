package com.yoobu.api.tenant.dto;

import com.yoobu.api.tenant.TenantType;
import java.time.OffsetDateTime;
import java.util.Map;

public record TenantDetailResponse(
        Long id,
        String slug,
        String name,
        TenantType type,
        boolean active,
        String timezone,
        String botToken,
        Long ownerTelegramId,
        OffsetDateTime createdAt,
        Map<String, String> config
) {
}
