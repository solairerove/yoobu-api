package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.ServiceResponse;
import com.yoobu.api.config.CacheNames;
import com.yoobu.api.tenant.TenantContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogQueryService {

    private final CatalogServiceRepository catalogServiceRepository;
    private final CatalogServiceMapper catalogServiceMapper;

    @Cacheable(value = CacheNames.TENANT_SERVICES, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    public List<ServiceResponse> getActiveServices() {
        return catalogServiceRepository.findByTenantIdAndStatusOrderBySortOrderAscIdAsc(
                        TenantContext.getRequiredTenantId(), ServiceStatus.ACTIVE)
                .stream()
                .map(catalogServiceMapper::toResponse)
                .toList();
    }
}
