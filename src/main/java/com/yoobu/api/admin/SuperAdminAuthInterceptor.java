package com.yoobu.api.admin;

import com.yoobu.api.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class SuperAdminAuthInterceptor implements HandlerInterceptor {

    private static final String BASIC_PREFIX = "Basic ";

    private final SecurityProperties securityProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Credentials credentials = extractCredentials(request);
        SecurityProperties.SuperAdmin superAdmin = securityProperties.getSuperadmin();
        if (!superAdmin.getUsername().equals(credentials.username())
                || !superAdmin.getPassword().equals(credentials.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid superadmin credentials");
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

        return new Credentials(decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1));
    }

    private record Credentials(String username, String password) {
    }
}
