package com.yoobu.api.tenant;

import com.yoobu.api.tenant.dto.TenantConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/t/{slug}")
public class TenantPublicController {

    private final TenantConfigService tenantConfigService;

    public TenantPublicController(TenantConfigService tenantConfigService) {
        this.tenantConfigService = tenantConfigService;
    }

    @GetMapping("/config")
    public TenantConfigResponse getConfig() {
        return tenantConfigService.getCurrentTenantConfig();
    }
}
