package com.yoobu.api.admin.panel;

import com.yoobu.api.tenant.TenantManagementService;
import com.yoobu.api.tenant.TenantConfigKeys;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import com.yoobu.api.tenant.dto.TenantDetailResponse;
import com.yoobu.api.tenant.dto.UpdateTenantRequest;
import jakarta.validation.Valid;
import java.util.Map;
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

    private static final String TENANTS_REDIRECT = "redirect:/superadmin/panel/tenants";
    private static final String TENANTS_VIEW = "superadmin/panel/tenants";
    private static final String TENANT_DETAIL_VIEW = "superadmin/panel/tenant-detail";
    private static final String TENANT_FORM_VIEW = "superadmin/panel/tenant-form";
    private static final String CREATE_MODE = "create";
    private static final String EDIT_MODE = "edit";
    private final TenantManagementService tenantManagementService;

    @GetMapping({"", "/"})
    public String panelHome() {
        return TENANTS_REDIRECT;
    }

    @GetMapping("/tenants")
    public String tenants(Model model) {
        model.addAttribute("tenants", tenantManagementService.getAllTenants());
        return TENANTS_VIEW;
    }

    @GetMapping("/tenants/{tenantId}")
    public String tenantDetail(@PathVariable Long tenantId, Model model) {
        model.addAttribute("tenant", tenantManagementService.getTenant(tenantId));
        return TENANT_DETAIL_VIEW;
    }

    @GetMapping("/tenants/new")
    public String newTenant(Model model) {
        model.addAttribute("tenantForm", new SuperAdminTenantForm());
        model.addAttribute("tenantTypes", TenantType.values());
        model.addAttribute("formMode", CREATE_MODE);
        return TENANT_FORM_VIEW;
    }

    @PostMapping("/tenants")
    public String createTenant(
            @Valid @ModelAttribute("tenantForm") SuperAdminTenantForm form,
            BindingResult bindingResult,
            Model model
    ) {
        validateCreateForm(form, bindingResult);

        if (bindingResult.hasErrors()) {
            return populateFormModel(model, CREATE_MODE);
        }

        try {
            tenantManagementService.createTenant(toRequest(form));
        } catch (ResponseStatusException ex) {
            return formError(model, CREATE_MODE, ex);
        }

        return TENANTS_REDIRECT;
    }

    @GetMapping("/tenants/{tenantId}/edit")
    public String editTenant(@PathVariable Long tenantId, Model model) {
        model.addAttribute("tenantForm", toForm(tenantManagementService.getTenant(tenantId)));
        model.addAttribute("tenantId", tenantId);
        return populateFormModel(model, EDIT_MODE);
    }

    @PostMapping("/tenants/{tenantId}")
    public String updateTenant(
            @PathVariable Long tenantId,
            @Valid @ModelAttribute("tenantForm") SuperAdminTenantForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return populateEditFormModel(model, tenantId);
        }

        try {
            tenantManagementService.updateTenant(tenantId, toUpdateRequest(form));
        } catch (ResponseStatusException ex) {
            return formError(populateTenantId(model, tenantId), EDIT_MODE, ex);
        }

        return tenantDetailRedirect(tenantId);
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
        Map<String, String> config = tenant.config();
        SuperAdminTenantForm form = new SuperAdminTenantForm();
        form.setSlug(tenant.slug());
        form.setName(tenant.name());
        form.setType(tenant.type());
        form.setBotToken(tenant.botToken());
        form.setOwnerTelegramId(tenant.ownerTelegramId());
        form.setTimezone(tenant.timezone());
        form.setPrimaryColor(config.get(TenantConfigKeys.PRIMARY_COLOR));
        form.setLogoUrl(config.get(TenantConfigKeys.LOGO_URL));
        form.setWelcomeMessage(config.get(TenantConfigKeys.WELCOME_MESSAGE));
        form.setAdminUsername(config.get(TenantConfigKeys.ADMIN_USERNAME));
        form.setActive(tenant.active());
        return form;
    }

    private String populateFormModel(Model model, String formMode) {
        model.addAttribute("tenantTypes", TenantType.values());
        model.addAttribute("formMode", formMode);
        return TENANT_FORM_VIEW;
    }

    private void validateCreateForm(SuperAdminTenantForm form, BindingResult bindingResult) {
        if (!StringUtils.hasText(form.getAdminPassword())) {
            bindingResult.rejectValue("adminPassword", "tenantForm.adminPassword", "must not be blank");
        }
        if (StringUtils.hasText(form.getSlug()) && !tenantManagementService.isSlugAvailable(form.getSlug())) {
            bindingResult.rejectValue("slug", "tenantForm.slug", "Tenant slug already exists");
        }
    }

    private String formError(Model model, String formMode, ResponseStatusException ex) {
        model.addAttribute("formError", ex.getReason());
        return populateFormModel(model, formMode);
    }

    private String populateEditFormModel(Model model, Long tenantId) {
        return populateFormModel(populateTenantId(model, tenantId), EDIT_MODE);
    }

    private Model populateTenantId(Model model, Long tenantId) {
        model.addAttribute("tenantId", tenantId);
        return model;
    }

    private String tenantDetailRedirect(Long tenantId) {
        return TENANTS_REDIRECT + "/" + tenantId;
    }
}
