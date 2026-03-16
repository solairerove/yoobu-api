package com.yoobu.api.tenant;

import com.yoobu.api.config.MapStructConfig;
import com.yoobu.api.tenant.dto.TenantConfigResponse;
import com.yoobu.api.tenant.dto.TenantDetailResponse;
import com.yoobu.api.tenant.dto.TenantSummaryResponse;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface TenantMapper {

    TenantSummaryResponse toSummaryResponse(Tenant tenant);

    @Mapping(target = "config", source = "config")
    TenantDetailResponse toDetailResponse(Tenant tenant, Map<String, String> config);

    @Mapping(target = "primaryColor", expression = "java(config.get(\"primary_color\"))")
    @Mapping(target = "logoUrl", expression = "java(config.get(\"logo_url\"))")
    @Mapping(target = "welcomeMessage", expression = "java(config.get(\"welcome_message\"))")
    TenantConfigResponse toConfigResponse(Tenant tenant, Map<String, String> config);
}
