package com.yoobu.api.booking;

import com.yoobu.api.booking.dto.BookingResponse;
import com.yoobu.api.booking.dto.CreateEcommerceOrderRequest;
import com.yoobu.api.telegram.TelegramPrincipal;
import com.yoobu.api.telegram.TelegramUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/t/{slug}/orders")
@RequiredArgsConstructor
public class EcommerceBookingController {

    private final BookingService bookingService;

    @PostMapping
    public BookingResponse createOrder(
            @Valid @RequestBody CreateEcommerceOrderRequest request,
            @TelegramPrincipal TelegramUser user
    ) {
        return bookingService.createEcommerceOrder(request, user.id());
    }
}
