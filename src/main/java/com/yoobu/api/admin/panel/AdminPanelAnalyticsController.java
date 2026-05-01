package com.yoobu.api.admin.panel;

import com.yoobu.api.booking.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/{slug}/panel")
public class AdminPanelAnalyticsController {

    private final BookingService bookingService;

    @GetMapping("/analytics")
    public String analytics(@PathVariable String slug, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("analytics", bookingService.getAnalytics());
        return "admin/panel/analytics";
    }
}
