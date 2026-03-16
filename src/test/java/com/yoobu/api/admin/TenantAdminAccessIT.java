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

    private static final String TENANT_SLUG = "tenant-auth";
    private static final String TENANT_SERVICES_PATH = "/admin/" + TENANT_SLUG + "/services";

    @Test
    void createdTenantCanAuthenticateWithValidCredentials() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        getWithTenantAdminAuth(TENANT_SERVICES_PATH, "tenant-admin", "tenant-secret")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void superAdminCanAccessTenantAdminEndpoint() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get(TENANT_SERVICES_PATH)
                        .header(AUTHORIZATION, superAdminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void tenantCannotAuthenticateWithInvalidCredentials() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get(TENANT_SERVICES_PATH)
                        .header(AUTHORIZATION, basicAuth("tenant-admin", "wrong-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Tenant Admin: " + TENANT_SLUG + "\""))
                .andExpect(status().reason("Invalid admin credentials"));
    }

    @Test
    void tenantCannotAccessAdminEndpointWithoutCredentials() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Tenant Auth", "bot-auth", "tenant-admin", "tenant-secret");

        mockMvc.perform(get(TENANT_SERVICES_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Tenant Admin: " + TENANT_SLUG + "\""))
                .andExpect(status().reason("Missing Basic authorization header"));
    }
}
