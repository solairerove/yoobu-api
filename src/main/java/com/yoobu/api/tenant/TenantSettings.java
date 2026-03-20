package com.yoobu.api.tenant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TenantSettings {

    public static final String DEFAULT_CURRENCY = "USD";

    private final Map<String, String> values;

    private TenantSettings(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static TenantSettings fromEntries(List<TenantConfig> entries) {
        Map<String, String> values = new LinkedHashMap<>();
        entries.forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        return new TenantSettings(values);
    }

    public static TenantSettings fromMap(Map<String, String> values) {
        return new TenantSettings(new LinkedHashMap<>(values));
    }

    public Map<String, String> asMap() {
        return values;
    }

    public AdminSettings admin() {
        return new AdminSettings(
                values.get(TenantConfigKeys.ADMIN_USERNAME),
                values.get(TenantConfigKeys.ADMIN_PASSWORD),
                values.containsKey(TenantConfigKeys.ADMIN_PASSWORD)
        );
    }

    public BrandingSettings branding() {
        return new BrandingSettings(
                values.get(TenantConfigKeys.PRIMARY_COLOR),
                values.get(TenantConfigKeys.LOGO_URL),
                values.get(TenantConfigKeys.WELCOME_MESSAGE)
        );
    }

    public CheckoutSettings checkout() {
        return new CheckoutSettings(
                values.get(TenantConfigKeys.CHECKOUT_NAME_HINT),
                values.get(TenantConfigKeys.CHECKOUT_PHONE_HINT),
                values.get(TenantConfigKeys.CHECKOUT_NOTE_HINT)
        );
    }

    public DeliverySettings delivery() {
        return new DeliverySettings(
                values.get(TenantConfigKeys.CUTOFF_HOUR),
                values.get(TenantConfigKeys.CUTOFF_MINUTE)
        );
    }

    public PricingSettings pricing() {
        String configuredCurrency = values.get(TenantConfigKeys.CURRENCY);
        if (configuredCurrency == null || configuredCurrency.isBlank()) {
            return new PricingSettings(DEFAULT_CURRENCY);
        }
        return new PricingSettings(configuredCurrency);
    }

    public PaymentSettings payment() {
        return new PaymentSettings(values.get(TenantConfigKeys.PAYMENT_QR_URL));
    }

    public record AdminSettings(
            String username,
            String passwordHash,
            boolean passwordConfigured
    ) {
    }

    public record BrandingSettings(
            String primaryColor,
            String logoUrl,
            String welcomeMessage
    ) {
    }

    public record CheckoutSettings(
            String nameHint,
            String phoneHint,
            String noteHint
    ) {
    }

    public record DeliverySettings(
            String cutoffHour,
            String cutoffMinute
    ) {
    }

    public record PricingSettings(
            String currency
    ) {
    }

    public record PaymentSettings(
            String qrUrl
    ) {
    }
}
