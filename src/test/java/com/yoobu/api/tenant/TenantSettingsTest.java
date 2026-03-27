package com.yoobu.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantSettingsTest {

    @Test
    void fromEntriesUsesLatestValueForDuplicateKeys() {
        Tenant tenant = new Tenant();

        TenantConfig username = config(tenant, TenantConfigKeys.ADMIN_USERNAME, "admin-before");
        TenantConfig latestUsername = config(tenant, TenantConfigKeys.ADMIN_USERNAME, "admin-after");
        TenantConfig color = config(tenant, TenantConfigKeys.PRIMARY_COLOR, "#112233");

        TenantSettings settings = TenantSettings.fromEntries(List.of(username, latestUsername, color));

        assertEquals("admin-after", settings.admin().username());
        assertEquals("#112233", settings.branding().primaryColor());
    }

    @Test
    void asMapReturnsReadOnlyView() {
        TenantSettings settings = TenantSettings.fromMap(Map.of(TenantConfigKeys.ADMIN_USERNAME, "admin"));

        assertEquals("admin", settings.asMap().get(TenantConfigKeys.ADMIN_USERNAME));
        assertFalse(settings.admin().passwordConfigured());
        assertThrows(
                UnsupportedOperationException.class,
                () -> settings.asMap().put(TenantConfigKeys.PRIMARY_COLOR, "#000000")
        );
    }

    @Test
    void exposesDomainSlicesForAdminBrandingCheckoutAndDelivery() {
        TenantSettings settings = TenantSettings.fromMap(Map.ofEntries(
                Map.entry(TenantConfigKeys.ADMIN_USERNAME, "root"),
                Map.entry(TenantConfigKeys.ADMIN_PASSWORD, "hash"),
                Map.entry(TenantConfigKeys.PRIMARY_COLOR, "#101010"),
                Map.entry(TenantConfigKeys.LOGO_URL, "https://cdn.example.com/logo.png"),
                Map.entry(TenantConfigKeys.WELCOME_MESSAGE, "hello"),
                Map.entry(TenantConfigKeys.CHECKOUT_NAME_HINT, "Receiver name"),
                Map.entry(TenantConfigKeys.CHECKOUT_PHONE_HINT, "+84..."),
                Map.entry(TenantConfigKeys.CHECKOUT_NOTE_HINT, "No onion, gate code"),
                Map.entry(TenantConfigKeys.CHECKOUT_DELIVERY_HINT, "Apartment and entrance"),
                Map.entry(TenantConfigKeys.CURRENCY, "THB"),
                Map.entry(TenantConfigKeys.CUTOFF_HOUR, "18"),
                Map.entry(TenantConfigKeys.CUTOFF_MINUTE, "30")
        ));

        assertEquals("root", settings.admin().username());
        assertEquals("hash", settings.admin().passwordHash());
        assertEquals("#101010", settings.branding().primaryColor());
        assertEquals("https://cdn.example.com/logo.png", settings.branding().logoUrl());
        assertEquals("hello", settings.branding().welcomeMessage());
        assertEquals("Receiver name", settings.checkout().nameHint());
        assertEquals("+84...", settings.checkout().phoneHint());
        assertEquals("No onion, gate code", settings.checkout().noteHint());
        assertEquals("Apartment and entrance", settings.checkout().deliveryHint());
        assertEquals("THB", settings.pricing().currency());
        assertEquals("18", settings.delivery().cutoffHour());
        assertEquals("30", settings.delivery().cutoffMinute());
    }

    @Test
    void pricingFallsBackToDefaultCurrencyWhenUnset() {
        TenantSettings settings = TenantSettings.fromMap(Map.of(TenantConfigKeys.ADMIN_USERNAME, "root"));

        assertEquals("USD", settings.pricing().currency());
    }

    private static TenantConfig config(Tenant tenant, String key, String value) {
        TenantConfig config = new TenantConfig();
        config.setTenant(tenant);
        config.setKey(key);
        config.setValue(value);
        return config;
    }
}
