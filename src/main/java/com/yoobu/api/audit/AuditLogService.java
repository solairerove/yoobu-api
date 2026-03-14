package com.yoobu.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.time.temporal.TemporalAccessor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void logCreate(Long tenantId, String entity, Long entityId, String actorId, Object newValue) {
        log(tenantId, entity, entityId, "CREATE", actorId, null, newValue);
    }

    public void logUpdate(Long tenantId, String entity, Long entityId, String actorId, Object oldValue, Object newValue) {
        log(tenantId, entity, entityId, "UPDATE", actorId, oldValue, newValue);
    }

    public void logAction(
            Long tenantId,
            String entity,
            Long entityId,
            String action,
            String actorId,
            Object oldValue,
            Object newValue
    ) {
        log(tenantId, entity, entityId, action, actorId, oldValue, newValue);
    }

    public String currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return Long.toString(number.longValue());
        }

        if (principal instanceof String text) {
            return text;
        }

        return null;
    }

    private void log(
            Long tenantId,
            String entity,
            Long entityId,
            String action,
            String actorId,
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
            return objectMapper.writeValueAsString(normalize(value));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize audit payload", ex);
        }
    }

    private Object normalize(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }

        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }

        if (value instanceof TemporalAccessor) {
            return value.toString();
        }

        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            mapValue.forEach((key, nestedValue) -> normalized.put(String.valueOf(key), normalize(nestedValue)));
            return normalized;
        }

        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            iterable.forEach(item -> normalized.add(normalize(item)));
            return normalized;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> normalized = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                normalized.add(normalize(Array.get(value, index)));
            }
            return normalized;
        }

        return String.valueOf(value);
    }
}
