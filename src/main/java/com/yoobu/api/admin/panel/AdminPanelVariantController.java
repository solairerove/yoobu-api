package com.yoobu.api.admin.panel;

import com.yoobu.api.catalog.AdminCatalogService;
import com.yoobu.api.catalog.dto.AdminUpsertVariantRequest;
import com.yoobu.api.catalog.dto.ProductVariantImageResponse;
import com.yoobu.api.catalog.dto.ProductVariantResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/{slug}/panel/services/{serviceId}/variants")
public class AdminPanelVariantController {

    private static final String VARIANT_FORM_VIEW = "admin/panel/variant-form";
    private static final String FLASH_TYPE_SUCCESS = "success";
    private static final String FLASH_TYPE_ERROR = "error";

    private final AdminCatalogService adminCatalogService;

    @GetMapping("/new")
    public String newVariant(@PathVariable String slug, @PathVariable Long serviceId, Model model) {
        return variantFormView(slug, serviceId, newVariantForm(), model, "New variant", "Add variant", null);
    }

    @PostMapping
    public String createVariant(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @Valid @ModelAttribute("variantForm") VariantForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return variantFormView(slug, serviceId, form, model, "New variant", "Add variant", null);
        }

        try {
            adminCatalogService.createVariant(serviceId, toRequest(form));
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
            return editServiceRedirect(slug, serviceId);
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Variant added.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return editServiceRedirect(slug, serviceId);
    }

    @GetMapping("/{variantId}/edit")
    public String editVariant(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @PathVariable Long variantId,
            Model model
    ) {
        ProductVariantResponse variant = adminCatalogService.getVariants(serviceId).stream()
                .filter(v -> v.id().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Variant not found"));

        List<ProductVariantImageResponse> variantImages = adminCatalogService.getVariantImages(serviceId, variantId);
        model.addAttribute("variantImages", variantImages);
        return variantFormView(slug, serviceId, fromResponse(variant), model, "Edit variant", "Save changes", variantId);
    }

    @PostMapping("/{variantId}")
    public String updateVariant(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @PathVariable Long variantId,
            @Valid @ModelAttribute("variantForm") VariantForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            List<ProductVariantImageResponse> variantImages = adminCatalogService.getVariantImages(serviceId, variantId);
            model.addAttribute("variantImages", variantImages);
            return variantFormView(slug, serviceId, form, model, "Edit variant", "Save changes", variantId);
        }

        try {
            adminCatalogService.updateVariant(serviceId, variantId, toRequest(form));
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
            return editServiceRedirect(slug, serviceId);
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Variant updated.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return editServiceRedirect(slug, serviceId);
    }

    @PostMapping("/{variantId}/images")
    public String addVariantImage(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @PathVariable Long variantId,
            @RequestParam("imageFile") MultipartFile imageFile,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminCatalogService.addVariantImage(serviceId, variantId, imageFile);
            redirectAttributes.addFlashAttribute("flashMessage", "Image added.");
            redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
        }
        return variantEditRedirect(slug, serviceId, variantId);
    }

    @PostMapping("/{variantId}/images/{imageId}/delete")
    public String deleteVariantImage(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @PathVariable Long variantId,
            @PathVariable Long imageId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminCatalogService.deleteVariantImage(serviceId, variantId, imageId);
            redirectAttributes.addFlashAttribute("flashMessage", "Image removed.");
            redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
        }
        return variantEditRedirect(slug, serviceId, variantId);
    }

    @PostMapping("/{variantId}/delete")
    public String deleteVariant(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @PathVariable Long variantId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminCatalogService.deleteVariant(serviceId, variantId);
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
            return editServiceRedirect(slug, serviceId);
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Variant deleted.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return editServiceRedirect(slug, serviceId);
    }

    @PostMapping("/{variantId}/stock")
    public String adjustStock(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @PathVariable Long variantId,
            @RequestParam int delta,
            RedirectAttributes redirectAttributes
    ) {
        try {
            adminCatalogService.adjustStock(serviceId, variantId, delta);
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
            return editServiceRedirect(slug, serviceId);
        }
        redirectAttributes.addFlashAttribute("flashMessage", "Stock updated.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return editServiceRedirect(slug, serviceId);
    }

    private String variantFormView(
            String slug,
            Long serviceId,
            VariantForm form,
            Model model,
            String formTitle,
            String submitLabel,
            Long variantId
    ) {
        model.addAttribute("slug", slug);
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("variantForm", form);
        model.addAttribute("formTitle", formTitle);
        model.addAttribute("submitLabel", submitLabel);
        model.addAttribute("editVariantId", variantId);
        model.addAttribute("formAction", variantFormAction(slug, serviceId, variantId));
        return VARIANT_FORM_VIEW;
    }

    private VariantForm newVariantForm() {
        VariantForm form = new VariantForm();
        form.setSortOrder(0);
        form.setStock(0);
        return form;
    }

    private VariantForm fromResponse(ProductVariantResponse variant) {
        VariantForm form = new VariantForm();
        form.setSize(variant.size());
        form.setColor(variant.color());
        form.setPrice(variant.price());
        form.setStock(variant.stock());
        form.setSortOrder(variant.sortOrder());
        return form;
    }

    private AdminUpsertVariantRequest toRequest(VariantForm form) {
        return new AdminUpsertVariantRequest(
                form.getSize(),
                form.getColor(),
                form.getPrice(),
                form.getStock(),
                form.getSortOrder()
        );
    }

    private String variantFormAction(String slug, Long serviceId, Long variantId) {
        String base = "/admin/" + slug + "/panel/services/" + serviceId + "/variants";
        return variantId == null ? base : base + "/" + variantId;
    }

    private String editServiceRedirect(String slug, Long serviceId) {
        return "redirect:/admin/" + slug + "/panel/services/" + serviceId + "/edit";
    }

    private String variantEditRedirect(String slug, Long serviceId, Long variantId) {
        return "redirect:/admin/" + slug + "/panel/services/" + serviceId + "/variants/" + variantId + "/edit";
    }

    private void setFlashError(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute("flashMessage", message != null ? message : "Unable to update variant.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_ERROR);
    }
}
