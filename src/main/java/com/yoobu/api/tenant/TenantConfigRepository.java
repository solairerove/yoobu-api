package com.yoobu.api.tenant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantConfigRepository extends JpaRepository<TenantConfig, Long> {

    List<TenantConfig> findByTenantId(Long tenantId);
}
