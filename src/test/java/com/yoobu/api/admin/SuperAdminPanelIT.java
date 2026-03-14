package com.yoobu.api.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        mockMvc.perform(get("/superadmin/panel/tenants")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Panel Tenant")))
                .andExpect(content().string(containsString("panel-tenant")))
                .andExpect(content().string(containsString("/admin/panel-tenant/panel")));
    }
}
