package com.yoobu.api;

import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class CorsConfigurationIT extends IntegrationTestSupport {

    private static final String ORIGIN_VALUE = "https://yoobu-web-production.up.railway.app";

    @Test
    void publicEndpointsReturnCorsHeadersForAllowedOrigins() throws Exception {
        createFoodOrderTenant("pizza", "Pizza", "bot-token", "admin", "password");

        mockMvc.perform(get("/t/pizza/config")
                        .header(ORIGIN, ORIGIN_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN_VALUE))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void adminPreflightRequestsDoNotRequireAuthentication() throws Exception {
        createFoodOrderTenant("sushi", "Sushi", "bot-token", "admin", "password");

        mockMvc.perform(options("/admin/sushi/services")
                        .header(ORIGIN, ORIGIN_VALUE)
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN_VALUE))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_METHODS, org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_HEADERS, org.hamcrest.Matchers.containsString("Authorization")));
    }
}
