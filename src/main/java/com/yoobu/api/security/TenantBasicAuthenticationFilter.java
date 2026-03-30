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
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class TenantBasicAuthenticationFilter extends OncePerRequestFilter {

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
        String realm = realm(tenant.getSlug());

        BasicAuthParser.Credentials credentials = BasicAuthParser.parse(request, response, realm);
        if (credentials == null) {
            return;
        }

        TenantSettings.AdminSettings admin = tenantSettingsService.getSettings(tenant.getId()).admin();
        String expectedUsername = admin.username();
        String expectedPasswordHash = admin.passwordHash();
        if (expectedUsername == null || expectedPasswordHash == null) {
            BasicAuthChallenge.send(response, realm, "Admin credentials are not configured");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = authenticate(credentials, expectedUsername, expectedPasswordHash);
        if (authentication == null) {
            BasicAuthChallenge.send(response, realm, "Invalid admin credentials");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UsernamePasswordAuthenticationToken authenticate(
            BasicAuthParser.Credentials credentials,
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
}
