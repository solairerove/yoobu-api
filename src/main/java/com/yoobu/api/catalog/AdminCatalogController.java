package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.AdminUpsertServiceRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(@PathVariable Long serviceId) {
        adminCatalogService.deleteService(serviceId);
    }
}
