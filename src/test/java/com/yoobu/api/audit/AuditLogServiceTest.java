package com.yoobu.api.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoobu.api.audit.dto.AuditLogItemResponse;
import com.yoobu.api.booking.BookingStatus;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditLogServiceTest {

    private final AuditLogRepositoryStub repository = new AuditLogRepositoryStub();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuditLogService service = new AuditLogService(repository.proxy, objectMapper);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchNormalizesPageBoundsAndCapsPageSizeToFifty() {
        service.search(10L, " booking ", " UPDATE ", " actor ", null, null, -5, 999);

        assertEquals(0, repository.lastPageable.getPageNumber());
        assertEquals(50, repository.lastPageable.getPageSize());
        assertEquals("createdAt: DESC,id: DESC", repository.lastPageable.getSort().toString());
    }

    @Test
    void searchForExportCapsSizeToFiveThousandAndUsesFirstPage() {
        service.searchForExport(10L, null, null, null, null, null, 100_000);

        assertEquals(0, repository.lastPageable.getPageNumber());
        assertEquals(5000, repository.lastPageable.getPageSize());
    }

    @Test
    void currentActorIdUsesNumericAndStringPrincipals() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(123L, "n/a")
        );
        assertEquals("123", service.currentActorId());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin-1", "n/a")
        );
        assertEquals("admin-1", service.currentActorId());

        SecurityContextHolder.clearContext();
        assertNull(service.currentActorId());
    }

    @Test
    void logActionSerializesEnumsAndTemporalValuesAsStrings() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.of(2026, 3, 20, 12, 15, 0, 0, ZoneOffset.UTC);
        Map<String, Object> oldPayload = Map.of("status", BookingStatus.NEW);
        Map<String, Object> newPayload = new LinkedHashMap<>();
        newPayload.put("status", BookingStatus.CONFIRMED);
        newPayload.put("updatedAt", timestamp);
        newPayload.put("history", List.of(BookingStatus.NEW, BookingStatus.PAYMENT_PENDING));

        service.logAction(5L, "booking", 99L, "UPDATE_STATUS", "admin-1", oldPayload, newPayload);

        AuditLog saved = repository.savedLogs.getLast();
        Map<?, ?> oldJson = objectMapper.readValue(saved.getOldValue(), Map.class);
        Map<?, ?> newJson = objectMapper.readValue(saved.getNewValue(), Map.class);
        assertEquals("NEW", oldJson.get("status"));
        assertEquals("CONFIRMED", newJson.get("status"));
        assertEquals(timestamp.toString(), newJson.get("updatedAt"));
        assertEquals(List.of("NEW", "PAYMENT_PENDING"), newJson.get("history"));
    }

    @Test
    void searchParsesNestedJsonStoredAsText() {
        AuditLog log = instantiate(AuditLog.class);
        log.setId(10L);
        log.setTenantId(1L);
        log.setEntity("booking");
        log.setEntityId(22L);
        log.setAction("UPDATE_STATUS");
        log.setActorId("admin");
        log.setOldValue("{\"status\":\"NEW\"}");
        log.setNewValue("\"{\\\"status\\\":\\\"CONFIRMED\\\"}\"");
        log.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.nextFindAllResult = List.of(log);

        Page<AuditLogItemResponse> page = service.search(null, null, null, null, null, null, 0, 20);
        Object parsed = page.getContent().getFirst().newValue();

        Map<?, ?> parsedMap = assertInstanceOf(Map.class, parsed);
        assertEquals("CONFIRMED", parsedMap.get("status"));
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot instantiate " + type.getName(), ex);
        }
    }

    private static final class AuditLogRepositoryStub {
        private final List<AuditLog> savedLogs = new java.util.ArrayList<>();
        private List<AuditLog> nextFindAllResult = List.of();
        private Pageable lastPageable;
        private Specification<AuditLog> lastSpec;
        private final AuditLogRepository proxy = (AuditLogRepository) Proxy.newProxyInstance(
                AuditLogRepository.class.getClassLoader(),
                new Class<?>[]{AuditLogRepository.class},
                (obj, method, args) -> switch (method.getName()) {
                    case "save" -> save((AuditLog) args[0]);
                    case "findAll" -> findAll(args[0], args[1]);
                    case "toString" -> "AuditLogRepositoryStub";
                    default -> throw new UnsupportedOperationException("Unexpected call: " + method.getName());
                }
        );

        private AuditLog save(AuditLog auditLog) {
            if (auditLog.getId() == null) {
                auditLog.setId((long) (savedLogs.size() + 1));
            }
            savedLogs.add(auditLog);
            return auditLog;
        }

        @SuppressWarnings("unchecked")
        private Page<AuditLog> findAll(Object spec, Object pageable) {
            lastSpec = (Specification<AuditLog>) spec;
            lastPageable = (Pageable) pageable;
            return new PageImpl<>(nextFindAllResult, lastPageable, nextFindAllResult.size());
        }
    }
}
