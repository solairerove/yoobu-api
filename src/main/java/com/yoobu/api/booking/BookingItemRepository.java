package com.yoobu.api.booking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    List<BookingItem> findByBookingIdOrderByIdAsc(Long bookingId);

    List<BookingItem> findByBookingIdInOrderByBookingIdAscIdAsc(List<Long> bookingIds);
}
