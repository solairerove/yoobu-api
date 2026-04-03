package com.yoobu.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yoobu.api.config.CacheNames;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class CacheIT extends IntegrationTestSupport {

    private static final String SLUG = "cache-tenant";
    private static final String ADMIN = "cache-admin";
    private static final String PASS = "cache-pass";

    @Autowired
    private CacheManager cacheManager;

    // --- tenant-services cache ---

    @Test
    void services_are_cached_after_first_public_request() throws Exception {
        long tenantId = createFoodOrderTenant(SLUG, "Cache Tenant", "cache-bot", ADMIN, PASS).get("id").asLong();

        tenantPublicGet(SLUG, "/services").andExpect(status().isOk());

        assertNotNull(servicesCache().get(tenantId), "Cache entry must exist after first GET /services");
    }

    @Test
    void services_cache_is_evicted_after_create() throws Exception {
        long tenantId = createFoodOrderTenant(SLUG, "Cache Tenant", "cache-bot", ADMIN, PASS).get("id").asLong();

        tenantPublicGet(SLUG, "/services").andExpect(status().isOk());
        assertNotNull(servicesCache().get(tenantId));

        createService(SLUG, ADMIN, PASS, "Pizza", "10.00");

        assertNull(servicesCache().get(tenantId), "Cache must be evicted after admin creates a service");
    }

    @Test
    void services_cache_is_evicted_after_update() throws Exception {
        long tenantId = createFoodOrderTenant(SLUG, "Cache Tenant", "cache-bot", ADMIN, PASS).get("id").asLong();
        long serviceId = createService(SLUG, ADMIN, PASS, "Pizza", "10.00").get("id").asLong();

        tenantPublicGet(SLUG, "/services").andExpect(status().isOk());
        assertNotNull(servicesCache().get(tenantId));

        tenantAdminPutJson(SLUG, "/services/" + serviceId, ADMIN, PASS,
                serviceRequest("Updated Pizza", "15.00"));

        assertNull(servicesCache().get(tenantId), "Cache must be evicted after admin updates a service");
    }

    @Test
    void services_cache_is_evicted_after_delete() throws Exception {
        long tenantId = createFoodOrderTenant(SLUG, "Cache Tenant", "cache-bot", ADMIN, PASS).get("id").asLong();
        long serviceId = createService(SLUG, ADMIN, PASS, "Pizza", "10.00").get("id").asLong();

        tenantPublicGet(SLUG, "/services").andExpect(status().isOk());
        assertNotNull(servicesCache().get(tenantId));

        tenantAdminDelete(SLUG, "/services/" + serviceId, ADMIN, PASS)
                .andExpect(status().isNoContent());

        assertNull(servicesCache().get(tenantId), "Cache must be evicted after admin deletes a service");
    }

    @Test
    void services_cache_reflects_fresh_data_after_eviction() throws Exception {
        createFoodOrderTenant(SLUG, "Cache Tenant", "cache-bot", ADMIN, PASS);

        tenantPublicGet(SLUG, "/services")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        createService(SLUG, ADMIN, PASS, "Pizza", "10.00");

        tenantPublicGet(SLUG, "/services")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Pizza"));
    }

    // --- tenant-config cache ---

    @Test
    void config_is_cached_after_first_public_request() throws Exception {
        long tenantId = createFoodOrderTenant(SLUG, "Cache Tenant", "cache-bot", ADMIN, PASS).get("id").asLong();

        tenantPublicGet(SLUG, "/config").andExpect(status().isOk());

        assertNotNull(configCache().get(tenantId), "Cache entry must exist after first GET /config");
    }

    @Test
    void config_cache_is_evicted_after_tenant_update() throws Exception {
        long tenantId = createFoodOrderTenant(SLUG, "Cache Tenant", "cache-bot", ADMIN, PASS).get("id").asLong();

        tenantPublicGet(SLUG, "/config").andExpect(status().isOk());
        assertNotNull(configCache().get(tenantId));

        superAdminPutJson("/superadmin/tenants/" + tenantId, updateRequest("Updated Name"))
                .andExpect(status().isOk());

        assertNull(configCache().get(tenantId), "Cache must be evicted after superadmin updates tenant");
    }

    @Test
    void config_cache_reflects_updated_name_after_eviction() throws Exception {
        long tenantId = createFoodOrderTenant(SLUG, "Cache Tenant", "cache-bot", ADMIN, PASS).get("id").asLong();

        tenantPublicGet(SLUG, "/config")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cache Tenant"));

        superAdminPutJson("/superadmin/tenants/" + tenantId, updateRequest("New Name"))
                .andExpect(status().isOk());

        tenantPublicGet(SLUG, "/config")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    // --- helpers ---

    private Cache servicesCache() {
        return cacheManager.getCache(CacheNames.TENANT_SERVICES);
    }

    private Cache configCache() {
        return cacheManager.getCache(CacheNames.TENANT_CONFIG);
    }

    private String updateRequest(String name) throws Exception {
        return """
                {
                  "name": "%s",
                  "type": "FOOD_ORDER",
                  "botToken": "cache-bot",
                  "ownerTelegramId": 123456789,
                  "timezone": "%s",
                  "currency": "USD",
                  "primaryColor": "#112233",
                  "logoUrl": "https://cdn.example.com/logo.png",
                  "welcomeMessage": "Hello from test",
                  "checkoutNameHint": "Your full name",
                  "checkoutPhoneHint": "+84...",
                  "checkoutNoteHint": "No onion, gate code, delivery code",
                  "checkoutDeliveryHint": "Apartment and entrance instructions",
                  "adminUsername": "%s",
                  "active": true
                }
                """.formatted(name, DEFAULT_TENANT_TIMEZONE, ADMIN);
    }
}
