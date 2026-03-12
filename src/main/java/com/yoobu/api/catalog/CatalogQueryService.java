package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.ServiceResponse;
import com.yoobu.api.tenant.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CatalogQueryService {

    private final CatalogServiceRepository catalogServiceRepository;

    public CatalogQueryService(CatalogServiceRepository catalogServiceRepository) {
        this.catalogServiceRepository = catalogServiceRepository;
    }

    public List<ServiceResponse> getActiveServices() {
        return catalogServiceRepository.findByTenantIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
                        TenantContext.getRequiredTenantId())
                .stream()
                .map(service -> new ServiceResponse(
                        service.getId(),
                        service.getName(),
                        service.getDescription(),
                        service.getPrice(),
                        service.getUnit(),
                        service.getDurationMinutes(),
                        service.getSortOrder()))
                .toList();
    }
}
