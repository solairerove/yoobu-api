package com.yoobu.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantTimeServiceTest {

    @Test
    void todayUsesTenantTimezoneInsteadOfServerDefault() {
        Tenant tenant = tenant("Asia/Ho_Chi_Minh");
        TenantTimeService service = new TenantTimeService(Clock.fixed(
                Instant.parse("2026-03-14T18:30:00Z"),
                ZoneOffset.UTC
        ));

        assertEquals(LocalDate.of(2026, 3, 15), service.today(tenant));
    }

    @Test
    void cutoffMovesEarliestDeliveryDateToTomorrowInTenantTimezone() {
        Tenant tenant = tenant("Asia/Ho_Chi_Minh");
        TenantTimeService service = new TenantTimeService(Clock.fixed(
                Instant.parse("2026-03-14T11:30:00Z"),
                ZoneOffset.UTC
        ));

        LocalDate earliestDeliveryDate = service.earliestDeliveryDate(
                tenant,
                Map.of("cutoff_hour", "18", "cutoff_minute", "0")
        );

        assertEquals(LocalDate.of(2026, 3, 15), earliestDeliveryDate);
    }

    @Test
    void sameInstantProducesDifferentEarliestDatesForDifferentTenantTimezones() {
        Tenant utcTenant = tenant("UTC");
        Tenant hoChiMinhTenant = tenant("Asia/Ho_Chi_Minh");
        TenantTimeService service = new TenantTimeService(Clock.fixed(
                Instant.parse("2026-03-14T17:30:00Z"),
                ZoneOffset.UTC
        ));

        LocalDate utcEarliestDeliveryDate = service.earliestDeliveryDate(
                utcTenant,
                Map.of("cutoff_hour", "18", "cutoff_minute", "0")
        );
        LocalDate hoChiMinhEarliestDeliveryDate = service.earliestDeliveryDate(
                hoChiMinhTenant,
                Map.of("cutoff_hour", "18", "cutoff_minute", "0")
        );

        assertEquals(LocalDate.of(2026, 3, 14), utcEarliestDeliveryDate);
        assertEquals(LocalDate.of(2026, 3, 15), hoChiMinhEarliestDeliveryDate);
    }

    @Test
    void exactCutoffTimeAlreadyRequiresTomorrow() {
        Tenant tenant = tenant("Asia/Ho_Chi_Minh");
        TenantTimeService service = new TenantTimeService(Clock.fixed(
                Instant.parse("2026-03-14T11:00:00Z"),
                ZoneOffset.UTC
        ));

        LocalDate earliestDeliveryDate = service.earliestDeliveryDate(
                tenant,
                Map.of("cutoff_hour", "18", "cutoff_minute", "0")
        );

        assertEquals(LocalDate.of(2026, 3, 15), earliestDeliveryDate);
    }

    @Test
    void missingCutoffKeepsTodayAsEarliestDeliveryDate() {
        Tenant tenant = tenant("Europe/Warsaw");
        TenantTimeService service = new TenantTimeService(Clock.fixed(
                Instant.parse("2026-03-14T10:00:00Z"),
                ZoneOffset.UTC
        ));

        assertEquals(LocalDate.of(2026, 3, 14), service.earliestDeliveryDate(tenant, Map.of()));
    }

    private static Tenant tenant(String timezone) {
        Tenant tenant = new Tenant();
        tenant.setTimezone(timezone);
        return tenant;
    }
}
