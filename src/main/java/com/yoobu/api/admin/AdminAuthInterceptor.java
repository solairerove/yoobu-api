package com.yoobu.api.admin;

import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantConfig;
import com.yoobu.api.tenant.TenantConfigRepository;
import com.yoobu.api.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String BASIC_PREFIX = "Basic ";

    private final TenantConfigRepository tenantConfigRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Tenant tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context is not available");
        }

        Credentials credentials = extractCredentials(request);
        Map<String, String> config = loadTenantConfig(tenant.getId());

        String expectedUsername = config.get("admin_username");
        String expectedPasswordHash = config.get("admin_password");
        if (expectedUsername == null || expectedPasswordHash == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin credentials are not configured");
        }

        if (!expectedUsername.equals(credentials.username())
                || !passwordEncoder.matches(credentials.password(), expectedPasswordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials");
        }

        return true;
    }

    private Credentials extractCredentials(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BASIC_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Basic authorization header");
        }

        String decoded;
        try {
            decoded = new String(
                    Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length())),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Basic authorization header");
        }

        int separatorIndex = decoded.indexOf(':');
        if (separatorIndex <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Basic authorization header");
        }

        return new Credentials(
                decoded.substring(0, separatorIndex),
                decoded.substring(separatorIndex + 1)
        );
    }

    private Map<String, String> loadTenantConfig(Long tenantId) {
        List<TenantConfig> configEntries = tenantConfigRepository.findByTenantId(tenantId);
        return configEntries.stream()
                .collect(Collectors.toMap(TenantConfig::getKey, TenantConfig::getValue, (left, right) -> right));
    }

    private record Credentials(String username, String password) {
    }
}
