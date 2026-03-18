package com.yoobu.api.audit.dto;

import java.util.List;

public record AuditLogPageResponse(
        List<AuditLogItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
