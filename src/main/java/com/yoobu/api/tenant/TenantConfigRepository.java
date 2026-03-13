package com.yoobu.api.tenant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantConfigRepository extends JpaRepository<TenantConfig, Long> {

    List<TenantConfig> findByTenantId(Long tenantId);

    Optional<TenantConfig> findByTenantIdAndKey(Long tenantId, String key);
}
