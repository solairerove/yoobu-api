package com.yoobu.api.notification;

import com.yoobu.api.booking.Booking;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.notification.event.BookingCreatedEvent;
import com.yoobu.api.tenant.Tenant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class NotificationService {

    private final TelegramBotApiClient telegramClient;

    void notifyCustomerStatusChanged(Booking booking, BookingStatus newStatus, Tenant tenant) {
        if (!StringUtils.hasText(tenant.getBotToken())) {
            return;
        }
        String text = customerStatusText(booking, newStatus);
        if (text == null) {
            return;
        }
        telegramClient.sendMessage(tenant.getBotToken(), booking.getTelegramUserId(), text);
    }

    void notifyAdminNewOrder(Booking booking, List<BookingCreatedEvent.OrderItem> items, Tenant tenant) {
        if (!StringUtils.hasText(tenant.getBotToken()) || tenant.getOwnerTelegramId() == null) {
            return;
        }
        telegramClient.sendMessage(tenant.getBotToken(), tenant.getOwnerTelegramId(),
                adminNewOrderText(booking, items));
    }

    void notifyAdminPaymentConfirmed(Booking booking, Tenant tenant) {
        if (!StringUtils.hasText(tenant.getBotToken()) || tenant.getOwnerTelegramId() == null) {
            return;
        }
        telegramClient.sendMessage(tenant.getBotToken(), tenant.getOwnerTelegramId(),
                adminPaymentConfirmedText(booking));
    }

    private String customerStatusText(Booking booking, BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> "✅ <b>Order #%d confirmed</b>\nDelivery: %s"
                    .formatted(booking.getId(), booking.getDeliveryDate());
            case DELIVERING -> {
                String base = "🚗 <b>Order #%d is on the way</b>".formatted(booking.getId());
                yield StringUtils.hasText(booking.getTrackingUrl())
                        ? base + "\nTrack: " + booking.getTrackingUrl()
                        : base;
            }
            case DONE -> "✅ <b>Order #%d delivered</b>\nThank you!".formatted(booking.getId());
            case CANCELLED -> "❌ <b>Order #%d cancelled</b>".formatted(booking.getId());
            default -> null;
        };
    }

    private String adminNewOrderText(Booking booking, List<BookingCreatedEvent.OrderItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("🆕 <b>New order #%d</b>\n".formatted(booking.getId()));
        sb.append("Customer: %s\n".formatted(booking.getCustomerName()));
        if (StringUtils.hasText(booking.getCustomerPhone())) {
            sb.append("Phone: %s\n".formatted(booking.getCustomerPhone()));
        }
        sb.append("Total: %s %s\n".formatted(booking.getTotalPrice(), booking.getCurrency()));
        sb.append("Delivery: %s\n".formatted(booking.getDeliveryDate()));
        sb.append("Address: %s".formatted(booking.getDeliveryAddress()));
        if (StringUtils.hasText(booking.getNote())) {
            sb.append("\nNote: %s".formatted(booking.getNote()));
        }
        if (!items.isEmpty()) {
            sb.append("\nItems:");
            for (BookingCreatedEvent.OrderItem item : items) {
                sb.append("\n• %s × %d".formatted(item.serviceName(), item.quantity()));
            }
        }
        return sb.toString();
    }

    private String adminPaymentConfirmedText(Booking booking) {
        return "💰 <b>Payment confirmed</b>\nOrder #%d\nCustomer: %s\nTotal: %s %s".formatted(
                booking.getId(),
                booking.getCustomerName(),
                booking.getTotalPrice(),
                booking.getCurrency()
        );
    }
}
