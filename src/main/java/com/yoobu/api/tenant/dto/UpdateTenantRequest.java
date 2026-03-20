package com.yoobu.api.tenant.dto;

import com.yoobu.api.tenant.TenantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateTenantRequest(
        @NotBlank String name,
        @NotNull TenantType type,
        String botToken,
        Long ownerTelegramId,
        String timezone,
        String currency,
        String primaryColor,
        String logoUrl,
        String welcomeMessage,
        String checkoutNameHint,
        String checkoutPhoneHint,
        String checkoutNoteHint,
        String paymentQrUrl,
        @NotBlank String adminUsername,
        String adminPassword,
        @NotNull Boolean active
) {
}
