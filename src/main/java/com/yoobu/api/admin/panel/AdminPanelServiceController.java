package com.yoobu.api.admin.panel;

import com.yoobu.api.catalog.AdminCatalogService;
import com.yoobu.api.catalog.ServiceStatus;
import com.yoobu.api.catalog.dto.AdminUpsertServiceRequest;
import com.yoobu.api.catalog.dto.ServiceResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/{slug}/panel/services")
public class AdminPanelServiceController {

    private static final String SERVICES_VIEW = "admin/panel/services";
    private static final String SERVICE_FORM_VIEW = "admin/panel/service-form";
    private static final List<ServiceStatus> SERVICE_STATUSES = List.of(ServiceStatus.ACTIVE, ServiceStatus.INACTIVE);
    private static final String NEW_SERVICE_TITLE = "New service";
    private static final String CREATE_SERVICE_LABEL = "Create service";
    private static final String EDIT_SERVICE_TITLE = "Edit service";
    private static final String SAVE_CHANGES_LABEL = "Save changes";

    private final AdminCatalogService adminCatalogService;

    @GetMapping
    public String services(@PathVariable String slug, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("services", adminCatalogService.getAdminServices());
        return SERVICES_VIEW;
    }

    @GetMapping("/new")
    public String newService(@PathVariable String slug, Model model) {
        return serviceFormView(slug, newServiceForm(), model, NEW_SERVICE_TITLE, CREATE_SERVICE_LABEL, null);
    }

    @PostMapping
    public String createService(
            @PathVariable String slug,
            @Valid @ModelAttribute("serviceForm") ServiceForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return serviceFormView(slug, form, model, NEW_SERVICE_TITLE, CREATE_SERVICE_LABEL, null);
        }

        adminCatalogService.createService(toRequest(form));
        return servicesRedirect(slug);
    }

    @GetMapping("/{serviceId}/edit")
    public String editService(@PathVariable String slug, @PathVariable Long serviceId, Model model) {
        ServiceResponse service = adminCatalogService.getAdminService(serviceId);
        return serviceFormView(
                slug,
                fromResponse(service),
                model,
                EDIT_SERVICE_TITLE,
                SAVE_CHANGES_LABEL,
                serviceId
        );
    }

    @PostMapping("/{serviceId}")
    public String updateService(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @Valid @ModelAttribute("serviceForm") ServiceForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return serviceFormView(slug, form, model, EDIT_SERVICE_TITLE, SAVE_CHANGES_LABEL, serviceId);
        }

        adminCatalogService.updateService(serviceId, toRequest(form));
        return servicesRedirect(slug);
    }

    @PostMapping("/{serviceId}/delete")
    public String deleteService(@PathVariable String slug, @PathVariable Long serviceId) {
        adminCatalogService.deleteService(serviceId);
        return servicesRedirect(slug);
    }

    private String serviceFormView(
            String slug,
            ServiceForm form,
            Model model,
            String formTitle,
            String submitLabel,
            Long serviceId
    ) {
        model.addAttribute("slug", slug);
        model.addAttribute("serviceForm", form);
        model.addAttribute("formTitle", formTitle);
        model.addAttribute("submitLabel", submitLabel);
        model.addAttribute("editServiceId", serviceId);
        model.addAttribute("serviceStatuses", SERVICE_STATUSES);
        model.addAttribute("formAction", formAction(slug, serviceId));
        return SERVICE_FORM_VIEW;
    }

    private ServiceForm newServiceForm() {
        ServiceForm form = new ServiceForm();
        form.setSortOrder(0);
        form.setStatus(ServiceStatus.ACTIVE);
        return form;
    }

    private ServiceForm fromResponse(ServiceResponse service) {
        ServiceForm form = new ServiceForm();
        form.setName(service.name());
        form.setDescription(service.description());
        form.setPrice(service.price());
        form.setUnit(service.unit());
        form.setDurationMinutes(service.durationMinutes());
        form.setSortOrder(service.sortOrder());
        form.setStatus(service.status());
        return form;
    }

    private AdminUpsertServiceRequest toRequest(ServiceForm form) {
        return new AdminUpsertServiceRequest(
                form.getName(),
                form.getDescription(),
                form.getPrice(),
                form.getUnit(),
                form.getDurationMinutes(),
                form.getSortOrder(),
                form.getStatus()
        );
    }

    private String servicesRedirect(String slug) {
        return "redirect:/admin/" + slug + "/panel/services";
    }

    private String formAction(String slug, Long serviceId) {
        return serviceId == null ? servicesPath(slug) : servicesPath(slug) + "/" + serviceId;
    }

    private String servicesPath(String slug) {
        return "/admin/" + slug + "/panel/services";
    }
}
