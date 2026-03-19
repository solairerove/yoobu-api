package com.yoobu.api.audit;

import com.yoobu.api.audit.dto.AuditLogItemResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogCsvExporter {

    private final AuditLogChangeFormatter changeFormatter;

    public String toCsv(List<AuditLogItemResponse> entries) {
        StringBuilder csv = new StringBuilder();
        csv.append("createdAt,tenantId,entity,entityId,action,actorId,changesSummary\n");
        for (AuditLogItemResponse entry : entries) {
            csv.append(csvValue(entry.createdAt()))
                    .append(',')
                    .append(csvValue(entry.tenantId()))
                    .append(',')
                    .append(csvValue(entry.entity()))
                    .append(',')
                    .append(csvValue(entry.entityId()))
                    .append(',')
                    .append(csvValue(entry.action()))
                    .append(',')
                    .append(csvValue(entry.actorId()))
                    .append(',')
                    .append(csvValue(changeFormatter.summarizeChanges(entry)))
                    .append('\n');
        }
        return csv.toString();
    }

    private String csvValue(Object rawValue) {
        String text = rawValue == null ? "" : String.valueOf(rawValue);
        String safe = sanitizeForSpreadsheet(text);
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String sanitizeForSpreadsheet(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }
}
