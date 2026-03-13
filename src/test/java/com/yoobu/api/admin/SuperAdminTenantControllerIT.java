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
class SuperAdminTenantControllerIT extends IntegrationTestSupport {

    @Test
    void superAdminCanAuthenticateAgainstProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/superadmin/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void superAdminCannotAuthenticateWithInvalidCredentials() throws Exception {
        mockMvc.perform(get("/superadmin/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Invalid superadmin credentials"));
    }

    @Test
    void superAdminCannotAccessProtectedEndpointWithoutCredentials() throws Exception {
        mockMvc.perform(get("/superadmin/tenants"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Missing Basic authorization header"));
    }

    @Test
    void superAdminCanCreateTenantAndReadItBack() throws Exception {
        var request = foodOrderTenant("tenant-it", "Tenant Integration Test", "bot-token", "admin", "secret");

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

    @Test
    void superAdminCannotCreateTwoTenantsWithSameSlug() throws Exception {
        var request = foodOrderTenant("duplicate-tenant", "Duplicate Tenant", "bot-token", "admin", "secret");

        mockMvc.perform(post("/superadmin/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/superadmin/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(status().reason("Tenant slug already exists"));
    }
}
