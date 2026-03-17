package com.yoobu.api.tenant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TenantConfigs {

    private TenantConfigs() {
    }

    public static Map<String, String> toMap(List<TenantConfig> entries) {
        Map<String, String> config = new LinkedHashMap<>();
        entries.forEach(entry -> config.put(entry.getKey(), entry.getValue()));
        return config;
    }
}
