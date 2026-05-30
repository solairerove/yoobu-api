package com.yoobu.api.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.booking.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class AnalyticsIT extends IntegrationTestSupport {

    private static final String SLUG = "analytics-tenant";
    private static final String ADMIN = "analytics-admin";
    private static final String PASSWORD = "analytics-secret";

    @Test
    void cancelledAndPendingOrdersAreExcludedFromCountAndRevenue() throws Exception {
        createFoodOrderTenant(SLUG, "Analytics Tenant", "analytics-bot", ADMIN, PASSWORD);
        long serviceId = createService(SLUG, ADMIN, PASSWORD, "Pizza", "10.00").get("id").asLong();

        // 1 DONE order at $10 — the only one that should count
        long doneId = createBooking(SLUG, 101L, serviceId, 1).get("id").asLong();
        confirmBookingPayment(SLUG, doneId, 101L);
        updateBookingStatus(SLUG, ADMIN, PASSWORD, doneId, BookingStatus.CONFIRMED);
        updateBookingStatus(SLUG, ADMIN, PASSWORD, doneId, BookingStatus.DELIVERING);
        updateBookingStatus(SLUG, ADMIN, PASSWORD, doneId, BookingStatus.DONE);

        // 1 CANCELLED order at $30 — must be excluded
        long cancelledId = createBooking(SLUG, 102L, serviceId, 3).get("id").asLong();
        updateBookingStatus(SLUG, ADMIN, PASSWORD, cancelledId, BookingStatus.CANCELLED);

        // 1 NEW order at $50 — must be excluded
        createBooking(SLUG, 103L, serviceId, 5);

        tenantAdminGet(SLUG, "/panel/analytics", ADMIN, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("USD 10.00")))
                .andExpect(content().string(not(containsString("USD 30.00"))))
                .andExpect(content().string(not(containsString("USD 50.00"))))
                .andExpect(content().string(not(containsString("USD 90.00"))));
    }

    @Test
    void topBuyersOnlyIncludesBuyersWithDoneOrders() throws Exception {
        createFoodOrderTenant(SLUG, "Analytics Tenant", "analytics-bot", ADMIN, PASSWORD);
        long serviceId = createService(SLUG, ADMIN, PASSWORD, "Pizza", "10.00").get("id").asLong();

        // User 101: 1 DONE order — should appear in top buyers
        long doneId = createBooking(SLUG, 101L, serviceId, 1).get("id").asLong();
        confirmBookingPayment(SLUG, doneId, 101L);
        updateBookingStatus(SLUG, ADMIN, PASSWORD, doneId, BookingStatus.CONFIRMED);
        updateBookingStatus(SLUG, ADMIN, PASSWORD, doneId, BookingStatus.DELIVERING);
        updateBookingStatus(SLUG, ADMIN, PASSWORD, doneId, BookingStatus.DONE);

        // User 102: only a CANCELLED order — must NOT appear in top buyers
        long cancelledId = createBooking(SLUG, 102L, serviceId, 5).get("id").asLong();
        updateBookingStatus(SLUG, ADMIN, PASSWORD, cancelledId, BookingStatus.CANCELLED);

        tenantAdminGet(SLUG, "/panel/analytics", ADMIN, PASSWORD)
                .andExpect(status().isOk())
                // buyer 101's DONE revenue appears exactly once in top buyers table
                .andExpect(content().string(containsString("USD 10.00")))
                // buyer 102's cancelled revenue must be absent
                .andExpect(content().string(not(containsString("USD 50.00"))));
    }
}
