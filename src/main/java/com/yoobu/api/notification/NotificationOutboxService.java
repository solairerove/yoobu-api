package com.yoobu.api.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoobu.api.booking.Booking;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.tenant.Tenant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxService {

    private static final Set<BookingStatus> CUSTOMER_NOTIFIED_STATUSES =
            Set.of(BookingStatus.CONFIRMED, BookingStatus.DELIVERING, BookingStatus.DONE, BookingStatus.CANCELLED);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public record OrderItem(String serviceName, int quantity) {
    }

    public void enqueueBookingCreated(Booking booking, List<OrderItem> items, Tenant tenant) {
        if (!hasBot(tenant) || tenant.getOwnerTelegramId() == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", tenant.getOwnerTelegramId());
        payload.put("booking_id", booking.getId());
        payload.put("customer_name", booking.getCustomerName());
        payload.put("customer_phone", orEmpty(booking.getCustomerPhone()));
        payload.put("total_price", booking.getTotalPrice() != null ? booking.getTotalPrice().toPlainString() : "0");
        payload.put("currency", booking.getCurrency());
        payload.put("delivery_date", booking.getDeliveryDate() != null ? booking.getDeliveryDate().toString() : "");
        payload.put("delivery_address", orEmpty(booking.getDeliveryAddress()));
        payload.put("note", orEmpty(booking.getNote()));
        payload.put("items", items.stream()
                .map(i -> Map.of("service_name", i.serviceName(), "quantity", i.quantity()))
                .toList());
        insertAndNotify(booking.getTenant().getId(), "BOOKING_CREATED", payload);
    }

    public void enqueueStatusChanged(Booking booking, BookingStatus newStatus, Tenant tenant) {
        if (!hasBot(tenant) || !CUSTOMER_NOTIFIED_STATUSES.contains(newStatus)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", booking.getTelegramUserId());
        payload.put("booking_id", booking.getId());
        payload.put("new_status", newStatus.name());
        payload.put("delivery_date", booking.getDeliveryDate() != null ? booking.getDeliveryDate().toString() : "");
        payload.put("tracking_url", orEmpty(booking.getTrackingUrl()));
        insertAndNotify(booking.getTenant().getId(), "STATUS_CHANGED", payload);
    }

    public void enqueuePaymentConfirmed(Booking booking, Tenant tenant) {
        if (!hasBot(tenant) || tenant.getOwnerTelegramId() == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", tenant.getOwnerTelegramId());
        payload.put("booking_id", booking.getId());
        payload.put("customer_name", booking.getCustomerName());
        payload.put("total_price", booking.getTotalPrice() != null ? booking.getTotalPrice().toPlainString() : "0");
        payload.put("currency", booking.getCurrency());
        insertAndNotify(booking.getTenant().getId(), "PAYMENT_CONFIRMED", payload);
    }

    private void insertAndNotify(Long tenantId, String eventType, Map<String, Object> payload) {
        String json = toJson(payload);
        jdbcTemplate.update(
                "INSERT INTO notification_outbox (tenant_id, event_type, payload) VALUES (?, ?, ?::jsonb)",
                tenantId, eventType, json
        );
        jdbcTemplate.execute("NOTIFY notification_outbox");
        log.debug("enqueued {} for tenant_id={}", eventType, tenantId);
    }

    private boolean hasBot(Tenant tenant) {
        return StringUtils.hasText(tenant.getBotToken());
    }

    private String orEmpty(String value) {
        return value != null ? value : "";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize notification payload", e);
        }
    }
}
