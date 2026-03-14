package com.yoobu.api.admin.panel;

import com.yoobu.api.booking.BookingStatus;
import jakarta.validation.constraints.NotNull;

public class BookingStatusForm {

    @NotNull
    private BookingStatus status;

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }
}
