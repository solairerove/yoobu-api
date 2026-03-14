package com.yoobu.api.security;

import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantConfig;
import com.yoobu.api.tenant.TenantConfigRepository;
import com.yoobu.api.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class TenantBasicAuthenticationFilter extends OncePerRequestFilter {

    private static final String BASIC_PREFIX = "Basic ";
    private static final String REALM_PREFIX = "Yoobu Tenant Admin: ";

    private final TenantConfigRepository tenantConfigRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Tenant tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context is not available");
        }

        Credentials credentials = extractCredentials(request, response, tenant.getSlug());
        if (credentials == null) {
            return;
        }

        Map<String, String> config = loadTenantConfig(tenant.getId());
        String expectedUsername = config.get("admin_username");
        String expectedPasswordHash = config.get("admin_password");
        if (expectedUsername == null || expectedPasswordHash == null) {
            BasicAuthChallenge.send(response, realm(tenant.getSlug()), "Admin credentials are not configured");
            return;
        }

        if (!expectedUsername.equals(credentials.username())
                || !passwordEncoder.matches(credentials.password(), expectedPasswordHash)) {
            BasicAuthChallenge.send(response, realm(tenant.getSlug()), "Invalid admin credentials");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                credentials.username(),
                null,
                AuthorityUtils.createAuthorityList("ROLE_TENANT_ADMIN")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Credentials extractCredentials(
            HttpServletRequest request,
            HttpServletResponse response,
            String slug
    ) throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BASIC_PREFIX)) {
            BasicAuthChallenge.send(response, realm(slug), "Missing Basic authorization header");
            return null;
        }

        String decoded;
        try {
            decoded = new String(
                    Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length())),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException ex) {
            BasicAuthChallenge.send(response, realm(slug), "Invalid Basic authorization header");
            return null;
        }

        int separatorIndex = decoded.indexOf(':');
        if (separatorIndex <= 0) {
            BasicAuthChallenge.send(response, realm(slug), "Invalid Basic authorization header");
            return null;
        }

        return new Credentials(decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1));
    }

    private Map<String, String> loadTenantConfig(Long tenantId) {
        List<TenantConfig> configEntries = tenantConfigRepository.findByTenantId(tenantId);
        return configEntries.stream()
                .collect(Collectors.toMap(TenantConfig::getKey, TenantConfig::getValue, (left, right) -> right));
    }

    private String realm(String slug) {
        return REALM_PREFIX + slug;
    }

    private record Credentials(String username, String password) {
    }
}
