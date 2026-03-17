package com.yoobu.api.tenant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TenantSettings {

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

    public String adminUsername() {
        return values.get(TenantConfigKeys.ADMIN_USERNAME);
    }

    public String adminPasswordHash() {
        return values.get(TenantConfigKeys.ADMIN_PASSWORD);
    }

    public boolean hasAdminPassword() {
        return values.containsKey(TenantConfigKeys.ADMIN_PASSWORD);
    }

    public String primaryColor() {
        return values.get(TenantConfigKeys.PRIMARY_COLOR);
    }

    public String logoUrl() {
        return values.get(TenantConfigKeys.LOGO_URL);
    }

    public String welcomeMessage() {
        return values.get(TenantConfigKeys.WELCOME_MESSAGE);
    }

    public String cutoffHour() {
        return values.get(TenantConfigKeys.CUTOFF_HOUR);
    }

    public String cutoffMinute() {
        return values.get(TenantConfigKeys.CUTOFF_MINUTE);
    }
}
