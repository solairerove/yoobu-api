package com.yoobu.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantSettingsServiceTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void getSettingsBuildsTenantSettingsFromRepositoryEntries() {
        Tenant tenant = new Tenant();
        TenantConfig username = config(tenant, TenantConfigKeys.ADMIN_USERNAME, "admin");
        TenantConfig color = config(tenant, TenantConfigKeys.PRIMARY_COLOR, "#123456");
        TenantSettingsService tenantSettingsService = new TenantSettingsService(repository(Map.of(
                7L, List.of(username, color)
        )));

        TenantSettings settings = tenantSettingsService.getSettings(7L);

        assertEquals("admin", settings.admin().username());
        assertEquals("#123456", settings.branding().primaryColor());
    }

    @Test
    void getCurrentTenantSettingsUsesTenantContextId() {
        Tenant tenant = new Tenant();
        tenant.setId(9L);
        TenantContext.setCurrentTenant(tenant);

        TenantConfig config = config(tenant, TenantConfigKeys.WELCOME_MESSAGE, "hello");
        TenantSettingsService tenantSettingsService = new TenantSettingsService(repository(Map.of(
                9L, List.of(config)
        )));

        TenantSettings settings = tenantSettingsService.getCurrentTenantSettings();

        assertEquals("hello", settings.branding().welcomeMessage());
    }

    private static TenantConfig config(Tenant tenant, String key, String value) {
        TenantConfig config = new TenantConfig();
        config.setTenant(tenant);
        config.setKey(key);
        config.setValue(value);
        return config;
    }

    private static TenantConfigRepository repository(Map<Long, List<TenantConfig>> entriesByTenantId) {
        Map<Long, List<TenantConfig>> storage = new HashMap<>(entriesByTenantId);
        return (TenantConfigRepository) Proxy.newProxyInstance(
                TenantConfigRepository.class.getClassLoader(),
                new Class<?>[]{TenantConfigRepository.class},
                (proxy, method, args) -> {
                    if ("findByTenantId".equals(method.getName())) {
                        return storage.getOrDefault((Long) args[0], List.of());
                    }
                    if ("toString".equals(method.getName())) {
                        return "TenantConfigRepositoryTestDouble";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException("Method not implemented in test double: " + method.getName());
                }
        );
    }
}
