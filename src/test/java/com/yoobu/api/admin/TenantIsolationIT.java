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
class TenantIsolationIT extends IntegrationTestSupport {

    @Test
    void tenantsDoNotIntercrossServicesOrAdminAccess() throws Exception {
        createTenant("tenant-one", "Tenant One", "bot-one", "admin-one", "secret-one");
        createTenant("tenant-two", "Tenant Two", "bot-two", "admin-two", "secret-two");

        mockMvc.perform(post("/admin/tenant-one/services")
                        .header(AUTHORIZATION, basicAuth("admin-one", "secret-one"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceRequest("Burger", "9.99"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Burger"));

        mockMvc.perform(get("/admin/tenant-two/services")
                        .header(AUTHORIZATION, basicAuth("admin-one", "secret-one")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Tenant Admin: tenant-two\""))
                .andExpect(status().reason("Invalid admin credentials"));

        mockMvc.perform(post("/admin/tenant-two/services")
                        .header(AUTHORIZATION, basicAuth("admin-one", "secret-one"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceRequest("Sneaky", "1.00"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Tenant Admin: tenant-two\""))
                .andExpect(status().reason("Invalid admin credentials"));

        mockMvc.perform(get("/t/tenant-one/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Burger"));

        mockMvc.perform(get("/t/tenant-two/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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
