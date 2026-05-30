package com.yoobu.api.catalog;

import com.yoobu.api.catalog.dto.ProductVariantResponse;
import com.yoobu.api.catalog.dto.ServiceResponse;
import com.yoobu.api.config.MapStructConfig;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface CatalogServiceMapper {

    @Mapping(target = "variants", expression = "java(java.util.List.of())")
    ServiceResponse toResponse(CatalogService service);

    @Mapping(target = "variants", source = "variants")
    ServiceResponse toResponse(CatalogService service, List<ProductVariantResponse> variants);

    @Mapping(target = "imageUrls", expression = "java(variant.getImages().stream().map(com.yoobu.api.catalog.ProductVariantImage::getImageUrl).collect(java.util.stream.Collectors.toList()))")
    ProductVariantResponse toResponse(ProductVariant variant);
}
