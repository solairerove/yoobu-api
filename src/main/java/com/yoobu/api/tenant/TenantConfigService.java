package com.yoobu.api.tenant;

import com.yoobu.api.tenant.dto.TenantConfigResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantConfigService {

    private final TenantConfigRepository tenantConfigRepository;
    private final TenantMapper tenantMapper;

    public TenantConfigResponse getCurrentTenantConfig() {
        Tenant tenant = TenantContext.requireCurrentTenant();
        List<TenantConfig> configEntries = tenantConfigRepository.findByTenantId(tenant.getId());
        TenantSettings settings = TenantSettings.fromEntries(configEntries);

        return tenantMapper.toConfigResponse(tenant, settings);
    }
}
