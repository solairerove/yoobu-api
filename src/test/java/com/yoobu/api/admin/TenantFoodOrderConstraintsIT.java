package com.yoobu.api.admin;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.tenant.TenantType;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class TenantFoodOrderConstraintsIT extends IntegrationTestSupport {

    @Test
    void tenantWithUnsupportedTypeCannotCreateServicesOrBookings() throws Exception {
        createTenant(
                "appointment-tenant",
                "Appointment Tenant",
                TenantType.APPOINTMENT,
                "appointment-bot",
                "appointment-admin",
                "appointment-secret"
        );

        mockMvc.perform(post("/admin/appointment-tenant/services")
                        .header(AUTHORIZATION, basicAuth("appointment-admin", "appointment-secret"))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceRequest("Consultation", "50.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Tenant does not support food ordering"));

        mockMvc.perform(post("/t/appointment-tenant/bookings")
                        .header("X-Telegram-User-Id", telegramUserId(123))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(999L, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Tenant does not support food ordering"));
    }

    @Test
    void bookingUnknownServiceIsRejected() throws Exception {
        createTenant(
                "food-tenant",
                "Food Tenant",
                TenantType.FOOD_ORDER,
                "food-bot",
                "food-admin",
                "food-secret"
        );

        mockMvc.perform(post("/t/food-tenant/bookings")
                        .header("X-Telegram-User-Id", telegramUserId(123))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(999L, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Service not found"));
    }

    @Test
    void deletedServiceDisappearsFromCatalogAndCannotBeBooked() throws Exception {
        createTenant(
                "food-tenant",
                "Food Tenant",
                TenantType.FOOD_ORDER,
                "food-bot",
                "food-admin",
                "food-secret"
        );

        JsonNode service = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50");
        long serviceId = service.get("id").asLong();

        mockMvc.perform(delete("/admin/food-tenant/services/" + serviceId)
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/t/food-tenant/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/t/food-tenant/bookings")
                        .header("X-Telegram-User-Id", telegramUserId(123))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(serviceId, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Service not found"));
    }

    @Test
    void tenantAdminCannotReadAnotherTenantsBookings() throws Exception {
        createTenant(
                "tenant-one",
                "Tenant One",
                TenantType.FOOD_ORDER,
                "bot-one",
                "admin-one",
                "secret-one"
        );
        createTenant(
                "tenant-two",
                "Tenant Two",
                TenantType.FOOD_ORDER,
                "bot-two",
                "admin-two",
                "secret-two"
        );

        JsonNode service = createService("tenant-two", "admin-two", "secret-two", "Burger", "9.99");
        long serviceId = service.get("id").asLong();

        mockMvc.perform(post("/t/tenant-two/bookings")
                        .header("X-Telegram-User-Id", telegramUserId(555))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(serviceId, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        mockMvc.perform(get("/admin/tenant-two/bookings")
                        .header(AUTHORIZATION, basicAuth("admin-one", "secret-one")))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Invalid admin credentials"));
    }
}
