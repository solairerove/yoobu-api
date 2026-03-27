package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import com.yoobu.api.tenant.dto.UpdateTenantRequest;
import java.time.LocalDate;
import java.time.ZoneId;
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

        tenantPublicGet(TENANT_SLUG, "/config")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(TENANT_SLUG))
                .andExpect(jsonPath("$.name").value("Food Tenant"))
                .andExpect(jsonPath("$.type").value("FOOD_ORDER"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.primaryColor").value("#112233"))
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.example.com/logo.png"))
                .andExpect(jsonPath("$.welcomeMessage").value("Hello from test"))
                .andExpect(jsonPath("$.checkoutNameHint").value("Your full name"))
                .andExpect(jsonPath("$.checkoutPhoneHint").value("+84..."))
                .andExpect(jsonPath("$.checkoutNoteHint").value("No onion, gate code, delivery code"))
                .andExpect(jsonPath("$.checkoutDeliveryHint").value("Apartment and entrance instructions"))
                .andExpect(jsonPath("$.paymentQrUrl").value("https://cdn.example.com/payment-qr.png"));
    }

    @Test
    void tenantPublicConfigReflectsUpdatedPaymentQrUrl() throws Exception {
        long tenantId = createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD)
                .get("id").asLong();

        UpdateTenantRequest updateRequest = new UpdateTenantRequest(
                "Food Tenant",
                TenantType.FOOD_ORDER,
                "food-bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                "#112233",
                "https://cdn.example.com/logo.png",
                "Hello from test",
                "Your full name",
                "+84...",
                "No onion, gate code, delivery code",
                "Apartment and entrance instructions",
                "https://cdn.example.com/payment-qr-updated.png",
                ADMIN_USERNAME,
                "",
                true,
                null,
                null
        );

        superAdminPutJson("/superadmin/tenants/" + tenantId, updateRequest)
                .andExpect(status().isOk());

        tenantPublicGet(TENANT_SLUG, "/config")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentQrUrl").value("https://cdn.example.com/payment-qr-updated.png"));
    }

    @Test
    void tenantPublicConfigAllowsClearingPaymentQrUrl() throws Exception {
        long tenantId = createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD)
                .get("id").asLong();

        UpdateTenantRequest updateRequest = new UpdateTenantRequest(
                "Food Tenant",
                TenantType.FOOD_ORDER,
                "food-bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                "#112233",
                "https://cdn.example.com/logo.png",
                "Hello from test",
                "Your full name",
                "+84...",
                "No onion, gate code, delivery code",
                "Apartment and entrance instructions",
                "",
                ADMIN_USERNAME,
                "",
                true,
                null,
                null
        );

        superAdminPutJson("/superadmin/tenants/" + tenantId, updateRequest)
                .andExpect(status().isOk());

        JsonNode configResponse = readJson(tenantPublicGet(TENANT_SLUG, "/config")
                .andExpect(status().isOk())
                .andReturn());
        JsonNode paymentQrUrl = configResponse.get("paymentQrUrl");
        assertTrue(paymentQrUrl == null || paymentQrUrl.isNull());
    }

    @Test
    void tenantAdminCanUpdateService() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        tenantAdminPutJson(
                TENANT_SLUG,
                "/services/" + serviceId,
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of(
                        "name", "Pasta",
                        "description", "Fresh pasta",
                        "price", 15.75,
                        "unit", "plate",
                        "durationMinutes", 20,
                        "sortOrder", 3,
                        "status", "INACTIVE"
                )
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceId))
                .andExpect(jsonPath("$.name").value("Pasta"))
                .andExpect(jsonPath("$.description").value("Fresh pasta"))
                .andExpect(jsonPath("$.price").value(15.75))
                .andExpect(jsonPath("$.unit").value("plate"))
                .andExpect(jsonPath("$.durationMinutes").value(20))
                .andExpect(jsonPath("$.sortOrder").value(3))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        tenantAdminGet(TENANT_SLUG, "/services", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("INACTIVE"));

        tenantPublicGet(TENANT_SLUG, "/services")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        tenantPublicPostJson(TENANT_SLUG, "/bookings", 101L, bookingPayload(serviceId, 1))
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

        tenantAdminDelete(TENANT_SLUG, "/services/" + serviceId, ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isNoContent());

        tenantAdminDelete(TENANT_SLUG, "/services/" + serviceId, ADMIN_USERNAME, ADMIN_PASSWORD)
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

        confirmBookingPayment(TENANT_SLUG, secondBookingId, 202L);
        updateBookingStatus(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, secondBookingId, "CONFIRMED");

        tenantAdminGet(TENANT_SLUG, "/bookings?status=CONFIRMED", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(secondBookingId))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        tenantAdminGet(TENANT_SLUG, "/bookings?status=NEW", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstBookingId))
                .andExpect(jsonPath("$[0].status").value("NEW"));
    }

    @Test
    void createServiceValidationRejectsMalformedPayload() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);

        tenantAdminPostJson(
                TENANT_SLUG,
                "/services",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                """
                        {
                          "name": "",
                          "price": null
                        }
                        """
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBookingValidationRejectsMalformedPayload() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);

        tenantPublicPostJson(
                TENANT_SLUG,
                "/bookings",
                101L,
                """
                        {
                          "customerName": "",
                          "customerPhone": "",
                          "deliveryAddress": "",
                          "deliveryDate": null,
                          "items": []
                        }
                        """
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBookingRejectsPastDeliveryDateRelativeToTenantTimezone() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        tenantPublicPostJson(TENANT_SLUG, "/bookings", 101L, bookingPayload(serviceId, 1, yesterday(DEFAULT_TENANT_TIMEZONE)))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Delivery date must be on or after " + LocalDate.now(
                        java.time.ZoneId.of(DEFAULT_TENANT_TIMEZONE))));
    }

    @Test
    void tenantPublicConfigReturnsCutoffFieldsAndEarliestDeliveryDateWhenConfigured() throws Exception {
        // Midnight cutoff means current time is always past it — earliestDeliveryDate is always tomorrow
        CreateTenantRequest request = new CreateTenantRequest(
                TENANT_SLUG, "Food Tenant", TenantType.FOOD_ORDER, "food-bot",
                123456789L, DEFAULT_TENANT_TIMEZONE, "USD",
                null, null, null, null, null, null, null, null,
                ADMIN_USERNAME, ADMIN_PASSWORD,
                0, 0
        );
        superAdminPostJson("/superadmin/tenants", request).andExpect(status().isOk());

        tenantPublicGet(TENANT_SLUG, "/config")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cutoffHour").value(0))
                .andExpect(jsonPath("$.cutoffMinute").value(0))
                .andExpect(jsonPath("$.earliestDeliveryDate").value(tomorrow().toString()));
    }

    @Test
    void tenantPublicConfigReturnsNullCutoffAndTodayAsEarliestDeliveryDateWhenNotConfigured() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);

        JsonNode config = readJson(tenantPublicGet(TENANT_SLUG, "/config")
                .andExpect(status().isOk())
                .andReturn());

        assertTrue(config.get("cutoffHour") == null || config.get("cutoffHour").isNull());
        assertTrue(config.get("cutoffMinute") == null || config.get("cutoffMinute").isNull());
        assertEquals(
                LocalDate.now(ZoneId.of(DEFAULT_TENANT_TIMEZONE)).toString(),
                config.get("earliestDeliveryDate").asText()
        );
    }

    @Test
    void createBookingRejectsTodayDeliveryWhenCurrentTimeIsPastCutoff() throws Exception {
        // Midnight cutoff means current time is always past it — today's delivery is always rejected
        CreateTenantRequest request = new CreateTenantRequest(
                TENANT_SLUG, "Food Tenant", TenantType.FOOD_ORDER, "food-bot",
                123456789L, DEFAULT_TENANT_TIMEZONE, "USD",
                null, null, null, null, null, null, null, null,
                ADMIN_USERNAME, ADMIN_PASSWORD,
                0, 0
        );
        superAdminPostJson("/superadmin/tenants", request).andExpect(status().isOk());

        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        LocalDate today = LocalDate.now(ZoneId.of(DEFAULT_TENANT_TIMEZONE));

        tenantPublicPostJson(TENANT_SLUG, "/bookings", 101L, bookingPayload(serviceId, 1, today))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Delivery date must be on or after " + tomorrow()));
    }
}
