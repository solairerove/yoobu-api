package com.yoobu.api.admin;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.booking.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class BookingLifecycleIT extends IntegrationTestSupport {

    @Test
    void customerCanCreateReadListAndCancelOwnBooking() throws Exception {
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();

        JsonNode booking = createBooking("food-tenant", 101L, serviceId, 2);
        long bookingId = booking.get("id").asLong();

        mockMvc.perform(get("/t/food-tenant/bookings/my")
                        .header("X-Telegram-User-Id", telegramUserId(101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bookingId))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].items[0].serviceName").value("Pizza"));

        mockMvc.perform(get("/t/food-tenant/bookings/" + bookingId)
                        .header("X-Telegram-User-Id", telegramUserId(101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.customerName").value("Alice"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.totalPrice").value(25.0));

        mockMvc.perform(post("/t/food-tenant/bookings/" + bookingId + "/cancel")
                        .header("X-Telegram-User-Id", telegramUserId(101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void customerCannotReadAnotherUsersBooking() throws Exception {
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking("food-tenant", 101L, serviceId, 1).get("id").asLong();

        mockMvc.perform(get("/t/food-tenant/bookings/" + bookingId)
                        .header("X-Telegram-User-Id", telegramUserId(202)))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Booking not found"));
    }

    @Test
    void adminCanListReadAndUpdateBookingStatus() throws Exception {
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking("food-tenant", 101L, serviceId, 1).get("id").asLong();

        mockMvc.perform(get("/admin/food-tenant/bookings")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bookingId))
                .andExpect(jsonPath("$[0].status").value("NEW"));

        mockMvc.perform(get("/admin/food-tenant/bookings/" + bookingId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("NEW"));

        mockMvc.perform(put("/admin/food-tenant/bookings/" + bookingId + "/status")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void completedBookingCannotBeCancelled() throws Exception {
        createTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking("food-tenant", 101L, serviceId, 1).get("id").asLong();

        updateBookingStatus("food-tenant", "food-admin", "food-secret", bookingId, BookingStatus.DONE);

        mockMvc.perform(post("/t/food-tenant/bookings/" + bookingId + "/cancel")
                        .header("X-Telegram-User-Id", telegramUserId(101)))
                .andExpect(status().isConflict())
                .andExpect(status().reason("Completed booking cannot be cancelled"));
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
            BookingStatus statusValue
    ) throws Exception {
        mockMvc.perform(put("/admin/" + slug + "/bookings/" + bookingId + "/status")
                        .header(AUTHORIZATION, basicAuth(adminUsername, adminPassword))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "%s"
                                }
                                """.formatted(statusValue.name())))
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
