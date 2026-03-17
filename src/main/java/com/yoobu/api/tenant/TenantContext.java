package com.yoobu.api.tenant;

public final class TenantContext {

    private static final ThreadLocal<Tenant> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCurrentTenant(Tenant tenant) {
        CURRENT_TENANT.set(tenant);
    }

    public static Tenant getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static Tenant requireCurrentTenant() {
        Tenant tenant = CURRENT_TENANT.get();
        if (tenant == null) {
            throw new IllegalStateException("Tenant context is not available");
        }
        return tenant;
    }

    public static Long getRequiredTenantId() {
        Tenant tenant = requireCurrentTenant();
        if (tenant.getId() == null) {
            throw new IllegalStateException("Tenant context is not available");
        }
        return tenant.getId();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
