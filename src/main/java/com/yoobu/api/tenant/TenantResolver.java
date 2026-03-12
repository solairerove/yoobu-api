package com.yoobu.api.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantResolver implements HandlerInterceptor {

    private final TenantRepository tenantRepository;

    public TenantResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String slug = extractSlug(request.getRequestURI());
        Tenant tenant = tenantRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        TenantContext.setCurrentTenant(tenant);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }

    private String extractSlug(String requestUri) {
        String[] segments = requestUri.split("/");
        if (segments.length < 3 || segments[2].isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant slug is missing");
        }
        return segments[2];
    }
}
