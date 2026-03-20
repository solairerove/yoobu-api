package com.yoobu.api.audit.dto;

import java.time.OffsetDateTime;

public record AuditLogItemResponse(
        Long id,
        Long tenantId,
        String entity,
        Long entityId,
        String action,
        String actorId,
        Object oldValue,
        Object newValue,
        OffsetDateTime createdAt
) {
}
