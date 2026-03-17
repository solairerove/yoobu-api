package com.yoobu.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantConfigsTest {

    @Test
    void toMapUsesLatestValueForDuplicateKeys() {
        Tenant tenant = new Tenant();

        TenantConfig username = config(tenant, TenantConfigKeys.ADMIN_USERNAME, "admin-before");
        TenantConfig latestUsername = config(tenant, TenantConfigKeys.ADMIN_USERNAME, "admin-after");
        TenantConfig color = config(tenant, TenantConfigKeys.PRIMARY_COLOR, "#112233");

        Map<String, String> config = TenantConfigs.toMap(List.of(username, latestUsername, color));

        assertEquals("admin-after", config.get(TenantConfigKeys.ADMIN_USERNAME));
        assertEquals("#112233", config.get(TenantConfigKeys.PRIMARY_COLOR));
    }

    private static TenantConfig config(Tenant tenant, String key, String value) {
        TenantConfig config = new TenantConfig();
        config.setTenant(tenant);
        config.setKey(key);
        config.setValue(value);
        return config;
    }
}
