package com.yoobu.api.tenant;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TenantTimeService {

    private final Clock clock;

    public ZonedDateTime now(Tenant tenant) {
        return clock.instant().atZone(zoneId(tenant));
    }

    public LocalDate today(Tenant tenant) {
        return now(tenant).toLocalDate();
    }

    public LocalDate earliestDeliveryDate(Tenant tenant, TenantSettings settings) {
        ZonedDateTime now = now(tenant);
        LocalDate today = now.toLocalDate();
        LocalTime cutoff = cutoffTime(settings);

        if (cutoff != null && !now.toLocalTime().isBefore(cutoff)) {
            return today.plusDays(1);
        }

        return today;
    }

    private ZoneId zoneId(Tenant tenant) {
        return ZoneId.of(tenant.getTimezone());
    }

    private LocalTime cutoffTime(TenantSettings settings) {
        TenantSettings.DeliverySettings delivery = settings.delivery();
        String cutoffHour = delivery.cutoffHour();
        String cutoffMinute = delivery.cutoffMinute();

        if (!StringUtils.hasText(cutoffHour) && !StringUtils.hasText(cutoffMinute)) {
            return null;
        }
        if (!StringUtils.hasText(cutoffHour) || !StringUtils.hasText(cutoffMinute)) {
            throw new IllegalStateException("Tenant cutoff config is incomplete");
        }

        try {
            return LocalTime.of(Integer.parseInt(cutoffHour), Integer.parseInt(cutoffMinute));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Tenant cutoff config is invalid", exception);
        }
    }
}
