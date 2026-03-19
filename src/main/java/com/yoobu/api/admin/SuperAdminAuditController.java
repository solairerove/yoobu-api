package com.yoobu.api.admin;

import com.yoobu.api.audit.AuditLogService;
import com.yoobu.api.audit.AuditLogCsvExporter;
import com.yoobu.api.audit.dto.AuditLogPageResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/superadmin/audit")
public class SuperAdminAuditController {

    private static final DateTimeFormatter EXPORT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final AuditLogService auditLogService;
    private final AuditLogCsvExporter auditLogCsvExporter;

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

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @RequestParam(defaultValue = "5000") int size
    ) {
        var items = auditLogService.searchForExport(tenantId, entity, action, actorId, createdFrom, createdTo, size);
        String csv = auditLogCsvExporter.toCsv(items);
        String filename = "audit-log-" + OffsetDateTime.now(ZoneOffset.UTC).format(EXPORT_TIMESTAMP_FORMATTER) + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
