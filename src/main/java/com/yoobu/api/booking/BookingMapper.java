package com.yoobu.api.booking;

import com.yoobu.api.booking.dto.BookingItemResponse;
import com.yoobu.api.booking.dto.BookingResponse;
import com.yoobu.api.config.MapStructConfig;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface BookingMapper {

    @Mapping(target = "items", source = "items")
    BookingResponse toResponse(Booking booking, List<BookingItem> items);

    @Mapping(target = "serviceName", source = "service.name")
    @Mapping(target = "variantSize", source = "variantSize")
    @Mapping(target = "variantColor", source = "variantColor")
    BookingItemResponse toResponse(BookingItem item);
}
