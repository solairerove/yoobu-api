package com.yoobu.api.admin;

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

    private static final String APPOINTMENT_TENANT_SLUG = "appointment-tenant";
    private static final String FOOD_TENANT_SLUG = "food-tenant";

    @Test
    void tenantWithUnsupportedTypeCannotCreateServicesOrBookings() throws Exception {
        createTenant(
                APPOINTMENT_TENANT_SLUG,
                "Appointment Tenant",
                TenantType.APPOINTMENT,
                "appointment-bot",
                "appointment-admin",
                "appointment-secret"
        );

        tenantAdminPostJson(
                APPOINTMENT_TENANT_SLUG,
                "/services",
                "appointment-admin",
                "appointment-secret",
                serviceRequest("Consultation", "50.00")
        )
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Tenant does not support catalog management"));

        tenantPublicPostJson(APPOINTMENT_TENANT_SLUG, "/bookings", 123L, bookingPayload(999L, 1))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Tenant does not support food ordering"));
    }

    @Test
    void bookingUnknownServiceIsRejected() throws Exception {
        createTenant(
                FOOD_TENANT_SLUG,
                "Food Tenant",
                TenantType.FOOD_ORDER,
                "food-bot",
                "food-admin",
                "food-secret"
        );

        tenantPublicPostJson(FOOD_TENANT_SLUG, "/bookings", 123L, bookingPayload(999L, 1))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Service not found"));
    }

    @Test
    void deletedServiceDisappearsFromCatalogAndCannotBeBooked() throws Exception {
        createTenant(
                FOOD_TENANT_SLUG,
                "Food Tenant",
                TenantType.FOOD_ORDER,
                "food-bot",
                "food-admin",
                "food-secret"
        );

        JsonNode service = createService(FOOD_TENANT_SLUG, "food-admin", "food-secret", "Pizza", "12.50");
        long serviceId = service.get("id").asLong();

        tenantAdminDelete(FOOD_TENANT_SLUG, "/services/" + serviceId, "food-admin", "food-secret")
                .andExpect(status().isNoContent());

        tenantPublicGet(FOOD_TENANT_SLUG, "/services")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        tenantPublicPostJson(FOOD_TENANT_SLUG, "/bookings", 123L, bookingPayload(serviceId, 1))
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

        tenantPublicPostJson("tenant-two", "/bookings", 555L, bookingPayload(serviceId, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        tenantAdminGet("tenant-two", "/bookings", "admin-one", "secret-one")
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Invalid admin credentials"));
    }
}
