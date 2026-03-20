package com.yoobu.api.tenant.dto;

import com.yoobu.api.tenant.TenantType;

public record TenantConfigResponse(
        String slug,
        String name,
        TenantType type,
        String currency,
        String primaryColor,
        String logoUrl,
        String welcomeMessage,
        String checkoutNameHint,
        String checkoutPhoneHint,
        String checkoutNoteHint,
        String paymentQrUrl
) {
}
