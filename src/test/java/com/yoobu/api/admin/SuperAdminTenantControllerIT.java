package com.yoobu.api.admin;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class SuperAdminTenantControllerIT extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void superAdminCanCreateTenantAndReadItBack() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest(
                "tenant-it",
                "Tenant Integration Test",
                TenantType.FOOD_ORDER,
                "bot-token",
                123456789L,
                "Europe/Warsaw",
                "#112233",
                "https://cdn.example.com/logo.png",
                "Hello from test",
                "admin",
                "secret");

        mockMvc.perform(post("/superadmin/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.slug").value("tenant-it"))
                .andExpect(jsonPath("$.name").value("Tenant Integration Test"))
                .andExpect(jsonPath("$.type").value("FOOD_ORDER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$.createdAt").isString());

        mockMvc.perform(get("/superadmin/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("tenant-it"))
                .andExpect(jsonPath("$[0].name").value("Tenant Integration Test"))
                .andExpect(jsonPath("$[0].type").value("FOOD_ORDER"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$[0].createdAt").isString());
    }

    private String basicAuth(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
