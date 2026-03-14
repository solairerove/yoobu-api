package com.yoobu.api.tenant.dto;

public record TenantSlugAvailabilityResponse(
        String slug,
        boolean available
) {
}
