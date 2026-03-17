package com.yoobu.api.security;

import com.yoobu.api.config.SecurityProperties;
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
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Slf4j
public class SuperAdminBasicAuthenticationFilter extends OncePerRequestFilter {

    private static final String BASIC_PREFIX = "Basic ";
    private static final String REALM = "Yoobu Super Admin";

    private final SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Credentials credentials = extractCredentials(request, response);
        if (credentials == null) {
            return;
        }

        SecurityProperties.SuperAdmin superAdmin = securityProperties.getSuperadmin();
        if (!superAdmin.getUsername().equals(credentials.username())
                || !superAdmin.getPassword().equals(credentials.password())) {
            log.warn("Superadmin auth failed for path={} username={}", request.getRequestURI(), credentials.username());
            BasicAuthChallenge.send(response, REALM, "Invalid superadmin credentials");
            return;
        }

        log.info("Superadmin auth succeeded for path={} username={}", request.getRequestURI(), credentials.username());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                credentials.username(),
                null,
                AuthorityUtils.createAuthorityList("ROLE_SUPERADMIN")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Credentials extractCredentials(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BASIC_PREFIX)) {
            log.warn("Superadmin auth missing/invalid Authorization header for path={}", request.getRequestURI());
            BasicAuthChallenge.send(response, REALM, "Missing Basic authorization header");
            return null;
        }

        String decoded;
        try {
            decoded = new String(
                    Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length())),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException ex) {
            log.warn("Superadmin auth received undecodable Basic header for path={}", request.getRequestURI());
            BasicAuthChallenge.send(response, REALM, "Invalid Basic authorization header");
            return null;
        }

        int separatorIndex = decoded.indexOf(':');
        if (separatorIndex <= 0) {
            log.warn("Superadmin auth received malformed Basic payload for path={}", request.getRequestURI());
            BasicAuthChallenge.send(response, REALM, "Invalid Basic authorization header");
            return null;
        }

        return new Credentials(decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1));
    }

    private record Credentials(String username, String password) {
    }
}
