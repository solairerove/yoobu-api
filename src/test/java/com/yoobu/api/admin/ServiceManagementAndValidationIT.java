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
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class ServiceManagementAndValidationIT extends IntegrationTestSupport {

    @Test
    void tenantPublicConfigReturnsConfiguredBranding() throws Exception {
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");

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
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
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
    }

    @Test
    void repeatedDeleteOfServiceReturnsNotFound() throws Exception {
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();

        mockMvc.perform(delete("/admin/food-tenant/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/admin/food-tenant/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Service not found"));
    }

    @Test
    void adminCanFilterBookingsByStatus() throws Exception {
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
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
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");

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
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");

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

    private JsonNode createService(
            String slug,
            String adminUsername,
            String adminPassword,
            String name,
            String price
    ) throws Exception {
        return readJson(mockMvc.perform(post("/admin/" + slug + "/services")
                        .header(AUTHORIZATION, basicAuth(adminUsername, adminPassword))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceRequest(name, price))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode createBooking(String slug, long telegramUserId, long serviceId, int quantity) throws Exception {
        return readJson(mockMvc.perform(post("/t/" + slug + "/bookings")
                        .header("X-Telegram-User-Id", telegramUserId(telegramUserId))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(serviceId, quantity)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void updateBookingStatus(
            String slug,
            String adminUsername,
            String adminPassword,
            long bookingId,
            String statusValue
    ) throws Exception {
        mockMvc.perform(put("/admin/" + slug + "/bookings/" + bookingId + "/status")
                        .header(AUTHORIZATION, basicAuth(adminUsername, adminPassword))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "%s"
                                }
                                """.formatted(statusValue)))
                .andExpect(status().isOk());
    }

    private String bookingPayload(long serviceId, int quantity) {
        return """
                {
                  "customerName": "Alice",
                  "customerPhone": "+48123456789",
                  "deliveryDate": "%s",
                  "note": "Leave at the door",
                  "items": [
                    {
                      "serviceId": %d,
                      "quantity": %d
                    }
                  ]
                }
                """.formatted(tomorrow(), serviceId, quantity);
    }
}
