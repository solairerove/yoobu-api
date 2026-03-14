package com.yoobu.api.admin;

import com.yoobu.api.tenant.TenantManagementService;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import com.yoobu.api.tenant.dto.TenantDetailResponse;
import com.yoobu.api.tenant.dto.TenantSlugAvailabilityResponse;
import com.yoobu.api.tenant.dto.TenantSummaryResponse;
import com.yoobu.api.tenant.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/{tenantId}")
    public TenantDetailResponse getTenant(@PathVariable Long tenantId) {
        return tenantManagementService.getTenant(tenantId);
    }

    @GetMapping("/slug-availability")
    public TenantSlugAvailabilityResponse getSlugAvailability(@RequestParam String slug) {
        return new TenantSlugAvailabilityResponse(slug, tenantManagementService.isSlugAvailable(slug));
    }

    @PostMapping
    public TenantSummaryResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return tenantManagementService.createTenant(request);
    }

    @PutMapping("/{tenantId}")
    public TenantSummaryResponse updateTenant(
            @PathVariable Long tenantId,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        return tenantManagementService.updateTenant(tenantId, request);
    }
}
