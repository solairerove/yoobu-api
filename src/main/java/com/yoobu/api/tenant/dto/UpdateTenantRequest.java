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
        String bannerUrl,
        String welcomeMessage,
        String checkoutNameHint,
        String checkoutPhoneHint,
        String checkoutNoteHint,
        String checkoutDeliveryHint,
        String paymentQrUrl,
        String paymentBankBin,
        String paymentAccountNumber,
        @NotBlank String adminUsername,
        String adminPassword,
        @NotNull Boolean active,
        Integer cutoffHour,
        Integer cutoffMinute
) {
}
