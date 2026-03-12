package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.ServiceResponse;
import com.yoobu.api.tenant.TenantContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogQueryService {

    private final CatalogServiceRepository catalogServiceRepository;

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
