package com.yoobu.api.security;

import com.yoobu.api.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class SuperAdminBasicAuthenticationFilter extends OncePerRequestFilter {

    private static final String REALM = "Yoobu Super Admin";

    private final SecurityProperties securityProperties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        BasicAuthParser.Credentials credentials = BasicAuthParser.parse(request, response, REALM);
        if (credentials == null) {
            return;
        }

        SecurityProperties.SuperAdmin superAdmin = securityProperties.getSuperadmin();
        if (!superAdmin.getUsername().equals(credentials.username())
                || !superAdmin.getPassword().equals(credentials.password())) {
            BasicAuthChallenge.send(response, REALM, "Invalid superadmin credentials");
            return;
        }

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

}
