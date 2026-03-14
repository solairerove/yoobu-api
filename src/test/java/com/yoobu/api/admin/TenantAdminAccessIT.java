package com.yoobu.api.admin;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        createFoodOrderTenant("tenant-auth", "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get("/admin/tenant-auth/services")
                        .header(AUTHORIZATION, basicAuth("tenant-admin", "tenant-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void superAdminCanAccessTenantAdminEndpoint() throws Exception {
        createFoodOrderTenant("tenant-auth", "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get("/admin/tenant-auth/services")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void tenantCannotAuthenticateWithInvalidCredentials() throws Exception {
        createFoodOrderTenant("tenant-auth", "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get("/admin/tenant-auth/services")
                        .header(AUTHORIZATION, basicAuth("tenant-admin", "wrong-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Tenant Admin: tenant-auth\""))
                .andExpect(status().reason("Invalid admin credentials"));
    }

    @Test
    void tenantCannotAccessAdminEndpointWithoutCredentials() throws Exception {
        createFoodOrderTenant("tenant-auth", "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get("/admin/tenant-auth/services"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Tenant Admin: tenant-auth\""))
                .andExpect(status().reason("Missing Basic authorization header"));
    }
}
