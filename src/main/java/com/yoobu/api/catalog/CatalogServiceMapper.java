package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.ServiceResponse;
import com.yoobu.api.config.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface CatalogServiceMapper {

    ServiceResponse toResponse(CatalogService service);
}
