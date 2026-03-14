package com.yoobu.api.admin.panel;

import com.yoobu.api.tenant.TenantManagementService;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import com.yoobu.api.tenant.dto.TenantDetailResponse;
import com.yoobu.api.tenant.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/tenants/{tenantId}")
    public String tenantDetail(@org.springframework.web.bind.annotation.PathVariable Long tenantId, Model model) {
        model.addAttribute("tenant", tenantManagementService.getTenant(tenantId));
        return "superadmin/panel/tenant-detail";
    }

    @GetMapping("/tenants/new")
    public String newTenant(Model model) {
        model.addAttribute("tenantForm", new SuperAdminTenantForm());
        model.addAttribute("tenantTypes", TenantType.values());
        model.addAttribute("formMode", "create");
        return "superadmin/panel/tenant-form";
    }

    @PostMapping("/tenants")
    public String createTenant(
            @Valid @ModelAttribute("tenantForm") SuperAdminTenantForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (!StringUtils.hasText(form.getAdminPassword())) {
            bindingResult.rejectValue("adminPassword", "tenantForm.adminPassword", "must not be blank");
        }

        if (bindingResult.hasErrors()) {
            return populateFormModel(model, "create");
        }

        try {
            tenantManagementService.createTenant(toRequest(form));
        } catch (ResponseStatusException ex) {
            model.addAttribute("formError", ex.getReason());
            return populateFormModel(model, "create");
        }

        return "redirect:/superadmin/panel/tenants";
    }

    @GetMapping("/tenants/{tenantId}/edit")
    public String editTenant(@PathVariable Long tenantId, Model model) {
        model.addAttribute("tenantForm", toForm(tenantManagementService.getTenant(tenantId)));
        model.addAttribute("tenantId", tenantId);
        return populateFormModel(model, "edit");
    }

    @PostMapping("/tenants/{tenantId}")
    public String updateTenant(
            @PathVariable Long tenantId,
            @Valid @ModelAttribute("tenantForm") SuperAdminTenantForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("tenantId", tenantId);
            return populateFormModel(model, "edit");
        }

        try {
            tenantManagementService.updateTenant(tenantId, toUpdateRequest(form));
        } catch (ResponseStatusException ex) {
            model.addAttribute("tenantId", tenantId);
            model.addAttribute("formError", ex.getReason());
            return populateFormModel(model, "edit");
        }

        return "redirect:/superadmin/panel/tenants/" + tenantId;
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

    private UpdateTenantRequest toUpdateRequest(SuperAdminTenantForm form) {
        return new UpdateTenantRequest(
                form.getName(),
                form.getType(),
                form.getBotToken(),
                form.getOwnerTelegramId(),
                form.getTimezone(),
                form.getPrimaryColor(),
                form.getLogoUrl(),
                form.getWelcomeMessage(),
                form.getAdminUsername(),
                form.getAdminPassword(),
                form.isActive()
        );
    }

    private SuperAdminTenantForm toForm(TenantDetailResponse tenant) {
        SuperAdminTenantForm form = new SuperAdminTenantForm();
        form.setSlug(tenant.slug());
        form.setName(tenant.name());
        form.setType(tenant.type());
        form.setBotToken(tenant.botToken());
        form.setOwnerTelegramId(tenant.ownerTelegramId());
        form.setTimezone(tenant.timezone());
        form.setPrimaryColor(tenant.config().get("primary_color"));
        form.setLogoUrl(tenant.config().get("logo_url"));
        form.setWelcomeMessage(tenant.config().get("welcome_message"));
        form.setAdminUsername(tenant.config().get("admin_username"));
        form.setActive(tenant.active());
        return form;
    }

    private String populateFormModel(Model model, String formMode) {
        model.addAttribute("tenantTypes", TenantType.values());
        model.addAttribute("formMode", formMode);
        return "superadmin/panel/tenant-form";
    }
}
