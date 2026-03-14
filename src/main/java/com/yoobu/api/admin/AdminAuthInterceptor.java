package com.yoobu.api.admin;

import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantConfig;
import com.yoobu.api.tenant.TenantConfigRepository;
import com.yoobu.api.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String BASIC_PREFIX = "Basic ";
    private static final String REALM = "Yoobu Tenant Admin";

    private final TenantConfigRepository tenantConfigRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Tenant tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context is not available");
        }

        Credentials credentials = extractCredentials(request, response);
        if (credentials == null) {
            return false;
        }
        Map<String, String> config = loadTenantConfig(tenant.getId());

        String expectedUsername = config.get("admin_username");
        String expectedPasswordHash = config.get("admin_password");
        if (expectedUsername == null || expectedPasswordHash == null) {
            return unauthorized(response, "Admin credentials are not configured");
        }

        if (!expectedUsername.equals(credentials.username())
                || !passwordEncoder.matches(credentials.password(), expectedPasswordHash)) {
            return unauthorized(response, "Invalid admin credentials");
        }

        return true;
    }

    private Credentials extractCredentials(HttpServletRequest request, HttpServletResponse response) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BASIC_PREFIX)) {
            unauthorized(response, "Missing Basic authorization header");
            return null;
        }

        String decoded;
        try {
            decoded = new String(
                    Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length())),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            unauthorized(response, "Invalid Basic authorization header");
            return null;
        }

        int separatorIndex = decoded.indexOf(':');
        if (separatorIndex <= 0) {
            unauthorized(response, "Invalid Basic authorization header");
            return null;
        }

        return new Credentials(
                decoded.substring(0, separatorIndex),
                decoded.substring(separatorIndex + 1)
        );
    }

    private boolean unauthorized(HttpServletResponse response, String message) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + REALM + "\"");
        try {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), message);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write unauthorized response", ex);
        }
        return false;
    }

    private Map<String, String> loadTenantConfig(Long tenantId) {
        List<TenantConfig> configEntries = tenantConfigRepository.findByTenantId(tenantId);
        return configEntries.stream()
                .collect(Collectors.toMap(TenantConfig::getKey, TenantConfig::getValue, (left, right) -> right));
    }

    private record Credentials(String username, String password) {
    }
}
