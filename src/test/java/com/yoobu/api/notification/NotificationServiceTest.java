package com.yoobu.api.notification;

import com.yoobu.api.booking.Booking;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.notification.event.BookingCreatedEvent;
import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTest {

    private final RecordingTelegramClient telegramClient = new RecordingTelegramClient();
    private final NotificationService notificationService = new NotificationService(telegramClient);

    // --- notifyCustomerStatusChanged ---

    @Test
    void notifyCustomerStatusChanged_confirmed_sendsToCustomer() {
        notificationService.notifyCustomerStatusChanged(
                booking(1L, 100L, null), BookingStatus.CONFIRMED, tenant("bot-token", 999L));

        assertEquals(1, telegramClient.calls.size());
        var call = telegramClient.calls.getFirst();
        assertEquals(100L, call.chatId());
        assertTrue(call.text().contains("Order #1 confirmed"));
        assertTrue(call.text().contains("✅"));
    }

    @Test
    void notifyCustomerStatusChanged_delivering_withTracking_includesTrackUrl() {
        notificationService.notifyCustomerStatusChanged(
                booking(2L, 200L, "https://track.example.com/123"),
                BookingStatus.DELIVERING,
                tenant("bot-token", 999L));

        assertEquals(1, telegramClient.calls.size());
        String text = telegramClient.calls.getFirst().text();
        assertTrue(text.contains("Order #2 is on the way"));
        assertTrue(text.contains("https://track.example.com/123"));
    }

    @Test
    void notifyCustomerStatusChanged_delivering_withoutTracking_noTrackLine() {
        notificationService.notifyCustomerStatusChanged(
                booking(2L, 200L, null), BookingStatus.DELIVERING, tenant("bot-token", 999L));

        assertEquals(1, telegramClient.calls.size());
        String text = telegramClient.calls.getFirst().text();
        assertTrue(text.contains("Order #2 is on the way"));
        assertTrue(!text.contains("Track:"));
    }

    @Test
    void notifyCustomerStatusChanged_done_sendsThankYou() {
        notificationService.notifyCustomerStatusChanged(
                booking(3L, 300L, null), BookingStatus.DONE, tenant("bot-token", 999L));

        assertEquals(1, telegramClient.calls.size());
        String text = telegramClient.calls.getFirst().text();
        assertTrue(text.contains("Order #3 delivered"));
        assertTrue(text.contains("Thank you!"));
    }

    @Test
    void notifyCustomerStatusChanged_cancelled_sendsCancelMessage() {
        notificationService.notifyCustomerStatusChanged(
                booking(4L, 400L, null), BookingStatus.CANCELLED, tenant("bot-token", 999L));

        assertEquals(1, telegramClient.calls.size());
        assertTrue(telegramClient.calls.getFirst().text().contains("Order #4 cancelled"));
    }

    @Test
    void notifyCustomerStatusChanged_new_noMessage() {
        notificationService.notifyCustomerStatusChanged(
                booking(5L, 500L, null), BookingStatus.NEW, tenant("bot-token", 999L));

        assertTrue(telegramClient.calls.isEmpty());
    }

    @Test
    void notifyCustomerStatusChanged_paymentPending_noMessage() {
        notificationService.notifyCustomerStatusChanged(
                booking(5L, 500L, null), BookingStatus.PAYMENT_PENDING, tenant("bot-token", 999L));

        assertTrue(telegramClient.calls.isEmpty());
    }

    @Test
    void notifyCustomerStatusChanged_nullBotToken_noMessage() {
        notificationService.notifyCustomerStatusChanged(
                booking(1L, 100L, null), BookingStatus.CONFIRMED, tenant(null, 999L));

        assertTrue(telegramClient.calls.isEmpty());
    }

    // --- notifyAdminNewOrder ---

    @Test
    void notifyAdminNewOrder_sendsToOwner() {
        List<BookingCreatedEvent.OrderItem> items = List.of(
                new BookingCreatedEvent.OrderItem("Pizza", 2),
                new BookingCreatedEvent.OrderItem("Soup", 3)
        );

        notificationService.notifyAdminNewOrder(booking(1L, 100L, null), "USD", items, tenant("bot-token", 777L));

        assertEquals(1, telegramClient.calls.size());
        var call = telegramClient.calls.getFirst();
        assertEquals(777L, call.chatId());
        String text = call.text();
        assertTrue(text.contains("New order #1"));
        assertTrue(text.contains("Pizza × 2"));
        assertTrue(text.contains("Soup × 3"));
        assertTrue(text.contains("USD"));
    }

    @Test
    void notifyAdminNewOrder_nullOwnerTelegramId_noMessage() {
        notificationService.notifyAdminNewOrder(
                booking(1L, 100L, null), "USD", List.of(), tenant("bot-token", null));

        assertTrue(telegramClient.calls.isEmpty());
    }

    @Test
    void notifyAdminNewOrder_nullBotToken_noMessage() {
        notificationService.notifyAdminNewOrder(
                booking(1L, 100L, null), "USD", List.of(), tenant(null, 777L));

        assertTrue(telegramClient.calls.isEmpty());
    }

    // --- notifyAdminPaymentConfirmed ---

    @Test
    void notifyAdminPaymentConfirmed_sendsToOwner() {
        notificationService.notifyAdminPaymentConfirmed(
                booking(1L, 100L, null), "EUR", tenant("bot-token", 777L));

        assertEquals(1, telegramClient.calls.size());
        var call = telegramClient.calls.getFirst();
        assertEquals(777L, call.chatId());
        String text = call.text();
        assertTrue(text.contains("Payment confirmed"));
        assertTrue(text.contains("Order #1"));
        assertTrue(text.contains("EUR"));
    }

    @Test
    void notifyAdminPaymentConfirmed_nullOwnerTelegramId_noMessage() {
        notificationService.notifyAdminPaymentConfirmed(
                booking(1L, 100L, null), "EUR", tenant("bot-token", null));

        assertTrue(telegramClient.calls.isEmpty());
    }

    // --- helpers ---

    private static Booking booking(Long id, Long telegramUserId, String trackingUrl) {
        Booking booking = instantiate(Booking.class);
        booking.setId(id);
        booking.setTelegramUserId(telegramUserId);
        booking.setTrackingUrl(trackingUrl);
        booking.setCustomerName("Alex");
        booking.setCustomerPhone("+123456789");
        booking.setTotalPrice(new BigDecimal("50.00"));
        booking.setDeliveryDate(LocalDate.of(2026, 3, 25));
        booking.setNote(null);
        return booking;
    }

    private static Tenant tenant(String botToken, Long ownerTelegramId) {
        Tenant tenant = instantiate(Tenant.class);
        tenant.setBotToken(botToken);
        tenant.setOwnerTelegramId(ownerTelegramId);
        tenant.setType(TenantType.FOOD_ORDER);
        return tenant;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot instantiate " + type.getName(), ex);
        }
    }

    private static final class RecordingTelegramClient extends TelegramBotApiClient {

        record Call(Long chatId, String text) {}

        final List<Call> calls = new ArrayList<>();

        RecordingTelegramClient() {
            super();
        }

        @Override
        boolean sendMessage(String botToken, Long chatId, String text) {
            calls.add(new Call(chatId, text));
            return true;
        }
    }
}
