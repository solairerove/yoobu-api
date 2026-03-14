package com.yoobu.api.admin.panel;

import com.yoobu.api.catalog.AdminCatalogService;
import com.yoobu.api.catalog.dto.AdminUpsertServiceRequest;
import com.yoobu.api.catalog.dto.ServiceResponse;
import jakarta.validation.Valid;
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

    private final AdminCatalogService adminCatalogService;

    @GetMapping
    public String services(@PathVariable String slug, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("services", adminCatalogService.getAdminServices());
        return "admin/panel/services";
    }

    @GetMapping("/new")
    public String newService(@PathVariable String slug, Model model) {
        ServiceForm form = new ServiceForm();
        form.setSortOrder(0);
        model.addAttribute("slug", slug);
        model.addAttribute("serviceForm", form);
        model.addAttribute("formTitle", "New service");
        model.addAttribute("submitLabel", "Create service");
        model.addAttribute("formAction", "/admin/" + slug + "/panel/services");
        return "admin/panel/service-form";
    }

    @PostMapping
    public String createService(
            @PathVariable String slug,
            @Valid @ModelAttribute("serviceForm") ServiceForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return serviceFormView(slug, form, model, "New service", "Create service", null);
        }

        adminCatalogService.createService(toRequest(form));
        return "redirect:/admin/" + slug + "/panel/services";
    }

    @GetMapping("/{serviceId}/edit")
    public String editService(@PathVariable String slug, @PathVariable Long serviceId, Model model) {
        ServiceResponse service = adminCatalogService.getAdminService(serviceId);
        return serviceFormView(
                slug,
                fromResponse(service),
                model,
                "Edit service",
                "Save changes",
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
            return serviceFormView(slug, form, model, "Edit service", "Save changes", serviceId);
        }

        adminCatalogService.updateService(serviceId, toRequest(form));
        return "redirect:/admin/" + slug + "/panel/services";
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
        model.addAttribute(
                "formAction",
                serviceId == null
                        ? "/admin/" + slug + "/panel/services"
                        : "/admin/" + slug + "/panel/services/" + serviceId
        );
        return "admin/panel/service-form";
    }

    private ServiceForm fromResponse(ServiceResponse service) {
        ServiceForm form = new ServiceForm();
        form.setName(service.name());
        form.setDescription(service.description());
        form.setPrice(service.price());
        form.setUnit(service.unit());
        form.setDurationMinutes(service.durationMinutes());
        form.setSortOrder(service.sortOrder());
        form.setActive(service.active());
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
                form.isActive()
        );
    }
}
