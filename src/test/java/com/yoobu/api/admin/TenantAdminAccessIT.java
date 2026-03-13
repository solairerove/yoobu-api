package com.yoobu.api.admin;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yoobu.api.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class TenantAdminAccessIT extends IntegrationTestSupport {

    @Test
    void createdTenantCanAuthenticateWithValidCredentials() throws Exception {
        createTenant("tenant-auth", "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get("/admin/tenant-auth/services")
                        .header(AUTHORIZATION, basicAuth("tenant-admin", "tenant-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void tenantCannotAuthenticateWithInvalidCredentials() throws Exception {
        createTenant("tenant-auth", "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get("/admin/tenant-auth/services")
                        .header(AUTHORIZATION, basicAuth("tenant-admin", "wrong-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Invalid admin credentials"));
    }

    @Test
    void tenantCannotAccessAdminEndpointWithoutCredentials() throws Exception {
        createTenant("tenant-auth", "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get("/admin/tenant-auth/services"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Missing Basic authorization header"));
    }

    private void createTenant(
            String slug,
            String name,
            String botToken,
            String adminUsername,
            String adminPassword
    ) throws Exception {
        mockMvc.perform(post("/superadmin/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                foodOrderTenant(slug, name, botToken, adminUsername, adminPassword))))
                .andExpect(status().isOk());
    }
}
