package com.yoobu.api.tenant;

import com.yoobu.api.tenant.dto.TenantConfigResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantConfigService {

    private final TenantConfigRepository tenantConfigRepository;

    public TenantConfigResponse getCurrentTenantConfig() {
        Tenant tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context is not available");
        }

        List<TenantConfig> configEntries = tenantConfigRepository.findByTenantId(tenant.getId());
        Map<String, String> config = configEntries.stream()
                .collect(Collectors.toMap(TenantConfig::getKey, TenantConfig::getValue, (left, right) -> right));

        return new TenantConfigResponse(
                tenant.getSlug(),
                tenant.getName(),
                tenant.getType(),
                config.get("primary_color"),
                config.get("logo_url"),
                config.get("welcome_message")
        );
    }
}
