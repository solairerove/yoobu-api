package com.yoobu.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoobu.api.audit.dto.AuditLogItemResponse;
import java.lang.reflect.Array;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public Page<AuditLogItemResponse> search(
            Long tenantId,
            String entity,
            String action,
            String actorId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            int page,
            int size
    ) {
        var pageable = PageRequest.of(
                Math.max(page, 0),
                normalizePageSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<AuditLog> logs = auditLogRepository.findAll(
                buildSearchSpec(
                        tenantId,
                        normalizeOptional(entity),
                        normalizeOptional(action),
                        normalizeOptional(actorId),
                        createdFrom,
                        createdTo
                ),
                pageable
        );

        List<AuditLogItemResponse> items = logs.stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(items, pageable, logs.getTotalElements());
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

    private AuditLogItemResponse toResponse(AuditLog log) {
        return new AuditLogItemResponse(
                log.getId(),
                log.getTenantId(),
                log.getEntity(),
                log.getEntityId(),
                log.getAction(),
                log.getActorId(),
                parseJson(log.getOldValue()),
                parseJson(log.getNewValue()),
                log.getCreatedAt()
        );
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

    private Object parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            if (parsed instanceof String nested) {
                String trimmedNested = nested.trim();
                if (trimmedNested.startsWith("{") || trimmedNested.startsWith("[")) {
                    return objectMapper.readValue(trimmedNested, Object.class);
                }
            }
            return parsed;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse audit payload", ex);
        }
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize < 1) {
            return 20;
        }
        return Math.min(requestedSize, 50);
    }

    private Specification<AuditLog> buildSearchSpec(
            Long tenantId,
            String entity,
            String action,
            String actorId,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo
    ) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (StringUtils.hasText(entity)) {
                predicates.add(cb.equal(root.get("entity"), entity));
            }
            if (StringUtils.hasText(action)) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (StringUtils.hasText(actorId)) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
