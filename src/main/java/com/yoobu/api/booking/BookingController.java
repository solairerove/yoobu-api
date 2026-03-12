package com.yoobu.api.booking;

import com.yoobu.api.booking.dto.BookingResponse;
import com.yoobu.api.booking.dto.CreateBookingRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/t/{slug}/bookings")
@RequiredArgsConstructor
public class BookingController {

    private static final String TELEGRAM_USER_HEADER = "X-Telegram-User-Id";

    private final BookingService bookingService;

    @PostMapping
    public BookingResponse createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(TELEGRAM_USER_HEADER) Long telegramUserId
    ) {
        return bookingService.createFoodOrder(request, telegramUserId);
    }

    @GetMapping("/my")
    public List<BookingResponse> getMyBookings(@RequestHeader(TELEGRAM_USER_HEADER) Long telegramUserId) {
        return bookingService.getMyBookings(telegramUserId);
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(
            @PathVariable Long bookingId,
            @RequestHeader(TELEGRAM_USER_HEADER) Long telegramUserId
    ) {
        return bookingService.getMyBooking(bookingId, telegramUserId);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(
            @PathVariable Long bookingId,
            @RequestHeader(TELEGRAM_USER_HEADER) Long telegramUserId
    ) {
        return bookingService.cancelMyBooking(bookingId, telegramUserId);
    }
}
