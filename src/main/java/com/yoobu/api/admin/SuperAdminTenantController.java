package com.yoobu.api.admin;

import com.yoobu.api.tenant.TenantManagementService;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import com.yoobu.api.tenant.dto.TenantSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/superadmin/tenants")
public class SuperAdminTenantController {

    private final TenantManagementService tenantManagementService;

    @GetMapping
    public List<TenantSummaryResponse> getTenants() {
        return tenantManagementService.getAllTenants();
    }

    @PostMapping
    public TenantSummaryResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return tenantManagementService.createTenant(request);
    }
}
