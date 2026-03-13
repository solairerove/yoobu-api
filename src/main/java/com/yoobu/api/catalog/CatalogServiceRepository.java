package com.yoobu.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogServiceRepository extends JpaRepository<CatalogService, Long> {

    List<CatalogService> findByTenantIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long tenantId);

    List<CatalogService> findByTenantIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long tenantId);

    Optional<CatalogService> findByIdAndTenantIdAndActiveTrueAndDeletedAtIsNull(Long id, Long tenantId);

    Optional<CatalogService> findByIdAndTenantIdAndDeletedAtIsNull(Long id, Long tenantId);
}
