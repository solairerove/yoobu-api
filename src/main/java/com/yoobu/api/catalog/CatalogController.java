package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.ServiceResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/t/{slug}/services")
public class CatalogController {

    private final CatalogQueryService catalogQueryService;

    public CatalogController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping
    public List<ServiceResponse> getServices() {
        return catalogQueryService.getActiveServices();
    }
}
