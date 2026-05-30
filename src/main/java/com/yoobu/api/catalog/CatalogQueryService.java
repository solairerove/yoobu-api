package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.ProductVariantResponse;
import com.yoobu.api.catalog.dto.ServiceResponse;
import com.yoobu.api.config.CacheNames;
import com.yoobu.api.tenant.TenantContext;
import com.yoobu.api.tenant.TenantType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogQueryService {

    private final CatalogServiceRepository catalogServiceRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CatalogServiceMapper catalogServiceMapper;

    @Cacheable(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    public List<ServiceResponse> getActiveServices() {
        Long tenantId = TenantContext.getRequiredTenantId();
        List<CatalogService> services = catalogServiceRepository
                .findByTenantIdAndStatusOrderBySortOrderAscIdAsc(tenantId, ServiceStatus.ACTIVE);

        if (TenantContext.requireCurrentTenant().getType() == TenantType.ECOMMERCE) {
            return services.stream()
                    .map(service -> {
                        List<ProductVariantResponse> variants = productVariantRepository
                                .findByServiceIdOrderBySortOrderAscIdAsc(service.getId())
                                .stream()
                                .map(catalogServiceMapper::toResponse)
                                .toList();
                        return catalogServiceMapper.toResponse(service, variants);
                    })
                    .toList();
        }

        return services.stream()
                .map(catalogServiceMapper::toResponse)
                .toList();
    }
}
