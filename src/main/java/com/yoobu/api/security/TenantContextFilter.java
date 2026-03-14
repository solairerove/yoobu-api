package com.yoobu.api.security;

import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantContext;
import com.yoobu.api.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String slug = extractSlug(request.getRequestURI());
            Tenant tenant = tenantRepository.findBySlugAndActiveTrue(slug)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
            TenantContext.setCurrentTenant(tenant);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractSlug(String requestUri) {
        String[] segments = requestUri.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if (("t".equals(segments[i]) || "admin".equals(segments[i])) && !segments[i + 1].isBlank()) {
                return segments[i + 1];
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant slug is missing");
    }
}
