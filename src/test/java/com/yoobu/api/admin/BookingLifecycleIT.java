package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();

        JsonNode booking = createBooking("food-tenant", 101L, serviceId, 2);
        long bookingId = booking.get("id").asLong();

        mockMvc.perform(get("/t/food-tenant/bookings/my")
                        .header("X-Telegram-User-Id", tenantUserId(101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bookingId))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].items[0].serviceName").value("Pizza"));

        mockMvc.perform(get("/t/food-tenant/bookings/" + bookingId)
                        .header("X-Telegram-User-Id", tenantUserId(101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.customerName").value("Alice"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.totalPrice").value(25.0));

        mockMvc.perform(post("/t/food-tenant/bookings/" + bookingId + "/cancel")
                        .header("X-Telegram-User-Id", tenantUserId(101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        JsonNode createAuditLog = latestAuditLog("booking", "CREATE");
        JsonNode createValue = newAuditValue(createAuditLog);
        assertEquals("101", createAuditLog.get("actor_id").asText());
        assertEquals("NEW", createValue.get("status").asText());
        assertEquals(2, createValue.get("items").get(0).get("quantity").asInt());

        JsonNode cancelAuditLog = latestAuditLog("booking", "CANCEL");
        JsonNode oldValue = oldAuditValue(cancelAuditLog);
        JsonNode newValue = newAuditValue(cancelAuditLog);
        assertEquals("NEW", oldValue.get("status").asText());
        assertEquals("CANCELLED", newValue.get("status").asText());
    }

    @Test
    void customerCannotReadAnotherUsersBooking() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking("food-tenant", 101L, serviceId, 1).get("id").asLong();

        mockMvc.perform(get("/t/food-tenant/bookings/" + bookingId)
                        .header("X-Telegram-User-Id", tenantUserId(202)))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Booking not found"));
    }

    @Test
    void adminCanListReadAndUpdateBookingStatus() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
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

        JsonNode auditLog = latestAuditLog("booking", "UPDATE_STATUS");
        JsonNode oldValue = oldAuditValue(auditLog);
        JsonNode newValue = newAuditValue(auditLog);
        assertEquals("food-admin", auditLog.get("actor_id").asText());
        assertEquals("NEW", oldValue.get("status").asText());
        assertEquals("CONFIRMED", newValue.get("status").asText());
    }

    @Test
    void completedBookingCannotBeCancelled() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot", "food-admin", "food-secret");
        long serviceId = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking("food-tenant", 101L, serviceId, 1).get("id").asLong();

        updateBookingStatus("food-tenant", "food-admin", "food-secret", bookingId, BookingStatus.DONE);

        mockMvc.perform(post("/t/food-tenant/bookings/" + bookingId + "/cancel")
                        .header("X-Telegram-User-Id", tenantUserId(101)))
                .andExpect(status().isConflict())
                .andExpect(status().reason("Completed booking cannot be cancelled"));
    }
}
