package com.yoobu.api.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import java.util.Map;
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

        tenantAdminGet(TENANT_SLUG, "/panel", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/bookings"));

        tenantAdminGet(TENANT_SLUG, "/panel/bookings", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bookings for " + TENANT_SLUG)))
                .andExpect(content().string(containsString("Alice")))
                .andExpect(content().string(containsString("NEW")));

        tenantAdminGet(TENANT_SLUG, "/panel/bookings/" + bookingId, ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Booking #" + bookingId)))
                .andExpect(content().string(containsString("Pizza")));

        tenantAdminPostForm(
                TENANT_SLUG,
                "/panel/bookings/" + bookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                Map.of("status", "CONFIRMED")
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/bookings"));

        tenantAdminGet(TENANT_SLUG, "/panel/services", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Services for " + TENANT_SLUG)))
                .andExpect(content().string(containsString("Pizza")));

        tenantAdminPostForm(
                TENANT_SLUG,
                "/panel/services",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                Map.of(
                        "name", "Pasta",
                        "description", "Fresh pasta",
                        "price", "14.50",
                        "unit", "bowl",
                        "durationMinutes", "25",
                        "sortOrder", "2",
                        "status", "ACTIVE"
                )
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/services"));

        tenantAdminPostForm(
                TENANT_SLUG,
                "/panel/services/" + serviceId,
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                Map.of(
                        "name", "Pizza Romana",
                        "description", "Thin crust",
                        "price", "13.50",
                        "unit", "pcs",
                        "durationMinutes", "20",
                        "sortOrder", "1",
                        "status", "ACTIVE"
                )
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/services"));

        tenantAdminGet(TENANT_SLUG, "/services", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pizza Romana")))
                .andExpect(content().string(containsString("Pasta")));

        tenantAdminGet(TENANT_SLUG, "/bookings/" + bookingId, ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CONFIRMED")));
    }

    @Test
    void superAdminCanOpenTenantAdminPanel() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot-token", ADMIN_USERNAME, ADMIN_PASSWORD);

        tenantAdminGet(TENANT_SLUG, "/panel", SUPERADMIN_USERNAME, SUPERADMIN_PASSWORD)
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/bookings"));
    }

    @Test
    void editServiceFormPreservesInactiveState() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot-token", ADMIN_USERNAME, ADMIN_PASSWORD);

        JsonNode service = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50");
        long serviceId = service.get("id").asLong();

        tenantAdminPostForm(
                TENANT_SLUG,
                "/panel/services/" + serviceId,
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                Map.of(
                        "name", "Pizza",
                        "description", "Thin crust",
                        "price", "12.50",
                        "unit", "pcs",
                        "durationMinutes", "20",
                        "sortOrder", "1",
                        "status", "INACTIVE"
                )
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/services"));

        tenantAdminGet(TENANT_SLUG, "/panel/services/" + serviceId + "/edit", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"status\"")))
                .andExpect(content().string(containsString("value=\"INACTIVE\" selected=\"selected\"")));
    }

    @Test
    void listPagesAllowInlineStatusUpdates() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot-token", ADMIN_USERNAME, ADMIN_PASSWORD);

        JsonNode service = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50");
        long serviceId = service.get("id").asLong();
        JsonNode booking = createBooking(TENANT_SLUG, 777L, serviceId, 2);
        long bookingId = booking.get("id").asLong();

        tenantAdminGet(TENANT_SLUG, "/panel/bookings", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/panel/bookings/" + bookingId + "/status")))
                .andExpect(content().string(containsString("booking-status-" + bookingId)));

        tenantAdminPostForm(
                TENANT_SLUG,
                "/panel/bookings/" + bookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                Map.of("status", "CONFIRMED")
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/bookings"));

        tenantAdminGet(TENANT_SLUG, "/bookings/" + bookingId, ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CONFIRMED")));

        tenantAdminGet(TENANT_SLUG, "/panel/services", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/panel/services/" + serviceId + "/status")))
                .andExpect(content().string(containsString("service-status-" + serviceId)));

        tenantAdminPostForm(
                TENANT_SLUG,
                "/panel/services/" + serviceId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                Map.of("status", "INACTIVE")
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_ROOT + "/services"));

        tenantAdminGet(TENANT_SLUG, "/panel/services/" + serviceId + "/edit", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"INACTIVE\" selected=\"selected\"")));
    }

    @Test
    void editServiceFormRequiresDeleteConfirmation() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot-token", ADMIN_USERNAME, ADMIN_PASSWORD);

        JsonNode service = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50");
        long serviceId = service.get("id").asLong();

        tenantAdminGet(TENANT_SLUG, "/panel/services/" + serviceId + "/edit", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("onsubmit=\"return confirm('Delete this service?');\"")))
                .andExpect(content().string(containsString("Delete service")));
    }
}
