package com.yoobu.api.admin.panel;

import com.yoobu.api.booking.BookingService;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.booking.dto.BookingResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/{slug}/panel")
public class AdminPanelBookingController {

    private final BookingService bookingService;

    @GetMapping({"", "/"})
    public String panelHome(@PathVariable String slug) {
        return "redirect:/admin/" + slug + "/panel/bookings";
    }

    @GetMapping("/bookings")
    public String bookings(
            @PathVariable String slug,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            Model model
    ) {
        model.addAttribute("slug", slug);
        model.addAttribute("bookings", bookingService.getAdminBookings(status, deliveryDate));
        model.addAttribute("selectedStatus", status);
        model.addAttribute("deliveryDate", deliveryDate);
        model.addAttribute("statuses", BookingStatus.values());
        return "admin/panel/bookings";
    }

    @GetMapping("/bookings/{bookingId}")
    public String bookingDetail(@PathVariable String slug, @PathVariable Long bookingId, Model model) {
        BookingResponse booking = bookingService.getAdminBooking(bookingId);

        BookingStatusForm form = new BookingStatusForm();
        form.setStatus(booking.status());

        return bookingDetailView(slug, booking, form, model);
    }

    @PostMapping("/bookings/{bookingId}/status")
    public String updateStatus(
            @PathVariable String slug,
            @PathVariable Long bookingId,
            @Valid @ModelAttribute("statusForm") BookingStatusForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return bookingDetailView(slug, bookingService.getAdminBooking(bookingId), form, model);
        }

        bookingService.updateBookingStatus(bookingId, form.getStatus());
        return "redirect:/admin/" + slug + "/panel/bookings/" + bookingId;
    }

    private String bookingDetailView(String slug, BookingResponse booking, BookingStatusForm form, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("booking", booking);
        model.addAttribute("statuses", BookingStatus.values());
        model.addAttribute("statusForm", form);
        return "admin/panel/booking-detail";
    }
}
