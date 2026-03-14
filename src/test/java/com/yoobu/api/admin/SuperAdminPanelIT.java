package com.yoobu.api.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class SuperAdminPanelIT extends IntegrationTestSupport {

    @Test
    void superAdminPanelListsAndCreatesTenants() throws Exception {
        mockMvc.perform(get("/superadmin/panel")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/superadmin/panel/tenants"));

        mockMvc.perform(post("/superadmin/panel/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .param("slug", "panel-tenant")
                        .param("name", "Panel Tenant")
                        .param("type", "FOOD_ORDER")
                        .param("botToken", "panel-bot")
                        .param("ownerTelegramId", "123456")
                        .param("timezone", "Asia/Ho_Chi_Minh")
                        .param("primaryColor", "#112233")
                        .param("logoUrl", "https://cdn.example.com/logo.png")
                        .param("welcomeMessage", "Hello from panel")
                        .param("adminUsername", "panel-admin")
                        .param("adminPassword", "panel-secret"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/superadmin/panel/tenants"));

        JsonNode tenants = readJson(mockMvc.perform(get("/superadmin/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn());
        long tenantId = tenants.get(0).get("id").asLong();

        mockMvc.perform(get("/superadmin/panel/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Panel Tenant")))
                .andExpect(content().string(containsString("panel-tenant")))
                .andExpect(content().string(containsString("/admin/panel-tenant/panel")));

        mockMvc.perform(get("/superadmin/panel/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Panel Tenant")))
                .andExpect(content().string(containsString("panel-admin")))
                .andExpect(content().string(containsString("Edit tenant")))
                .andExpect(content().string(containsString("/t/panel-tenant/services")));
    }

    @Test
    void superAdminPanelCanEditTenantAndRotateCredentials() throws Exception {
        long tenantId = createFoodOrderTenant("panel-edit", "Panel Before", "bot-before", "panel-admin", "panel-secret")
                .get("id").asLong();

        mockMvc.perform(get("/superadmin/panel/tenants/" + tenantId + "/edit")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit tenant")))
                .andExpect(content().string(containsString("panel-edit")));

        mockMvc.perform(post("/superadmin/panel/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .param("slug", "panel-edit")
                        .param("name", "Panel After")
                        .param("type", "FOOD_ORDER")
                        .param("botToken", "")
                        .param("ownerTelegramId", "")
                        .param("timezone", "Asia/Ho_Chi_Minh")
                        .param("primaryColor", "")
                        .param("logoUrl", "https://cdn.example.com/panel-updated.png")
                        .param("welcomeMessage", "Updated from panel")
                        .param("adminUsername", "panel-admin-2")
                        .param("adminPassword", "panel-secret-2")
                        .param("active", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/superadmin/panel/tenants/" + tenantId));

        mockMvc.perform(get("/superadmin/panel/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Panel After")))
                .andExpect(content().string(containsString("panel-admin-2")))
                .andExpect(content().string(containsString("Yes")))
                .andExpect(content().string(containsString("https://cdn.example.com/panel-updated.png")));

        mockMvc.perform(get("/admin/panel-edit/services")
                        .header(AUTHORIZATION, basicAuth("panel-admin", "panel-secret")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/panel-edit/services")
                        .header(AUTHORIZATION, basicAuth("panel-admin-2", "panel-secret-2")))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminPanelRejectsDuplicateSlugOnCreateForm() throws Exception {
        createFoodOrderTenant("panel-duplicate", "Existing Tenant", "bot-existing", "existing-admin", "existing-secret");

        mockMvc.perform(post("/superadmin/panel/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .param("slug", "panel-duplicate")
                        .param("name", "New Panel Tenant")
                        .param("type", "FOOD_ORDER")
                        .param("botToken", "panel-bot")
                        .param("ownerTelegramId", "123456")
                        .param("timezone", "Asia/Ho_Chi_Minh")
                        .param("primaryColor", "#112233")
                        .param("logoUrl", "https://cdn.example.com/logo.png")
                        .param("welcomeMessage", "Hello from panel")
                        .param("adminUsername", "panel-admin")
                        .param("adminPassword", "panel-secret"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tenant slug already exists")));
    }
}
