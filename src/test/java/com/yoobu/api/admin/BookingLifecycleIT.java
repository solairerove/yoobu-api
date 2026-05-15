package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.UpdateTenantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class BookingLifecycleIT extends IntegrationTestSupport {

    private static final String TENANT_SLUG = "food-tenant";
    private static final String ADMIN_USERNAME = "food-admin";
    private static final String ADMIN_PASSWORD = "food-secret";

    @Test
    void customerCanCreateReadListAndCancelOwnBooking() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        JsonNode booking = createBooking(TENANT_SLUG, 101L, serviceId, 2);
        long bookingId = booking.get("id").asLong();

        tenantPublicGetAsUser(TENANT_SLUG, "/bookings/my", 101L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bookingId))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].customerPhone").value("+48123456789"))
                .andExpect(jsonPath("$[0].items[0].serviceName").value("Pizza"));

        tenantPublicGetAsUser(TENANT_SLUG, "/bookings/" + bookingId, 101L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.customerName").value("Alice"))
                .andExpect(jsonPath("$.customerPhone").value("+48123456789"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.totalPrice").value(25.0));

        tenantPublicPostJson(TENANT_SLUG, "/bookings/" + bookingId + "/cancel", 101L, "")
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
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        tenantPublicGetAsUser(TENANT_SLUG, "/bookings/" + bookingId, 202L)
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Booking not found"));
    }

    @Test
    void customerCanConfirmPaymentAndAuditIsRecorded() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        tenantPublicPostJson(TENANT_SLUG, "/bookings/" + bookingId + "/confirm-payment", 101L, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));

        JsonNode auditLog = latestAuditLog("booking", "CONFIRM_PAYMENT");
        JsonNode oldValue = oldAuditValue(auditLog);
        JsonNode newValue = newAuditValue(auditLog);
        assertEquals("101", auditLog.get("actor_id").asText());
        assertEquals("NEW", oldValue.get("status").asText());
        assertEquals("PAYMENT_PENDING", newValue.get("status").asText());
    }

    @Test
    void customerCannotConfirmPaymentWhenBookingStatusIsNotNew() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();
        long paymentPendingBookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();
        long cancelledBookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        confirmBookingPayment(TENANT_SLUG, paymentPendingBookingId, 101L);
        tenantPublicPostJson(TENANT_SLUG, "/bookings/" + paymentPendingBookingId + "/confirm-payment", 101L, "")
                .andExpect(status().isConflict())
                .andExpect(status().reason("Payment can only be confirmed for booking in NEW status"));

        tenantPublicPostJson(TENANT_SLUG, "/bookings/" + cancelledBookingId + "/cancel", 101L, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        tenantPublicPostJson(TENANT_SLUG, "/bookings/" + cancelledBookingId + "/confirm-payment", 101L, "")
                .andExpect(status().isConflict())
                .andExpect(status().reason("Payment can only be confirmed for booking in NEW status"));
    }

    @Test
    void customerCannotConfirmAnotherUsersBooking() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        tenantPublicPostJson(TENANT_SLUG, "/bookings/" + bookingId + "/confirm-payment", 202L, "")
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Booking not found"));
    }

    @Test
    void customerCannotConfirmPaymentForUnknownBooking() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);

        tenantPublicPostJson(TENANT_SLUG, "/bookings/999999/confirm-payment", 101L, "")
                .andExpect(status().isNotFound())
                .andExpect(status().reason("Booking not found"));
    }

    @Test
    void adminCanListReadAndUpdateBookingStatus() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        tenantAdminGet(TENANT_SLUG, "/bookings", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bookingId))
                .andExpect(jsonPath("$[0].status").value("NEW"));

        tenantAdminGet(TENANT_SLUG, "/bookings/" + bookingId, ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("NEW"));

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + bookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "CONFIRMED")
        )
                .andExpect(status().isConflict())
                .andExpect(status().reason("Invalid booking status transition from NEW to CONFIRMED"));

        tenantPublicPostJson(TENANT_SLUG, "/bookings/" + bookingId + "/confirm-payment", 101L, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + bookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "CONFIRMED")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        JsonNode auditLog = latestAuditLog("booking", "UPDATE_STATUS");
        JsonNode oldValue = oldAuditValue(auditLog);
        JsonNode newValue = newAuditValue(auditLog);
        assertEquals(ADMIN_USERNAME, auditLog.get("actor_id").asText());
        assertEquals("PAYMENT_PENDING", oldValue.get("status").asText());
        assertEquals("CONFIRMED", newValue.get("status").asText());
    }

    @Test
    void adminCanSetTrackingUrlWhenMovingBookingToDelivering() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();
        String trackingUrl = "https://grab.example.com/track/abc-123";

        confirmBookingPayment(TENANT_SLUG, bookingId, 101L);
        updateBookingStatus(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, bookingId, BookingStatus.CONFIRMED);

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + bookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "DELIVERING", "trackingUrl", trackingUrl)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERING"))
                .andExpect(jsonPath("$.trackingUrl").value(trackingUrl));

        tenantPublicGetAsUser(TENANT_SLUG, "/bookings/" + bookingId, 101L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERING"))
                .andExpect(jsonPath("$.trackingUrl").value(trackingUrl));

        JsonNode auditLog = latestAuditLog("booking", "UPDATE_STATUS");
        JsonNode oldValue = oldAuditValue(auditLog);
        JsonNode newValue = newAuditValue(auditLog);
        assertEquals("CONFIRMED", oldValue.get("status").asText());
        assertEquals("DELIVERING", newValue.get("status").asText());
        assertEquals(trackingUrl, newValue.get("trackingUrl").asText());
    }

    @Test
    void adminTransitionMatrixRejectsInvalidAndAllowsExpectedTransitions() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        long firstBookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();
        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + firstBookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "DONE")
        )
                .andExpect(status().isConflict())
                .andExpect(status().reason("Invalid booking status transition from NEW to DONE"));

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + firstBookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "CANCELLED")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + firstBookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "CONFIRMED")
        )
                .andExpect(status().isConflict())
                .andExpect(status().reason("Invalid booking status transition from CANCELLED to CONFIRMED"));

        long secondBookingId = createBooking(TENANT_SLUG, 202L, serviceId, 1).get("id").asLong();
        confirmBookingPayment(TENANT_SLUG, secondBookingId, 202L);

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + secondBookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "DONE")
        )
                .andExpect(status().isConflict())
                .andExpect(status().reason("Invalid booking status transition from PAYMENT_PENDING to DONE"));

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + secondBookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "CONFIRMED")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + secondBookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "DONE")
        )
                .andExpect(status().isConflict())
                .andExpect(status().reason("Invalid booking status transition from CONFIRMED to DONE"));

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + secondBookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "DELIVERING")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERING"));

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + secondBookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "DONE")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + secondBookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "CANCELLED")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void completedBookingCannotBeCancelled() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        confirmBookingPayment(TENANT_SLUG, bookingId, 101L);
        updateBookingStatus(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, bookingId, BookingStatus.CONFIRMED);
        updateBookingStatus(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, bookingId, BookingStatus.DELIVERING);
        updateBookingStatus(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, bookingId, BookingStatus.DONE);

        tenantPublicPostJson(TENANT_SLUG, "/bookings/" + bookingId + "/cancel", 101L, "")
                .andExpect(status().isConflict())
                .andExpect(status().reason("Completed booking cannot be cancelled"));
    }

    @Test
    void adminCannotMoveCompletedBookingBackToNew() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        confirmBookingPayment(TENANT_SLUG, bookingId, 101L);
        updateBookingStatus(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, bookingId, BookingStatus.CONFIRMED);
        updateBookingStatus(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, bookingId, BookingStatus.DELIVERING);
        updateBookingStatus(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, bookingId, BookingStatus.DONE);

        tenantAdminPutJson(
                TENANT_SLUG,
                "/bookings/" + bookingId + "/status",
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                java.util.Map.of("status", "NEW")
        )
                .andExpect(status().isConflict())
                .andExpect(status().reason("Invalid booking status transition from DONE to NEW"));
    }

    @Test
    void existingBookingCurrencyStaysUnchangedWhenTenantCurrencyChanges() throws Exception {
        long tenantId = createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD)
                .get("id").asLong();
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();

        long firstBookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        tenantPublicGetAsUser(TENANT_SLUG, "/bookings/" + firstBookingId, 101L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));

        UpdateTenantRequest updateRequest = new UpdateTenantRequest(
                "Food Tenant",
                TenantType.FOOD_ORDER,
                "food-bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "THB",
                "#112233",
                "https://cdn.example.com/logo.png",
                null,
                "Hello from test",
                "Your full name",
                "+84...",
                "No onion, gate code, delivery code",
                "Apartment and entrance instructions",
                "https://cdn.example.com/payment-qr-updated.png",
                null,
                null,
                ADMIN_USERNAME,
                "",
                true,
                null,
                null
        );

        superAdminPutJson("/superadmin/tenants/" + tenantId, updateRequest)
                .andExpect(status().isOk());

        long secondBookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        tenantPublicGetAsUser(TENANT_SLUG, "/bookings/" + firstBookingId, 101L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));

        tenantPublicGetAsUser(TENANT_SLUG, "/bookings/" + secondBookingId, 101L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("THB"));
    }
}
