package com.yoobu.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogServiceRepository extends JpaRepository<CatalogService, Long> {

    List<CatalogService> findByTenantIdAndStatusOrderBySortOrderAscIdAsc(Long tenantId, ServiceStatus status);

    List<CatalogService> findByTenantIdAndStatusNotOrderBySortOrderAscIdAsc(Long tenantId, ServiceStatus status);

    Page<CatalogService> findByTenantIdAndStatusNot(Long tenantId, ServiceStatus status, Pageable pageable);

    Page<CatalogService> findByTenantIdAndStatusNotAndNameContainingIgnoreCase(
            Long tenantId,
            ServiceStatus status,
            String name,
            Pageable pageable
    );

    Optional<CatalogService> findByIdAndTenantIdAndStatus(Long id, Long tenantId, ServiceStatus status);

    Optional<CatalogService> findByIdAndTenantIdAndStatusNot(Long id, Long tenantId, ServiceStatus status);
}
