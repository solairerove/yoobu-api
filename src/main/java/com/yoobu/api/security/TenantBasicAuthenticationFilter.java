package com.yoobu.api.security;

import com.yoobu.api.config.SecurityProperties;
import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantContext;
import com.yoobu.api.tenant.TenantSettings;
import com.yoobu.api.tenant.TenantSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Slf4j
public class TenantBasicAuthenticationFilter extends OncePerRequestFilter {

    private static final String BASIC_PREFIX = "Basic ";
    private static final String REALM_PREFIX = "Yoobu Tenant Admin: ";

    private final TenantSettingsService tenantSettingsService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Tenant tenant = TenantContext.requireCurrentTenant();

        Credentials credentials = extractCredentials(request, response, tenant.getSlug());
        if (credentials == null) {
            return;
        }

        TenantSettings.AdminSettings admin = tenantSettingsService.getSettings(tenant.getId()).admin();
        String expectedUsername = admin.username();
        String expectedPasswordHash = admin.passwordHash();
        if (expectedUsername == null || expectedPasswordHash == null) {
            log.warn("Tenant admin credentials are not configured for slug={} path={}", tenant.getSlug(), request.getRequestURI());
            BasicAuthChallenge.send(response, realm(tenant.getSlug()), "Admin credentials are not configured");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = authenticate(credentials, expectedUsername, expectedPasswordHash);
        if (authentication == null) {
            log.warn("Tenant admin auth failed for slug={} path={} username={}",
                    tenant.getSlug(), request.getRequestURI(), credentials.username());
            BasicAuthChallenge.send(response, realm(tenant.getSlug()), "Invalid admin credentials");
            return;
        }

        log.info("Tenant admin auth succeeded for slug={} path={} username={}",
                tenant.getSlug(), request.getRequestURI(), credentials.username());

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
            log.warn("Tenant admin auth missing/invalid Authorization header for slug={} path={}", slug, request.getRequestURI());
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
            log.warn("Tenant admin auth received undecodable Basic header for slug={} path={}", slug, request.getRequestURI());
            BasicAuthChallenge.send(response, realm(slug), "Invalid Basic authorization header");
            return null;
        }

        int separatorIndex = decoded.indexOf(':');
        if (separatorIndex <= 0) {
            log.warn("Tenant admin auth received malformed Basic payload for slug={} path={}", slug, request.getRequestURI());
            BasicAuthChallenge.send(response, realm(slug), "Invalid Basic authorization header");
            return null;
        }

        return new Credentials(decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1));
    }

    private UsernamePasswordAuthenticationToken authenticate(
            Credentials credentials,
            String expectedUsername,
            String expectedPasswordHash
    ) {
        if (expectedUsername.equals(credentials.username())
                && passwordEncoder.matches(credentials.password(), expectedPasswordHash)) {
            return new UsernamePasswordAuthenticationToken(
                    credentials.username(),
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_TENANT_ADMIN")
            );
        }

        SecurityProperties.SuperAdmin superAdmin = securityProperties.getSuperadmin();
        if (superAdmin.getUsername().equals(credentials.username())
                && superAdmin.getPassword().equals(credentials.password())) {
            return new UsernamePasswordAuthenticationToken(
                    credentials.username(),
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_SUPERADMIN")
            );
        }

        return null;
    }

    private String realm(String slug) {
        return REALM_PREFIX + slug;
    }

    private record Credentials(String username, String password) {
    }
}
