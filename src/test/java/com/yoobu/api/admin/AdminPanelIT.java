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

    @Test
    void adminPanelPagesRenderAndFormsMutateState() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot-token", "food-admin", "food-secret");

        JsonNode service = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50");
        long serviceId = service.get("id").asLong();
        JsonNode booking = createBooking("food-tenant", 777L, serviceId, 2);
        long bookingId = booking.get("id").asLong();

        mockMvc.perform(get("/admin/food-tenant/panel")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/food-tenant/panel/bookings"));

        mockMvc.perform(get("/admin/food-tenant/panel/bookings")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bookings for food-tenant")))
                .andExpect(content().string(containsString("Alice")))
                .andExpect(content().string(containsString("NEW")));

        mockMvc.perform(get("/admin/food-tenant/panel/bookings/" + bookingId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Booking #" + bookingId)))
                .andExpect(content().string(containsString("Pizza")));

        mockMvc.perform(post("/admin/food-tenant/panel/bookings/" + bookingId + "/status")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret"))
                        .param("status", "CONFIRMED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/food-tenant/panel/bookings/" + bookingId));

        mockMvc.perform(get("/admin/food-tenant/panel/services")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Services for food-tenant")))
                .andExpect(content().string(containsString("Pizza")));

        mockMvc.perform(post("/admin/food-tenant/panel/services")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret"))
                        .param("name", "Pasta")
                        .param("description", "Fresh pasta")
                        .param("price", "14.50")
                        .param("unit", "bowl")
                        .param("durationMinutes", "25")
                        .param("sortOrder", "2")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/food-tenant/panel/services"));

        mockMvc.perform(post("/admin/food-tenant/panel/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret"))
                        .param("name", "Pizza Romana")
                        .param("description", "Thin crust")
                        .param("price", "13.50")
                        .param("unit", "pcs")
                        .param("durationMinutes", "20")
                        .param("sortOrder", "1")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/food-tenant/panel/services"));

        mockMvc.perform(get("/admin/food-tenant/services")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pizza Romana")))
                .andExpect(content().string(containsString("Pasta")));

        mockMvc.perform(get("/admin/food-tenant/bookings/" + bookingId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CONFIRMED")));
    }

    @Test
    void superAdminCanOpenTenantAdminPanel() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot-token", "food-admin", "food-secret");

        mockMvc.perform(get("/admin/food-tenant/panel")
                        .header(AUTHORIZATION, basicAuth("test-superadmin", "test-password")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/food-tenant/panel/bookings"));
    }

    @Test
    void editServiceFormPreservesInactiveState() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot-token", "food-admin", "food-secret");

        JsonNode service = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50");
        long serviceId = service.get("id").asLong();

        mockMvc.perform(post("/admin/food-tenant/panel/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret"))
                        .param("name", "Pizza")
                        .param("description", "Thin crust")
                        .param("price", "12.50")
                        .param("unit", "pcs")
                        .param("durationMinutes", "20")
                        .param("sortOrder", "1")
                        .param("status", "INACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin/food-tenant/panel/services"));

        mockMvc.perform(get("/admin/food-tenant/panel/services/" + serviceId + "/edit")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"status\"")))
                .andExpect(content().string(containsString("value=\"INACTIVE\" selected=\"selected\"")));
    }
}
