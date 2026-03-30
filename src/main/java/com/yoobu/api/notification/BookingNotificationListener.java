package com.yoobu.api.notification;

import com.yoobu.api.notification.event.BookingCreatedEvent;
import com.yoobu.api.notification.event.BookingStatusChangedEvent;
import com.yoobu.api.notification.event.PaymentConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BookingNotificationListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onBookingCreated(BookingCreatedEvent event) {
        notificationService.notifyAdminNewOrder(event.booking(), event.items(), event.tenant());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onBookingStatusChanged(BookingStatusChangedEvent event) {
        notificationService.notifyCustomerStatusChanged(
                event.booking(), event.newStatus(), event.tenant());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPaymentConfirmed(PaymentConfirmedEvent event) {
        notificationService.notifyAdminPaymentConfirmed(event.booking(), event.tenant());
    }
}
