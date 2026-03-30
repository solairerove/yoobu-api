package com.yoobu.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;

final class BasicAuthParser {

    private static final String BASIC_PREFIX = "Basic ";

    private BasicAuthParser() {
    }

    record Credentials(String username, String password) {
    }

    static Credentials parse(HttpServletRequest request, HttpServletResponse response, String realm)
            throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BASIC_PREFIX)) {
            BasicAuthChallenge.send(response, realm, "Missing Basic authorization header");
            return null;
        }

        String decoded;
        try {
            decoded = new String(
                    Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length())),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException ex) {
            BasicAuthChallenge.send(response, realm, "Invalid Basic authorization header");
            return null;
        }

        int separatorIndex = decoded.indexOf(':');
        if (separatorIndex <= 0) {
            BasicAuthChallenge.send(response, realm, "Invalid Basic authorization header");
            return null;
        }

        return new Credentials(decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1));
    }
}
