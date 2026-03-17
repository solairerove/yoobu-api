package com.yoobu.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void requireCurrentTenantReturnsTenantFromContext() {
        Tenant tenant = new Tenant();
        tenant.setId(42L);
        TenantContext.setCurrentTenant(tenant);

        assertSame(tenant, TenantContext.requireCurrentTenant());
        assertEquals(42L, TenantContext.getRequiredTenantId());
    }

    @Test
    void requireCurrentTenantFailsWhenContextIsEmpty() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, TenantContext::requireCurrentTenant);

        assertEquals("Tenant context is not available", exception.getMessage());
    }
}
