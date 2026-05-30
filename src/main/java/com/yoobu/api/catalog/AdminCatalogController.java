package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.AdminUpsertServiceRequest;
import com.yoobu.api.catalog.dto.AdminUpsertVariantRequest;
import com.yoobu.api.catalog.dto.AdjustStockRequest;
import com.yoobu.api.catalog.dto.ProductVariantResponse;
import com.yoobu.api.catalog.dto.ServiceResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/{slug}/services")
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;

    @GetMapping
    public List<ServiceResponse> getServices() {
        return adminCatalogService.getAdminServices();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse createService(@Valid @RequestBody AdminUpsertServiceRequest request) {
        return adminCatalogService.createService(request);
    }

    @PutMapping("/{serviceId}")
    public ServiceResponse updateService(
            @PathVariable Long serviceId,
            @Valid @RequestBody AdminUpsertServiceRequest request
    ) {
        return adminCatalogService.updateService(serviceId, request);
    }

    @PostMapping("/{serviceId}/image")
    public ServiceResponse uploadServiceImage(
            @PathVariable Long serviceId,
            @RequestParam("file") MultipartFile file
    ) {
        return adminCatalogService.uploadServiceImage(serviceId, file);
    }

    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(@PathVariable Long serviceId) {
        adminCatalogService.deleteService(serviceId);
    }

    // --- Variant endpoints ---

    @GetMapping("/{serviceId}/variants")
    public List<ProductVariantResponse> getVariants(@PathVariable Long serviceId) {
        return adminCatalogService.getVariants(serviceId);
    }

    @PostMapping("/{serviceId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductVariantResponse createVariant(
            @PathVariable Long serviceId,
            @Valid @RequestBody AdminUpsertVariantRequest request
    ) {
        return adminCatalogService.createVariant(serviceId, request);
    }

    @PutMapping("/{serviceId}/variants/{variantId}")
    public ProductVariantResponse updateVariant(
            @PathVariable Long serviceId,
            @PathVariable Long variantId,
            @Valid @RequestBody AdminUpsertVariantRequest request
    ) {
        return adminCatalogService.updateVariant(serviceId, variantId, request);
    }

    @DeleteMapping("/{serviceId}/variants/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVariant(
            @PathVariable Long serviceId,
            @PathVariable Long variantId
    ) {
        adminCatalogService.deleteVariant(serviceId, variantId);
    }

    @PostMapping("/{serviceId}/variants/{variantId}/images")
    public ProductVariantResponse addVariantImage(
            @PathVariable Long serviceId,
            @PathVariable Long variantId,
            @RequestParam("file") MultipartFile file
    ) {
        return adminCatalogService.addVariantImage(serviceId, variantId, file);
    }

    @PutMapping("/{serviceId}/variants/{variantId}/stock")
    public ProductVariantResponse adjustStock(
            @PathVariable Long serviceId,
            @PathVariable Long variantId,
            @Valid @RequestBody AdjustStockRequest request
    ) {
        return adminCatalogService.adjustStock(serviceId, variantId, request.delta());
    }
}
