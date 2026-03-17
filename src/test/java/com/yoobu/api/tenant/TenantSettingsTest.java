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
    void exposesDomainSlicesForAdminBrandingAndDelivery() {
        TenantSettings settings = TenantSettings.fromMap(Map.of(
                TenantConfigKeys.ADMIN_USERNAME, "root",
                TenantConfigKeys.ADMIN_PASSWORD, "hash",
                TenantConfigKeys.PRIMARY_COLOR, "#101010",
                TenantConfigKeys.LOGO_URL, "https://cdn.example.com/logo.png",
                TenantConfigKeys.WELCOME_MESSAGE, "hello",
                TenantConfigKeys.CUTOFF_HOUR, "18",
                TenantConfigKeys.CUTOFF_MINUTE, "30"
        ));

        assertEquals("root", settings.admin().username());
        assertEquals("hash", settings.admin().passwordHash());
        assertEquals("#101010", settings.branding().primaryColor());
        assertEquals("https://cdn.example.com/logo.png", settings.branding().logoUrl());
        assertEquals("hello", settings.branding().welcomeMessage());
        assertEquals("18", settings.delivery().cutoffHour());
        assertEquals("30", settings.delivery().cutoffMinute());
    }

    private static TenantConfig config(Tenant tenant, String key, String value) {
        TenantConfig config = new TenantConfig();
        config.setTenant(tenant);
        config.setKey(key);
        config.setValue(value);
        return config;
    }
}
