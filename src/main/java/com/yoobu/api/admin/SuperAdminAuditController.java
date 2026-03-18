package com.yoobu.api.admin;

import com.yoobu.api.audit.AuditLogService;
import com.yoobu.api.audit.dto.AuditLogPageResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/superadmin/audit")
public class SuperAdminAuditController {

    private final AuditLogService auditLogService;

    @GetMapping
    public AuditLogPageResponse getAuditLogs(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var auditPage = auditLogService.search(tenantId, entity, action, actorId, createdFrom, createdTo, page, size);
        return new AuditLogPageResponse(
                auditPage.getContent(),
                auditPage.getNumber(),
                auditPage.getSize(),
                auditPage.getTotalElements(),
                auditPage.getTotalPages(),
                auditPage.hasNext(),
                auditPage.hasPrevious()
        );
    }
}
