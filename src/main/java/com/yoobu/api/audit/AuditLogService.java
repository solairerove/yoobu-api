package com.yoobu.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void logCreate(Long tenantId, String entity, Long entityId, Long actorId, Object newValue) {
        log(tenantId, entity, entityId, "CREATE", actorId, null, newValue);
    }

    public void logUpdate(Long tenantId, String entity, Long entityId, Long actorId, Object oldValue, Object newValue) {
        log(tenantId, entity, entityId, "UPDATE", actorId, oldValue, newValue);
    }

    public void logAction(
            Long tenantId,
            String entity,
            Long entityId,
            String action,
            Long actorId,
            Object oldValue,
            Object newValue
    ) {
        log(tenantId, entity, entityId, action, actorId, oldValue, newValue);
    }

    public Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }

        if (principal instanceof String text && text.matches("-?\\d+")) {
            return Long.parseLong(text);
        }

        return null;
    }

    private void log(
            Long tenantId,
            String entity,
            Long entityId,
            String action,
            Long actorId,
            Object oldValue,
            Object newValue
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setTenantId(tenantId);
        auditLog.setEntity(entity);
        auditLog.setEntityId(entityId);
        auditLog.setAction(action);
        auditLog.setActorId(actorId);
        auditLog.setOldValue(toJson(oldValue));
        auditLog.setNewValue(toJson(newValue));
        auditLog.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        auditLogRepository.save(auditLog);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize audit payload", ex);
        }
    }
}
