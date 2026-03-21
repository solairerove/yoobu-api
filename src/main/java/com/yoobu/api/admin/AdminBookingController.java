package com.yoobu.api.admin;

import com.yoobu.api.admin.dto.UpdateStatusRequest;
import com.yoobu.api.booking.BookingService;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.booking.dto.BookingResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/{slug}/bookings")
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping
    public List<BookingResponse> getBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate
    ) {
        return bookingService.getAdminBookings(status, deliveryDate);
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@PathVariable Long bookingId) {
        return bookingService.getAdminBooking(bookingId);
    }

    @PutMapping("/{bookingId}/status")
    public BookingResponse updateStatus(
            @PathVariable Long bookingId,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        return bookingService.updateBookingStatus(bookingId, request.status(), request.trackingUrl());
    }
}
