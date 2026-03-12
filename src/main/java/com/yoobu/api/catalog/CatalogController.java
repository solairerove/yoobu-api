package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.ServiceResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/t/{slug}/services")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogQueryService catalogQueryService;

    @GetMapping
    public List<ServiceResponse> getServices() {
        return catalogQueryService.getActiveServices();
    }
}
