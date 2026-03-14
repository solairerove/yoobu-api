package com.yoobu.api.admin.panel;

import com.yoobu.api.tenant.TenantManagementService;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/superadmin/panel")
public class SuperAdminPanelController {

    private final TenantManagementService tenantManagementService;

    @GetMapping({"", "/"})
    public String panelHome() {
        return "redirect:/superadmin/panel/tenants";
    }

    @GetMapping("/tenants")
    public String tenants(Model model) {
        model.addAttribute("tenants", tenantManagementService.getAllTenants());
        return "superadmin/panel/tenants";
    }

    @GetMapping("/tenants/new")
    public String newTenant(Model model) {
        model.addAttribute("tenantForm", new SuperAdminTenantForm());
        model.addAttribute("tenantTypes", TenantType.values());
        return "superadmin/panel/tenant-form";
    }

    @PostMapping("/tenants")
    public String createTenant(
            @Valid @ModelAttribute("tenantForm") SuperAdminTenantForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("tenantTypes", TenantType.values());
            return "superadmin/panel/tenant-form";
        }

        try {
            tenantManagementService.createTenant(toRequest(form));
        } catch (ResponseStatusException ex) {
            model.addAttribute("tenantTypes", TenantType.values());
            model.addAttribute("formError", ex.getReason());
            return "superadmin/panel/tenant-form";
        }

        return "redirect:/superadmin/panel/tenants";
    }

    private CreateTenantRequest toRequest(SuperAdminTenantForm form) {
        return new CreateTenantRequest(
                form.getSlug(),
                form.getName(),
                form.getType(),
                form.getBotToken(),
                form.getOwnerTelegramId(),
                form.getTimezone(),
                form.getPrimaryColor(),
                form.getLogoUrl(),
                form.getWelcomeMessage(),
                form.getAdminUsername(),
                form.getAdminPassword()
        );
    }
}
