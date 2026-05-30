package com.yoobu.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @EntityGraph(attributePaths = "images")
    List<ProductVariant> findByServiceIdOrderBySortOrderAscIdAsc(Long serviceId);

    Optional<ProductVariant> findByIdAndServiceId(Long id, Long serviceId);

    Optional<ProductVariant> findByIdAndServiceTenantId(Long id, Long tenantId);
}
