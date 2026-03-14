package com.yoobu.api.admin;

import com.yoobu.api.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class SuperAdminAuthInterceptor implements HandlerInterceptor {

    private static final String BASIC_PREFIX = "Basic ";
    private static final String REALM = "Yoobu Super Admin";

    private final SecurityProperties securityProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Credentials credentials = extractCredentials(request, response);
        if (credentials == null) {
            return false;
        }
        SecurityProperties.SuperAdmin superAdmin = securityProperties.getSuperadmin();
        if (!superAdmin.getUsername().equals(credentials.username())
                || !superAdmin.getPassword().equals(credentials.password())) {
            return unauthorized(response, "Invalid superadmin credentials");
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

        return new Credentials(decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1));
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

    private record Credentials(String username, String password) {
    }
}
