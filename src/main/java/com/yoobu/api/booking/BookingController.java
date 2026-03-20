package com.yoobu.api.booking;

import com.yoobu.api.booking.dto.BookingResponse;
import com.yoobu.api.booking.dto.CreateBookingRequest;
import com.yoobu.api.telegram.TelegramPrincipal;
import com.yoobu.api.telegram.TelegramUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/t/{slug}/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public BookingResponse createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @TelegramPrincipal TelegramUser user
    ) {
        return bookingService.createFoodOrder(request, user.id());
    }

    @GetMapping("/my")
    public List<BookingResponse> getMyBookings(@TelegramPrincipal TelegramUser user) {
        return bookingService.getMyBookings(user.id());
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(
            @PathVariable Long bookingId,
            @TelegramPrincipal TelegramUser user
    ) {
        return bookingService.getMyBooking(bookingId, user.id());
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(
            @PathVariable Long bookingId,
            @TelegramPrincipal TelegramUser user
    ) {
        return bookingService.cancelMyBooking(bookingId, user.id());
    }

    @PostMapping("/{bookingId}/confirm-payment")
    public BookingResponse confirmPayment(
            @PathVariable Long bookingId,
            @TelegramPrincipal TelegramUser user
    ) {
        return bookingService.confirmMyBookingPayment(bookingId, user.id());
    }
}
