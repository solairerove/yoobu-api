package com.yoobu.api.catalog;

import com.yoobu.api.audit.AuditLogService;
import com.yoobu.api.catalog.dto.AdminUpsertServiceRequest;
import com.yoobu.api.catalog.dto.ServiceResponse;
import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantContext;
import com.yoobu.api.tenant.TenantType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {

    private static final String ENTITY_NAME = "service";

    private final AuditLogService auditLogService;
    private final CatalogServiceRepository catalogServiceRepository;

    @Transactional(readOnly = true)
    public List<ServiceResponse> getAdminServices() {
        requireFoodOrderTenant();
        return catalogServiceRepository.findByTenantIdAndStatusNotOrderBySortOrderAscIdAsc(
                        TenantContext.getRequiredTenantId(), ServiceStatus.DELETED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse getAdminService(Long serviceId) {
        requireFoodOrderTenant();

        CatalogService service = catalogServiceRepository.findByIdAndTenantIdAndStatusNot(
                        serviceId, TenantContext.getRequiredTenantId(), ServiceStatus.DELETED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse createService(AdminUpsertServiceRequest request) {
        Tenant tenant = requireFoodOrderTenant();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CatalogService service = new CatalogService();
        service.setTenant(tenant);
        applyRequest(service, request);
        service.setStatus(resolveUpsertStatus(request.status()));
        service.setDeletedAt(null);
        service.setCreatedAt(now);
        service.setUpdatedAt(now);

        CatalogService savedService = catalogServiceRepository.save(service);
        auditLogService.logCreate(
                tenant.getId(),
                ENTITY_NAME,
                savedService.getId(),
                auditLogService.currentActorId(),
                toAuditSnapshot(savedService)
        );

        return toResponse(savedService);
    }

    @Transactional
    public ServiceResponse updateService(Long serviceId, AdminUpsertServiceRequest request) {
        requireFoodOrderTenant();

        CatalogService service = catalogServiceRepository.findByIdAndTenantIdAndStatusNot(
                        serviceId, TenantContext.getRequiredTenantId(), ServiceStatus.DELETED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        Map<String, Object> oldSnapshot = toAuditSnapshot(service);

        applyRequest(service, request);
        ServiceStatus status = resolveUpsertStatus(request.status());
        service.setStatus(status);
        if (status != ServiceStatus.DELETED) {
            service.setDeletedAt(null);
        }
        service.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        CatalogService savedService = catalogServiceRepository.save(service);
        auditLogService.logUpdate(
                savedService.getTenant().getId(),
                ENTITY_NAME,
                savedService.getId(),
                auditLogService.currentActorId(),
                oldSnapshot,
                toAuditSnapshot(savedService)
        );

        return toResponse(savedService);
    }

    @Transactional
    public void deleteService(Long serviceId) {
        requireFoodOrderTenant();

        CatalogService service = catalogServiceRepository.findByIdAndTenantIdAndStatusNot(
                        serviceId, TenantContext.getRequiredTenantId(), ServiceStatus.DELETED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
        Map<String, Object> oldSnapshot = toAuditSnapshot(service);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        service.setStatus(ServiceStatus.DELETED);
        service.setDeletedAt(now);
        service.setUpdatedAt(now);
        CatalogService savedService = catalogServiceRepository.save(service);
        auditLogService.logAction(
                savedService.getTenant().getId(),
                ENTITY_NAME,
                savedService.getId(),
                "DELETE",
                auditLogService.currentActorId(),
                oldSnapshot,
                toAuditSnapshot(savedService)
        );
    }

    private ServiceStatus resolveUpsertStatus(ServiceStatus status) {
        if (status == null) {
            return ServiceStatus.ACTIVE;
        }
        if (status == ServiceStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use delete endpoint to remove a service");
        }
        return status;
    }

    private Tenant requireFoodOrderTenant() {
        Tenant tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context is not available");
        }
        if (tenant.getType() != TenantType.FOOD_ORDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant does not support food ordering");
        }
        return tenant;
    }

    private void applyRequest(CatalogService service, AdminUpsertServiceRequest request) {
        service.setName(request.name());
        service.setDescription(request.description());
        service.setPrice(request.price());
        service.setUnit(StringUtils.hasText(request.unit()) ? request.unit() : "шт");
        service.setDurationMinutes(request.durationMinutes());
        service.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private ServiceResponse toResponse(CatalogService service) {
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getUnit(),
                service.getDurationMinutes(),
                service.getSortOrder(),
                service.getStatus()
        );
    }

    private Map<String, Object> toAuditSnapshot(CatalogService service) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", service.getId());
        snapshot.put("tenantId", service.getTenant().getId());
        snapshot.put("name", service.getName());
        snapshot.put("description", service.getDescription());
        snapshot.put("price", service.getPrice());
        snapshot.put("unit", service.getUnit());
        snapshot.put("durationMinutes", service.getDurationMinutes());
        snapshot.put("status", service.getStatus());
        snapshot.put("sortOrder", service.getSortOrder());
        snapshot.put("deletedAt", service.getDeletedAt());
        return snapshot;
    }
}
