package com.yoobu.api.tenant;

import com.yoobu.api.config.CacheNames;
import com.yoobu.api.tenant.dto.TenantConfigResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantConfigService {

    private final TenantSettingsService tenantSettingsService;
    private final TenantTimeService tenantTimeService;
    private final TenantMapper tenantMapper;

    @Cacheable(value = CacheNames.TENANT_CONFIG, key = "T(com.yoobu.api.tenant.TenantContext).getRequiredTenantId()")
    public TenantConfigResponse getCurrentTenantConfig() {
        Tenant tenant = TenantContext.requireCurrentTenant();
        TenantSettings settings = tenantSettingsService.getCurrentTenantSettings();
        LocalDate earliestDeliveryDate = tenantTimeService.earliestDeliveryDate(tenant, settings);

        return tenantMapper.toConfigResponse(tenant, settings, earliestDeliveryDate);
    }
}
