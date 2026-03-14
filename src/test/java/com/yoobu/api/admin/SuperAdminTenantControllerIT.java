package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.UpdateTenantRequest;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
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
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Super Admin\""))
                .andExpect(status().reason("Invalid superadmin credentials"));
    }

    @Test
    void superAdminCannotAccessProtectedEndpointWithoutCredentials() throws Exception {
        mockMvc.perform(get("/superadmin/tenants"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Super Admin\""))
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

    @Test
    void superAdminCanGetAndUpdateTenantWithoutChangingSlug() throws Exception {
        long tenantId = createFoodOrderTenant("tenant-edit", "Tenant Before", "bot-before", "admin-before", "secret-before")
                .get("id").asLong();

        mockMvc.perform(get("/superadmin/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-edit"))
                .andExpect(jsonPath("$.name").value("Tenant Before"))
                .andExpect(jsonPath("$.config.admin_username").value("admin-before"));

        UpdateTenantRequest request = new UpdateTenantRequest(
                "Tenant After",
                TenantType.APPOINTMENT,
                "bot-after",
                999999L,
                "Asia/Ho_Chi_Minh",
                "#445566",
                "https://cdn.example.com/updated-logo.png",
                "Updated welcome",
                "admin-after",
                "secret-after",
                true
        );

        mockMvc.perform(put("/superadmin/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantId))
                .andExpect(jsonPath("$.slug").value("tenant-edit"))
                .andExpect(jsonPath("$.name").value("Tenant After"))
                .andExpect(jsonPath("$.type").value("APPOINTMENT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));

        mockMvc.perform(get("/superadmin/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-edit"))
                .andExpect(jsonPath("$.name").value("Tenant After"))
                .andExpect(jsonPath("$.type").value("APPOINTMENT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.botToken").value("bot-after"))
                .andExpect(jsonPath("$.ownerTelegramId").value(999999L))
                .andExpect(jsonPath("$.config.admin_username").value("admin-after"))
                .andExpect(jsonPath("$.config.primary_color").value("#445566"))
                .andExpect(jsonPath("$.config.logo_url").value("https://cdn.example.com/updated-logo.png"))
                .andExpect(jsonPath("$.config.welcome_message").value("Updated welcome"));

        mockMvc.perform(get("/admin/tenant-edit/services")
                        .header(AUTHORIZATION, basicAuth("admin-before", "secret-before")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/tenant-edit/services")
                        .header(AUTHORIZATION, basicAuth("admin-after", "secret-after")))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Tenant does not support food ordering"));
    }

    @Test
    void superAdminCanKeepExistingPasswordWhileUpdatingOtherTenantFields() throws Exception {
        long tenantId = createFoodOrderTenant("tenant-keep-pass", "Keep Password", "bot-token", "admin-before", "secret-before")
                .get("id").asLong();

        UpdateTenantRequest request = new UpdateTenantRequest(
                "Keep Password Updated",
                TenantType.FOOD_ORDER,
                "bot-token-updated",
                444444L,
                "Asia/Ho_Chi_Minh",
                "#778899",
                "https://cdn.example.com/keep-pass.png",
                "Password unchanged",
                "admin-after",
                "",
                true
        );

        mockMvc.perform(put("/superadmin/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-keep-pass"))
                .andExpect(jsonPath("$.name").value("Keep Password Updated"));

        mockMvc.perform(get("/superadmin/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-keep-pass"))
                .andExpect(jsonPath("$.config.admin_username").value("admin-after"))
                .andExpect(jsonPath("$.botToken").value("bot-token-updated"))
                .andExpect(jsonPath("$.config.primary_color").value("#778899"));

        mockMvc.perform(get("/admin/tenant-keep-pass/services")
                        .header(AUTHORIZATION, basicAuth("admin-before", "secret-before")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/tenant-keep-pass/services")
                        .header(AUTHORIZATION, basicAuth("admin-after", "secret-before")))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCanClearOptionalTenantFieldsAndDeactivateTenant() throws Exception {
        long tenantId = createFoodOrderTenant("tenant-clear", "Tenant Clear", "bot-clear", "admin-clear", "secret-clear")
                .get("id").asLong();

        UpdateTenantRequest request = new UpdateTenantRequest(
                "Tenant Cleared",
                TenantType.FOOD_ORDER,
                "",
                null,
                "",
                "",
                "",
                "",
                "admin-clear",
                "",
                false
        );

        mockMvc.perform(put("/superadmin/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-clear"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));

        mockMvc.perform(get("/superadmin/tenants/" + tenantId)
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-clear"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.botToken").doesNotExist())
                .andExpect(jsonPath("$.ownerTelegramId").doesNotExist())
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.config.admin_username").value("admin-clear"))
                .andExpect(jsonPath("$.config.primary_color").doesNotExist())
                .andExpect(jsonPath("$.config.logo_url").doesNotExist())
                .andExpect(jsonPath("$.config.welcome_message").doesNotExist());

        ResponseStatusException publicAccessFailure = assertTenantNotFound(() ->
                mockMvc.perform(get("/t/tenant-clear/services")));
        assertEquals("Tenant not found", publicAccessFailure.getReason());

        ResponseStatusException adminAccessFailure = assertTenantNotFound(() ->
                mockMvc.perform(get("/admin/tenant-clear/services")
                        .header(AUTHORIZATION, basicAuth("admin-clear", "secret-clear"))));
        assertEquals("Tenant not found", adminAccessFailure.getReason());
    }

    private ResponseStatusException assertTenantNotFound(Executable executable) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, executable);
        assertEquals(404, exception.getStatusCode().value());
        return exception;
    }
}
