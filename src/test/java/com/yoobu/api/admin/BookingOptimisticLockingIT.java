package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.booking.Booking;
import com.yoobu.api.booking.BookingRepository;
import com.yoobu.api.booking.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class BookingOptimisticLockingIT extends IntegrationTestSupport {

    private static final String TENANT_SLUG = "food-tenant";
    private static final String ADMIN_USERNAME = "food-admin";
    private static final String ADMIN_PASSWORD = "food-secret";

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void staleBookingUpdateFailsWithOptimisticLockingConflict() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot", ADMIN_USERNAME, ADMIN_PASSWORD);
        long serviceId = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking(TENANT_SLUG, 101L, serviceId, 1).get("id").asLong();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Booking firstSnapshot = tx.execute(status -> bookingRepository.findById(bookingId).orElseThrow());
        Booking staleSnapshot = tx.execute(status -> bookingRepository.findById(bookingId).orElseThrow());
        assertNotNull(firstSnapshot);
        assertNotNull(staleSnapshot);

        Long initialVersion = firstSnapshot.getVersion();
        tx.executeWithoutResult(status -> {
            firstSnapshot.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.saveAndFlush(firstSnapshot);
        });

        Booking latest = tx.execute(status -> bookingRepository.findById(bookingId).orElseThrow());
        assertNotNull(latest);
        assertEquals(BookingStatus.CONFIRMED, latest.getStatus());
        assertEquals(initialVersion + 1, latest.getVersion());

        assertThrows(OptimisticLockingFailureException.class, () ->
                tx.executeWithoutResult(status -> {
                    staleSnapshot.setStatus(BookingStatus.CANCELLED);
                    bookingRepository.saveAndFlush(staleSnapshot);
                }));
    }
}
