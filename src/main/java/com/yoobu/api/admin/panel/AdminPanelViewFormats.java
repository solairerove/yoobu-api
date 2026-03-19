package com.yoobu.api.admin.panel;

import com.yoobu.api.tenant.Tenant;
import com.yoobu.api.tenant.TenantContext;
import com.yoobu.api.tenant.TenantSettings;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("adminPanelViewFormats")
public class AdminPanelViewFormats {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Locale MONEY_LOCALE = Locale.US;

    public String money(BigDecimal value, String currency) {
        BigDecimal amount = value != null ? value : BigDecimal.ZERO;
        String currencyCode = StringUtils.hasText(currency) ? currency.trim() : TenantSettings.DEFAULT_CURRENCY;
        return currencyCode + " " + String.format(MONEY_LOCALE, "%,.2f", amount);
    }

    public String timestamp(OffsetDateTime value) {
        if (value == null) {
            return "N/A";
        }

        ZoneId zone = resolveTenantZone();
        return value.atZoneSameInstant(zone).format(TIMESTAMP_FORMATTER) + " " + zone.getId();
    }

    private ZoneId resolveTenantZone() {
        Tenant tenant = TenantContext.getCurrentTenant();
        if (tenant == null || !StringUtils.hasText(tenant.getTimezone())) {
            return UTC;
        }
        return ZoneId.of(tenant.getTimezone());
    }
}
