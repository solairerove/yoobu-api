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

        assertEquals("admin-after", settings.adminUsername());
        assertEquals("#112233", settings.primaryColor());
    }

    @Test
    void asMapReturnsReadOnlyView() {
        TenantSettings settings = TenantSettings.fromMap(Map.of(TenantConfigKeys.ADMIN_USERNAME, "admin"));

        assertEquals("admin", settings.asMap().get(TenantConfigKeys.ADMIN_USERNAME));
        assertFalse(settings.hasAdminPassword());
        assertThrows(
                UnsupportedOperationException.class,
                () -> settings.asMap().put(TenantConfigKeys.PRIMARY_COLOR, "#000000")
        );
    }

    private static TenantConfig config(Tenant tenant, String key, String value) {
        TenantConfig config = new TenantConfig();
        config.setTenant(tenant);
        config.setKey(key);
        config.setValue(value);
        return config;
    }
}
