package com.yoobu.api.tenant;

import com.yoobu.api.tenant.dto.TenantConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantConfigService {

    private final TenantSettingsService tenantSettingsService;
    private final TenantMapper tenantMapper;

    public TenantConfigResponse getCurrentTenantConfig() {
        Tenant tenant = TenantContext.requireCurrentTenant();
        TenantSettings settings = tenantSettingsService.getCurrentTenantSettings();

        return tenantMapper.toConfigResponse(tenant, settings);
    }
}
