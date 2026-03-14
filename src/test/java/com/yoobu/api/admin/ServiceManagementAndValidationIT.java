package com.yoobu.api.admin;

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

    @Test
    void tenantPublicConfigReturnsConfiguredBranding() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");

        mockMvc.perform(get("/t/food-tenant/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("food-tenant"))
                .andExpect(jsonPath("$.name").value("Food Tenant"))
                .andExpect(jsonPath("$.type").value("FOOD_ORDER"))
                .andExpect(jsonPath("$.primaryColor").value("#112233"))
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.example.com/logo.png"))
                .andExpect(jsonPath("$.welcomeMessage").value("Hello from test"));
    }

    @Test
    void tenantAdminCanUpdateService() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();

        mockMvc.perform(put("/admin/food-tenant/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pasta",
                                  "description": "Fresh pasta",
                                  "price": 15.75,
                                  "unit": "plate",
                                  "durationMinutes": 20,
                                  "sortOrder": 3,
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceId))
                .andExpect(jsonPath("$.name").value("Pasta"))
                .andExpect(jsonPath("$.description").value("Fresh pasta"))
                .andExpect(jsonPath("$.price").value(15.75))
                .andExpect(jsonPath("$.unit").value("plate"))
                .andExpect(jsonPath("$.durationMinutes").value(20))
                .andExpect(jsonPath("$.sortOrder").value(3));

        mockMvc.perform(get("/t/food-tenant/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Pasta"))
                .andExpect(jsonPath("$[0].price").value(15.75));

        JsonNode auditLog = latestAuditLog("service", "UPDATE");
        JsonNode oldValue = objectMapper.readTree(auditLog.get("old_value").asText());
        JsonNode newValue = objectMapper.readTree(auditLog.get("new_value").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Pizza", oldValue.get("name").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Pasta", newValue.get("name").asText());
        org.junit.jupiter.api.Assertions.assertEquals("UPDATE", auditLog.get("action").asText());
    }

    @Test
    void repeatedDeleteOfServiceReturnsNotFound() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();

        mockMvc.perform(delete("/admin/food-tenant/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/admin/food-tenant/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Service not found"));

        JsonNode auditLog = latestAuditLog("service", "DEACTIVATE");
        JsonNode newValue = objectMapper.readTree(auditLog.get("new_value").asText());
        org.junit.jupiter.api.Assertions.assertEquals(serviceId, auditLog.get("entity_id").asLong());
        org.junit.jupiter.api.Assertions.assertFalse(newValue.get("active").asBoolean());
        org.junit.jupiter.api.Assertions.assertFalse(newValue.get("deletedAt").isNull());
    }

    @Test
    void adminCanFilterBookingsByStatus() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();

        long firstBookingId = createBooking("food-tenant", 101L, serviceId, 1).get("id").asLong();
        long secondBookingId = createBooking("food-tenant", 202L, serviceId, 2).get("id").asLong();

        updateBookingStatus("food-tenant", "food-admin", "food-secret", secondBookingId, "CONFIRMED");

        mockMvc.perform(get("/admin/food-tenant/bookings?status=CONFIRMED")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(secondBookingId))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/admin/food-tenant/bookings?status=NEW")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstBookingId))
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void createServiceValidationRejectsMalformedPayload() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");

        mockMvc.perform(post("/admin/food-tenant/services")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret"))
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
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");

        mockMvc.perform(post("/t/food-tenant/bookings")
                        .header("X-Telegram-User-Id", telegramUserId(101))
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
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();

        mockMvc.perform(post("/t/food-tenant/bookings")
                        .header("X-Telegram-User-Id", telegramUserId(101))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(serviceId, 1, yesterday(DEFAULT_TENANT_TIMEZONE))))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Delivery date must be on or after " + LocalDate.now(
                        java.time.ZoneId.of(DEFAULT_TENANT_TIMEZONE))));
    }
}
