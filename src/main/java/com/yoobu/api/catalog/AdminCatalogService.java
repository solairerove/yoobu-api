package com.yoobu.api.catalog;

import com.yoobu.api.audit.AuditLogService;
import com.yoobu.api.catalog.dto.AdminUpsertServiceRequest;
import com.yoobu.api.catalog.dto.AdminUpsertVariantRequest;
import com.yoobu.api.catalog.dto.ProductVariantImageResponse;
import com.yoobu.api.catalog.dto.ProductVariantResponse;
import com.yoobu.api.catalog.dto.ServiceResponse;
import com.yoobu.api.config.CacheNames;
import com.yoobu.api.media.ImageServiceClient;
import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantContext;
import com.yoobu.api.tenant.TenantType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {

    private static final String ENTITY_NAME = "service";
    private static final String VARIANT_ENTITY_NAME = "product_variant";
    private static final int MAX_VARIANT_IMAGES = 5;

    private final AuditLogService auditLogService;
    private final CatalogServiceRepository catalogServiceRepository;
    private final CatalogServiceMapper catalogServiceMapper;
    private final ProductVariantRepository productVariantRepository;
    private final ProductVariantImageRepository productVariantImageRepository;
    private final ImageServiceClient imageServiceClient;

    @Transactional(readOnly = true)
    public List<ServiceResponse> getAdminServices() {
        requireCatalogTenant();
        return catalogServiceRepository.findByTenantIdAndStatusNotOrderBySortOrderAscIdAsc(
                        TenantContext.getRequiredTenantId(), ServiceStatus.DELETED)
                .stream()
                .map(catalogServiceMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ServiceResponse> getAdminServicesPage(String query, int page, int size) {
        requireCatalogTenant();

        String normalizedQuery = normalizeOptional(query);
        var pageable = PageRequest.of(
                Math.max(page, 0),
                normalizePageSize(size),
                Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "id"))
        );

        Page<CatalogService> servicePage = StringUtils.hasText(normalizedQuery)
                ? catalogServiceRepository.findByTenantIdAndStatusNotAndNameContainingIgnoreCase(
                        TenantContext.getRequiredTenantId(),
                        ServiceStatus.DELETED,
                        normalizedQuery,
                        pageable
                )
                : catalogServiceRepository.findByTenantIdAndStatusNot(
                        TenantContext.getRequiredTenantId(),
                        ServiceStatus.DELETED,
                        pageable
                );

        return servicePage.map(catalogServiceMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ServiceResponse getAdminService(Long serviceId) {
        requireCatalogTenant();

        CatalogService service = findServiceForTenant(serviceId);
        List<ProductVariantResponse> variants = productVariantRepository
                .findByServiceIdOrderBySortOrderAscIdAsc(serviceId)
                .stream()
                .map(catalogServiceMapper::toResponse)
                .toList();
        return catalogServiceMapper.toResponse(service, variants);
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public ServiceResponse createService(AdminUpsertServiceRequest request) {
        Tenant tenant = requireCatalogTenant();
        requirePriceForFoodOrder(tenant, request.price());
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

        return catalogServiceMapper.toResponse(savedService);
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public ServiceResponse updateService(Long serviceId, AdminUpsertServiceRequest request) {
        Tenant tenant = requireCatalogTenant();
        requirePriceForFoodOrder(tenant, request.price());

        CatalogService service = findServiceForTenant(serviceId);
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

        return catalogServiceMapper.toResponse(savedService);
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public ServiceResponse uploadServiceImage(Long serviceId, MultipartFile file) {
        requireCatalogTenant();

        CatalogService service = findServiceForTenant(serviceId);
        String oldImageUrl = service.getImageUrl();
        String cdnUrl = imageServiceClient.upload(service.getTenant().getId(), "services/" + serviceId, file);

        service.setImageUrl(cdnUrl);
        service.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        CatalogService saved = catalogServiceRepository.save(service);

        if (oldImageUrl != null) {
            imageServiceClient.deleteByUrl(oldImageUrl);
        }

        auditLogService.logAction(
                saved.getTenant().getId(),
                ENTITY_NAME,
                saved.getId(),
                "UPLOAD_IMAGE",
                auditLogService.currentActorId(),
                Map.of("imageUrl", String.valueOf(oldImageUrl)),
                Map.of("imageUrl", cdnUrl)
        );

        return catalogServiceMapper.toResponse(saved);
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public void deleteService(Long serviceId) {
        requireCatalogTenant();

        CatalogService service = findServiceForTenant(serviceId);
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
        imageServiceClient.deleteByUrl(service.getImageUrl());
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public ProductVariantResponse addVariantImage(Long serviceId, Long variantId, MultipartFile file) {
        Tenant tenant = requireCatalogTenant();
        ProductVariant variant = findVariantForService(serviceId, variantId);

        long existingCount = productVariantImageRepository.countByVariantId(variantId);
        if (existingCount >= MAX_VARIANT_IMAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_VARIANT_IMAGES + " images per variant");
        }

        String cdnUrl = imageServiceClient.upload(tenant.getId(), "variants/" + variantId + "/gallery", file);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ProductVariantImage image = ProductVariantImage.of(variant, cdnUrl, (int) existingCount, now);
        productVariantImageRepository.save(image);

        if (existingCount == 0) {
            variant.setImageUrl(cdnUrl);
            variant.setUpdatedAt(now);
            productVariantRepository.save(variant);
        }

        auditLogService.logAction(
                tenant.getId(),
                VARIANT_ENTITY_NAME,
                variantId,
                "ADD_IMAGE",
                auditLogService.currentActorId(),
                Map.of(),
                Map.of("imageUrl", cdnUrl)
        );

        return catalogServiceMapper.toResponse(findVariantForService(serviceId, variantId));
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public void deleteVariantImage(Long serviceId, Long variantId, Long imageId) {
        Tenant tenant = requireCatalogTenant();
        ProductVariant variant = findVariantForService(serviceId, variantId);

        ProductVariantImage image = productVariantImageRepository.findByIdAndVariantId(imageId, variantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));

        String deletedUrl = image.getImageUrl();
        productVariantImageRepository.delete(image);
        imageServiceClient.deleteByUrl(deletedUrl);

        productVariantImageRepository.findFirstByVariantIdOrderBySortOrderAscIdAsc(variantId)
                .ifPresentOrElse(
                        first -> {
                            variant.setImageUrl(first.getImageUrl());
                            variant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                            productVariantRepository.save(variant);
                        },
                        () -> {
                            variant.setImageUrl(null);
                            variant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                            productVariantRepository.save(variant);
                        }
                );

        auditLogService.logAction(
                tenant.getId(),
                VARIANT_ENTITY_NAME,
                variantId,
                "DELETE_IMAGE",
                auditLogService.currentActorId(),
                Map.of("imageUrl", deletedUrl),
                Map.of()
        );
    }

    @Transactional(readOnly = true)
    public List<ProductVariantImageResponse> getVariantImages(Long serviceId, Long variantId) {
        requireCatalogTenant();
        findVariantForService(serviceId, variantId);
        return productVariantImageRepository.findByVariantIdOrderBySortOrderAscIdAsc(variantId)
                .stream()
                .map(img -> new ProductVariantImageResponse(img.getId(), img.getImageUrl()))
                .toList();
    }

    // --- Variant management ---

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getVariants(Long serviceId) {
        requireCatalogTenant();
        findServiceForTenant(serviceId);
        return productVariantRepository.findByServiceIdOrderBySortOrderAscIdAsc(serviceId)
                .stream()
                .map(catalogServiceMapper::toResponse)
                .toList();
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public ProductVariantResponse createVariant(Long serviceId, AdminUpsertVariantRequest request) {
        Tenant tenant = requireCatalogTenant();
        CatalogService service = findServiceForTenant(serviceId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ProductVariant variant = new ProductVariant();
        variant.setService(service);
        applyVariantRequest(variant, request);
        variant.setCreatedAt(now);
        variant.setUpdatedAt(now);

        ProductVariant saved = productVariantRepository.save(variant);
        auditLogService.logCreate(
                tenant.getId(),
                VARIANT_ENTITY_NAME,
                saved.getId(),
                auditLogService.currentActorId(),
                toVariantAuditSnapshot(saved)
        );

        return catalogServiceMapper.toResponse(saved);
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public ProductVariantResponse updateVariant(Long serviceId, Long variantId, AdminUpsertVariantRequest request) {
        Tenant tenant = requireCatalogTenant();
        ProductVariant variant = findVariantForService(serviceId, variantId);
        Map<String, Object> oldSnapshot = toVariantAuditSnapshot(variant);

        applyVariantRequest(variant, request);
        variant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        ProductVariant saved = productVariantRepository.save(variant);
        auditLogService.logUpdate(
                tenant.getId(),
                VARIANT_ENTITY_NAME,
                saved.getId(),
                auditLogService.currentActorId(),
                oldSnapshot,
                toVariantAuditSnapshot(saved)
        );

        return catalogServiceMapper.toResponse(saved);
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public void deleteVariant(Long serviceId, Long variantId) {
        Tenant tenant = requireCatalogTenant();
        ProductVariant variant = findVariantForService(serviceId, variantId);
        Map<String, Object> snapshot = toVariantAuditSnapshot(variant);

        List<String> imageUrls = productVariantImageRepository
                .findByVariantIdOrderBySortOrderAscIdAsc(variantId)
                .stream()
                .map(ProductVariantImage::getImageUrl)
                .toList();

        productVariantRepository.delete(variant);
        imageUrls.forEach(imageServiceClient::deleteByUrl);

        auditLogService.logAction(
                tenant.getId(),
                VARIANT_ENTITY_NAME,
                variantId,
                "DELETE",
                auditLogService.currentActorId(),
                snapshot,
                Map.of()
        );
    }

    @CacheEvict(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    @Transactional
    public ProductVariantResponse adjustStock(Long serviceId, Long variantId, int delta) {
        Tenant tenant = requireCatalogTenant();
        ProductVariant variant = findVariantForService(serviceId, variantId);
        Map<String, Object> oldSnapshot = toVariantAuditSnapshot(variant);

        int newStock = variant.getStock() + delta;
        if (newStock < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock cannot go below zero");
        }

        variant.setStock(newStock);
        variant.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        ProductVariant saved = productVariantRepository.save(variant);
        auditLogService.logAction(
                tenant.getId(),
                VARIANT_ENTITY_NAME,
                saved.getId(),
                "ADJUST_STOCK",
                auditLogService.currentActorId(),
                oldSnapshot,
                toVariantAuditSnapshot(saved)
        );

        return catalogServiceMapper.toResponse(saved);
    }

    private CatalogService findServiceForTenant(Long serviceId) {
        return catalogServiceRepository.findByIdAndTenantIdAndStatusNot(
                        serviceId, TenantContext.getRequiredTenantId(), ServiceStatus.DELETED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }

    private ProductVariant findVariantForService(Long serviceId, Long variantId) {
        findServiceForTenant(serviceId);
        return productVariantRepository.findByIdAndServiceId(variantId, serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found"));
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

    private void requirePriceForFoodOrder(Tenant tenant, BigDecimal price) {
        if (tenant.getType() == TenantType.FOOD_ORDER && price == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price is required for food order services");
        }
    }

    private Tenant requireCatalogTenant() {
        Tenant tenant = TenantContext.requireCurrentTenant();
        if (tenant.getType() != TenantType.FOOD_ORDER && tenant.getType() != TenantType.ECOMMERCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant does not support catalog management");
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

    private void applyVariantRequest(ProductVariant variant, AdminUpsertVariantRequest request) {
        variant.setSize(normalizeOptional(request.size()));
        variant.setColor(normalizeOptional(request.color()));
        variant.setPrice(request.price());
        variant.setStock(request.stock());
        variant.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
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
        snapshot.put("imageUrl", service.getImageUrl());
        snapshot.put("deletedAt", service.getDeletedAt());
        return snapshot;
    }

    private Map<String, Object> toVariantAuditSnapshot(ProductVariant variant) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", variant.getId());
        snapshot.put("serviceId", variant.getService().getId());
        snapshot.put("size", variant.getSize());
        snapshot.put("color", variant.getColor());
        snapshot.put("price", variant.getPrice());
        snapshot.put("stock", variant.getStock());
        snapshot.put("sortOrder", variant.getSortOrder());
        snapshot.put("imageUrl", variant.getImageUrl());
        return snapshot;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int normalizePageSize(int requestedSize) {
        if (requestedSize < 1) {
            return 20;
        }
        return Math.min(requestedSize, 100);
    }
}
