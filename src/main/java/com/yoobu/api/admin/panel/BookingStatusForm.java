package com.yoobu.api.admin.panel;

import com.yoobu.api.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class BookingStatusForm {

    @NotNull
    private BookingStatus status;

    @Size(max = 2048)
    private String trackingUrl;

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    public void setTrackingUrl(String trackingUrl) {
        this.trackingUrl = trackingUrl;
    }
}
