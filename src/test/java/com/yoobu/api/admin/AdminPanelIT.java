package com.yoobu.api.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class AdminPanelIT extends IntegrationTestSupport {

    private static final String TENANT_SLUG = "food-tenant";
    private static final String ADMIN_USERNAME = "food-admin";
    private static final String ADMIN_PASSWORD = "food-secret";
    private static final String PANEL_ROOT = "/admin/" + TENANT_SLUG + "/panel";

    @Test
    void adminPanelPagesRenderAndFormsMutateState() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot-token", ADMIN_USERNAME, ADMIN_PASSWORD);

        JsonNode service = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50");
        long serviceId = service.get("id").asLong();
        JsonNode booking = createBooking(TENANT_SLUG, 777L, serviceId, 2);
        long bookingId = booking.get("id").asLong();

        getWithTenantAdminAuth(PANEL_ROOT, ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/bookings"));

        getWithTenantAdminAuth(PANEL_ROOT + "/bookings", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bookings for " + TENANT_SLUG)))
                .andExpect(content().string(containsString("Alice")))
                .andExpect(content().string(containsString("NEW")));

        getWithTenantAdminAuth(PANEL_ROOT + "/bookings/" + bookingId, ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Booking #" + bookingId)))
                .andExpect(content().string(containsString("Pizza")));

        mockMvc.perform(post(PANEL_ROOT + "/bookings/" + bookingId + "/status")
                        .header(AUTHORIZATION, basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("status", "CONFIRMED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/bookings/" + bookingId));

        getWithTenantAdminAuth(PANEL_ROOT + "/services", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Services for " + TENANT_SLUG)))
                .andExpect(content().string(containsString("Pizza")));

        mockMvc.perform(post(PANEL_ROOT + "/services")
                        .header(AUTHORIZATION, basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("name", "Pasta")
                        .param("description", "Fresh pasta")
                        .param("price", "14.50")
                        .param("unit", "bowl")
                        .param("durationMinutes", "25")
                        .param("sortOrder", "2")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/services"));

        mockMvc.perform(post(PANEL_ROOT + "/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("name", "Pizza Romana")
                        .param("description", "Thin crust")
                        .param("price", "13.50")
                        .param("unit", "pcs")
                        .param("durationMinutes", "20")
                        .param("sortOrder", "1")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/services"));

        getWithTenantAdminAuth("/admin/" + TENANT_SLUG + "/services", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pizza Romana")))
                .andExpect(content().string(containsString("Pasta")));

        getWithTenantAdminAuth("/admin/" + TENANT_SLUG + "/bookings/" + bookingId, ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CONFIRMED")));
    }

    @Test
    void superAdminCanOpenTenantAdminPanel() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot-token", ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(get(PANEL_ROOT)
                        .header(AUTHORIZATION, superAdminAuth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/bookings"));
    }

    @Test
    void editServiceFormPreservesInactiveState() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot-token", ADMIN_USERNAME, ADMIN_PASSWORD);

        JsonNode service = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50");
        long serviceId = service.get("id").asLong();

        mockMvc.perform(post(PANEL_ROOT + "/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("name", "Pizza")
                        .param("description", "Thin crust")
                        .param("price", "12.50")
                        .param("unit", "pcs")
                        .param("durationMinutes", "20")
                        .param("sortOrder", "1")
                        .param("status", "INACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/services"));

        getWithTenantAdminAuth(PANEL_ROOT + "/services/" + serviceId + "/edit", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"status\"")))
                .andExpect(content().string(containsString("value=\"INACTIVE\" selected=\"selected\"")));
    }
}
