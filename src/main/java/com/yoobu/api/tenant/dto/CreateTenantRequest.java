package com.yoobu.api.tenant.dto;

import com.yoobu.api.tenant.TenantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTenantRequest(
        @NotBlank String slug,
        @NotBlank String name,
        @NotNull TenantType type,
        String botToken,
        Long ownerTelegramId,
        String timezone,
        String primaryColor,
        String logoUrl,
        String welcomeMessage,
        @NotBlank String adminUsername,
        @NotBlank String adminPassword
) {
}
