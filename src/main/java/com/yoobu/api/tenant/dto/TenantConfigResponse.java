package com.yoobu.api.tenant.dto;

import com.yoobu.api.tenant.TenantType;

public record TenantConfigResponse(
        String slug,
        String name,
        TenantType type,
        String primaryColor,
        String logoUrl,
        String welcomeMessage,
        String checkoutPhoneHint,
        String checkoutNoteHint
) {
}
