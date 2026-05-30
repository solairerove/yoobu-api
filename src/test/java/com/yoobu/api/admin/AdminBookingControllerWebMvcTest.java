package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yoobu.api.booking.BookingType;
import com.yoobu.api.booking.BookingService;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.booking.dto.BookingItemResponse;
import com.yoobu.api.booking.dto.BookingResponse;
import com.yoobu.api.config.WebConfig;
import com.yoobu.api.telegram.TelegramInitDataValidator;
import com.yoobu.api.telegram.TelegramUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminBookingController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class)
)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminBookingControllerWebMvcTest.TestConfig.class)
class AdminBookingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingBookingService bookingService;

    @BeforeEach
    void resetStub() {
        bookingService.reset();
    }

    @Test
    void updateStatusBindsTrackingUrlAndDelegatesToService() throws Exception {
        bookingService.response = response(300L, BookingStatus.DELIVERING, "https://grab.example.com/track/abc-1");

        mockMvc.perform(put("/admin/demo/bookings/300/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DELIVERING",
                                  "trackingUrl": "https://grab.example.com/track/abc-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(300))
                .andExpect(jsonPath("$.status").value("DELIVERING"))
                .andExpect(jsonPath("$.trackingUrl").value("https://grab.example.com/track/abc-1"));

        assertEquals(1, bookingService.updateCalls);
        assertEquals(300L, bookingService.lastBookingId);
        assertEquals(BookingStatus.DELIVERING, bookingService.lastStatus);
        assertEquals("https://grab.example.com/track/abc-1", bookingService.lastTrackingUrl);
    }

    @Test
    void updateStatusWithoutTrackingUrlPassesNullToService() throws Exception {
        bookingService.response = response(301L, BookingStatus.CONFIRMED, null);

        mockMvc.perform(put("/admin/demo/bookings/301/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.trackingUrl").doesNotExist());

        assertEquals(1, bookingService.updateCalls);
        assertEquals(301L, bookingService.lastBookingId);
        assertEquals(BookingStatus.CONFIRMED, bookingService.lastStatus);
        assertNull(bookingService.lastTrackingUrl);
    }

    @Test
    void updateStatusRejectsTooLongTrackingUrl() throws Exception {
        String tooLongTrackingUrl = "https://grab.example.com/track/" + "a".repeat(2100);

        mockMvc.perform(put("/admin/demo/bookings/302/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DELIVERING",
                                  "trackingUrl": "%s"
                                }
                                """.formatted(tooLongTrackingUrl)))
                .andExpect(status().isBadRequest());

        assertEquals(0, bookingService.updateCalls);
    }

    private static BookingResponse response(Long id, BookingStatus status, String trackingUrl) {
        return new BookingResponse(
                id,
                BookingType.ORDER,
                status,
                trackingUrl,
                "Alex",
                "+1000000000",
                "Main st. 1",
                new BigDecimal("12.50"),
                "USD",
                LocalDate.of(2026, 3, 22),
                "note",
                List.of(new BookingItemResponse("Pizza", 1, new BigDecimal("12.50"), null, null)),
                OffsetDateTime.of(2026, 3, 20, 12, 0, 0, 0, ZoneOffset.UTC),
                null
        );
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        RecordingBookingService bookingService() {
            return new RecordingBookingService();
        }

        @Bean
        TelegramInitDataValidator telegramInitDataValidator() {
            return new TelegramInitDataValidator(new com.fasterxml.jackson.databind.ObjectMapper()) {
                @Override
                public TelegramUser validate(String rawInitData) {
                    return new TelegramUser(1L, "Admin", "User", "admin");
                }
            };
        }
    }

    static class RecordingBookingService extends BookingService {
        private int updateCalls;
        private Long lastBookingId;
        private BookingStatus lastStatus;
        private String lastTrackingUrl;
        private BookingResponse response;

        RecordingBookingService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public BookingResponse updateBookingStatus(Long bookingId, BookingStatus status, String trackingUrl) {
            updateCalls++;
            lastBookingId = bookingId;
            lastStatus = status;
            lastTrackingUrl = trackingUrl;
            return response;
        }

        void reset() {
            updateCalls = 0;
            lastBookingId = null;
            lastStatus = null;
            lastTrackingUrl = null;
            response = null;
        }
    }
}
