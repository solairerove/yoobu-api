package com.yoobu.api.admin.panel;

import com.yoobu.api.booking.BookingService;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.booking.dto.BookingResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/{slug}/panel")
public class AdminPanelBookingController {

    private static final String BOOKINGS_VIEW = "admin/panel/bookings";
    private static final String BOOKING_DETAIL_VIEW = "admin/panel/booking-detail";
    private static final String STATUS_FORM_ATTRIBUTE = "statusForm";
    private static final String FLASH_TYPE_SUCCESS = "success";
    private static final String FLASH_TYPE_ERROR = "error";

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
        populateBookingsModel(slug, status, deliveryDate, model);
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
            @RequestParam(defaultValue = "list") String returnTo,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            setFlashError(redirectAttributes, "Please choose a valid booking status.");
            return redirectTo(slug, bookingId, returnTo);
        }

        try {
            bookingService.updateBookingStatus(bookingId, form.getStatus());
        } catch (ResponseStatusException ex) {
            setFlashError(redirectAttributes, ex.getReason());
            return redirectTo(slug, bookingId, returnTo);
        }

        redirectAttributes.addFlashAttribute(
                "flashMessage",
                "Booking #%d status updated to %s.".formatted(bookingId, form.getStatus())
        );
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_SUCCESS);
        return redirectTo(slug, bookingId, returnTo);
    }

    private String bookingDetailView(String slug, BookingResponse booking, BookingStatusForm form, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("booking", booking);
        model.addAttribute("statusOptions", bookingService.getAllowedAdminStatuses(booking.status()));
        model.addAttribute(STATUS_FORM_ATTRIBUTE, form);
        return BOOKING_DETAIL_VIEW;
    }

    private void populateBookingsModel(String slug, BookingStatus selectedStatus, LocalDate deliveryDate, Model model) {
        var bookings = bookingService.getAdminBookings(selectedStatus, deliveryDate);
        Map<Long, java.util.List<BookingStatus>> statusOptionsByBookingId = bookings.stream()
                .collect(java.util.stream.Collectors.toMap(
                        BookingResponse::id,
                        booking -> bookingService.getAllowedAdminStatuses(booking.status()),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));

        model.addAttribute("slug", slug);
        model.addAttribute("bookings", bookings);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("deliveryDate", deliveryDate);
        model.addAttribute("statuses", BookingStatus.values());
        model.addAttribute("statusOptionsByBookingId", statusOptionsByBookingId);
    }

    private String bookingsRedirect(String slug) {
        return "redirect:/admin/" + slug + "/panel/bookings";
    }

    private String bookingDetailRedirect(String slug, Long bookingId) {
        return bookingsRedirect(slug) + "/" + bookingId;
    }

    private String redirectTo(String slug, Long bookingId, String returnTo) {
        if ("detail".equalsIgnoreCase(returnTo)) {
            return bookingDetailRedirect(slug, bookingId);
        }
        return bookingsRedirect(slug);
    }

    private void setFlashError(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute("flashMessage", message != null ? message : "Unable to update booking status.");
        redirectAttributes.addFlashAttribute("flashType", FLASH_TYPE_ERROR);
    }
}
