package com.yoobu.api.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

final class BasicAuthChallenge {

    private BasicAuthChallenge() {
    }

    static void send(HttpServletResponse response, String realm, String message) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + realm + "\"");
        response.sendError(HttpStatus.UNAUTHORIZED.value(), message);
    }
}
