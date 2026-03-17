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

    private static final String BOOKINGS_VIEW = "admin/panel/bookings";
    private static final String BOOKING_DETAIL_VIEW = "admin/panel/booking-detail";
    private static final String STATUS_FORM_ATTRIBUTE = "statusForm";

    private final BookingService bookingService;

    @GetMapping({"", "/"})
    public String panelHome(@PathVariable String slug) {
        return bookingsRedirect(slug);
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
        return BOOKINGS_VIEW;
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
            @Valid @ModelAttribute(STATUS_FORM_ATTRIBUTE) BookingStatusForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("slug", slug);
            model.addAttribute("bookings", bookingService.getAdminBookings(null, null));
            model.addAttribute("selectedStatus", null);
            model.addAttribute("deliveryDate", null);
            model.addAttribute("statuses", BookingStatus.values());
            return BOOKINGS_VIEW;
        }

        bookingService.updateBookingStatus(bookingId, form.getStatus());
        return bookingsRedirect(slug);
    }

    private String bookingDetailView(String slug, BookingResponse booking, BookingStatusForm form, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("booking", booking);
        model.addAttribute("statuses", BookingStatus.values());
        model.addAttribute(STATUS_FORM_ATTRIBUTE, form);
        return BOOKING_DETAIL_VIEW;
    }

    private String bookingsRedirect(String slug) {
        return "redirect:/admin/" + slug + "/panel/bookings";
    }

    private String bookingDetailRedirect(String slug, Long bookingId) {
        return bookingsRedirect(slug) + "/" + bookingId;
    }
}
