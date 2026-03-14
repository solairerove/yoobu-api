package com.yoobu.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogServiceRepository extends JpaRepository<CatalogService, Long> {

    List<CatalogService> findByTenantIdAndStatusOrderBySortOrderAscIdAsc(Long tenantId, ServiceStatus status);

    List<CatalogService> findByTenantIdAndStatusNotOrderBySortOrderAscIdAsc(Long tenantId, ServiceStatus status);

    Optional<CatalogService> findByIdAndTenantIdAndStatus(Long id, Long tenantId, ServiceStatus status);

    Optional<CatalogService> findByIdAndTenantIdAndStatusNot(Long id, Long tenantId, ServiceStatus status);
}
