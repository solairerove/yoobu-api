package com.yoobu.api.admin.panel;

import com.yoobu.api.catalog.AdminCatalogService;
import com.yoobu.api.catalog.ServiceStatus;
import com.yoobu.api.catalog.dto.AdminUpsertServiceRequest;
import com.yoobu.api.catalog.dto.ServiceResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private static final String STATUS_FORM_ATTRIBUTE = "statusForm";
    private static final String FLASH_TYPE_SUCCESS = "success";
    private static final String FLASH_TYPE_ERROR = "error";

    private final AdminCatalogService adminCatalogService;

    @GetMapping
    public String services(
            @PathVariable String slug,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        populateServicesModel(slug, query, page, size, model);
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
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return serviceFormView(slug, form, model, NEW_SERVICE_TITLE, CREATE_SERVICE_LABEL, null);
        }

        try {
            adminCatalogService.createService(toRequest(form));
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
            return servicesRedirect(slug);
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Service created.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
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
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return serviceFormView(slug, form, model, EDIT_SERVICE_TITLE, SAVE_CHANGES_LABEL, serviceId);
        }

        try {
            adminCatalogService.updateService(serviceId, toRequest(form));
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
            return servicesRedirect(slug);
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Service updated.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return servicesRedirect(slug);
    }

    @PostMapping("/{serviceId}/delete")
    public String deleteService(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @RequestParam(required = false) String confirmName,
            RedirectAttributes redirectAttributes
    ) {
        ServiceResponse service = adminCatalogService.getAdminService(serviceId);
        String normalizedConfirmName = normalize(confirmName);
        if (!StringUtils.hasText(normalizedConfirmName) || !service.name().equals(normalizedConfirmName)) {
            setFlashError(redirectAttributes, "Delete confirmation failed. Type the exact service name.");
            return editServiceRedirect(slug, serviceId);
        }

        try {
            adminCatalogService.deleteService(serviceId);
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
            return servicesRedirect(slug);
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Service deleted.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return servicesRedirect(slug);
    }

    @PostMapping("/{serviceId}/status")
    public String updateStatus(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @Valid @ModelAttribute(STATUS_FORM_ATTRIBUTE) ServiceStatusForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            setFlashError(redirectAttributes, "Please choose a valid service status.");
            return servicesRedirect(slug);
        }

        try {
            ServiceResponse service = adminCatalogService.getAdminService(serviceId);
            adminCatalogService.updateService(serviceId, new AdminUpsertServiceRequest(
                    service.name(),
                    service.description(),
                    service.price(),
                    service.unit(),
                    service.durationMinutes(),
                    service.sortOrder(),
                    form.getStatus()
            ));
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
            return servicesRedirect(slug);
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Service status updated.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return servicesRedirect(slug);
    }

    private void populateServicesModel(String slug, String query, int page, int size, Model model) {
        Page<ServiceResponse> servicePage = adminCatalogService.getAdminServicesPage(query, page, size);
        model.addAttribute("slug", slug);
        model.addAttribute("services", servicePage.getContent());
        model.addAttribute("serviceStatuses", SERVICE_STATUSES);
        model.addAttribute("servicePage", servicePage);
        model.addAttribute("query", query);
        model.addAttribute("size", servicePage.getSize());
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

    private String editServiceRedirect(String slug, Long serviceId) {
        return "redirect:" + servicesPath(slug) + "/" + serviceId + "/edit";
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private void setFlashError(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute("flashMessage", message != null ? message : "Unable to update service.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_ERROR);
    }
}
