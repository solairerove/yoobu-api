package com.yoobu.api.tenant;

import com.yoobu.api.config.MapStructConfig;
import com.yoobu.api.tenant.dto.TenantConfigResponse;
import com.yoobu.api.tenant.dto.TenantDetailResponse;
import com.yoobu.api.tenant.dto.TenantSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class, imports = TenantConfigKeys.class)
public interface TenantMapper {

    TenantSummaryResponse toSummaryResponse(Tenant tenant);

    @Mapping(target = "config", source = "config")
    TenantDetailResponse toDetailResponse(Tenant tenant, java.util.Map<String, String> config);

    @Mapping(target = "primaryColor", expression = "java(settings.branding().primaryColor())")
    @Mapping(target = "logoUrl", expression = "java(settings.branding().logoUrl())")
    @Mapping(target = "welcomeMessage", expression = "java(settings.branding().welcomeMessage())")
    TenantConfigResponse toConfigResponse(Tenant tenant, TenantSettings settings);
}
