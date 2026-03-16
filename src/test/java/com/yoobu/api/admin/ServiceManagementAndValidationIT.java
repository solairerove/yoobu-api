package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class ServiceManagementAndValidationIT extends IntegrationTestSupport {

    private static final String TENANT_SLUG = "food-tenant";
    private static final String ADMIN_USERNAME = "food-admin";
    private static final String ADMIN_PASSWORD = "food-secret";

    @Test
    void tenantPublicConfigReturnsConfiguredBranding() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(get("/t/" + TENANT_SLUG + "/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(TENANT_SLUG))
                .andExpect(jsonPath("$.name").value("Food Tenant"))
                .andExpect(jsonPath("$.type").value("FOOD_ORDER"))
                .andExpect(jsonPath("$.primaryColor").value("#112233"))
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.example.com/logo.png"))
                .andExpect(jsonPath("$.welcomeMessage").value("Hello from test"));
    }

    @Test
    void tenantAdminCanUpdateService() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        mockMvc.perform(put("/admin/" + TENANT_SLUG + "/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pasta",
                                  "description": "Fresh pasta",
                                  "price": 15.75,
                                  "unit": "plate",
                                  "durationMinutes": 20,
                                  "sortOrder": 3,
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceId))
                .andExpect(jsonPath("$.name").value("Pasta"))
                .andExpect(jsonPath("$.description").value("Fresh pasta"))
                .andExpect(jsonPath("$.price").value(15.75))
                .andExpect(jsonPath("$.unit").value("plate"))
                .andExpect(jsonPath("$.durationMinutes").value(20))
                .andExpect(jsonPath("$.sortOrder").value(3))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        getWithTenantAdminAuth("/admin/" + TENANT_SLUG + "/services", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("INACTIVE"));

        mockMvc.perform(get("/t/" + TENANT_SLUG + "/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/t/" + TENANT_SLUG + "/bookings")
                        .header("X-Telegram-User-Id", tenantUserId(101))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(serviceId, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Service not found"));

        JsonNode auditLog = latestAuditLog("service", "UPDATE");
        JsonNode oldValue = oldAuditValue(auditLog);
        JsonNode newValue = newAuditValue(auditLog);
        assertEquals("Pizza", oldValue.get("name").asText());
        assertEquals("Pasta", newValue.get("name").asText());
        assertEquals("INACTIVE", newValue.get("status").asText());
        assertEquals("UPDATE", auditLog.get("action").asText());
        assertEquals(ADMIN_USERNAME, auditLog.get("actor_id").asText());
    }

    @Test
    void repeatedDeleteOfServiceReturnsNotFound() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        mockMvc.perform(delete("/admin/" + TENANT_SLUG + "/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/admin/" + TENANT_SLUG + "/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Service not found"));

        JsonNode auditLog = latestAuditLog("service", "DELETE");
        JsonNode newValue = newAuditValue(auditLog);
        assertEquals(serviceId, auditLog.get("entity_id").asLong());
        assertEquals(ADMIN_USERNAME, auditLog.get("actor_id").asText());
        assertEquals("DELETED", newValue.get("status").asText());
        assertFalse(newValue.get("deletedAt").isNull());
    }

    @Test
    void adminCanFilterBookingsByStatus() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        long firstBookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();
        long secondBookingId = createBooking(TENANT_SLUG, 202L, serviceId, 2).get("id").asLong();

        updateBookingStatus(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, secondBookingId, "CONFIRMED");

        getWithTenantAdminAuth("/admin/" + TENANT_SLUG + "/bookings?status=CONFIRMED", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(secondBookingId))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        getWithTenantAdminAuth("/admin/" + TENANT_SLUG + "/bookings?status=NEW", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstBookingId))
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void createServiceValidationRejectsMalformedPayload() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(post("/admin/" + TENANT_SLUG + "/services")
                        .header(AUTHORIZATION, basicAuth(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "price": null
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBookingValidationRejectsMalformedPayload() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(post("/t/" + TENANT_SLUG + "/bookings")
                        .header("X-Telegram-User-Id", tenantUserId(101))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "",
                                  "customerPhone": "",
                                  "deliveryDate": null,
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBookingRejectsPastDeliveryDateRelativeToTenantTimezone() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        mockMvc.perform(post("/t/" + TENANT_SLUG + "/bookings")
                        .header("X-Telegram-User-Id", tenantUserId(101))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(serviceId, 1, yesterday(DEFAULT_TENANT_TIMEZONE))))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Delivery date must be on or after " + LocalDate.now(
                        java.time.ZoneId.of(DEFAULT_TENANT_TIMEZONE))));
    }
}
