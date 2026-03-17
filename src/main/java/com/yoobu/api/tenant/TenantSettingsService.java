package com.yoobu.api.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantSettingsService {

    private final TenantConfigRepository tenantConfigRepository;

    public TenantSettings getSettings(Long tenantId) {
        return TenantSettings.fromEntries(tenantConfigRepository.findByTenantId(tenantId));
    }

    public TenantSettings getCurrentTenantSettings() {
        return getSettings(TenantContext.getRequiredTenantId());
    }
}
