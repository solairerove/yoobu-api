package com.yoobu.api.booking;

import com.yoobu.api.booking.dto.BookingItemResponse;
import com.yoobu.api.booking.dto.BookingResponse;
import com.yoobu.api.booking.dto.CreateBookingRequest;
import com.yoobu.api.config.WebConfig;
import com.yoobu.api.telegram.TelegramInitDataValidator;
import com.yoobu.api.telegram.TelegramUser;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BookingController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class)
)
@AutoConfigureMockMvc(addFilters = false)
@Import(BookingControllerWebMvcTest.TestConfig.class)
class BookingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingBookingService bookingService;

    @BeforeEach
    void resetStub() {
        bookingService.reset();
    }

    @Test
    void createBookingDelegatesToServiceAndReturnsResponse() throws Exception {
        bookingService.createResponse = response(101L, BookingStatus.NEW);

        String payload = """
                {
                  "customerName": "Alex",
                  "customerPhone": "+1000000000",
                  "deliveryAddress": "Main st. 1",
                  "deliveryDate": "2026-03-22",
                  "note": "note",
                  "items": [{"serviceId": 11, "quantity": 2}]
                }
                """;

        mockMvc.perform(post("/t/demo/bookings")
                        .header("X-Telegram-Init-Data", "stub-init-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.currency").value("USD"));

        assertEquals(1, bookingService.createCalls);
        assertNotNull(bookingService.lastCreateRequest);
        assertEquals("Alex", bookingService.lastCreateRequest.customerName());
    }

    @Test
    void createBookingRejectsInvalidPayload() throws Exception {
        String invalidPayload = """
                {
                  "customerPhone": "+1000000000",
                  "deliveryAddress": "Main st. 1",
                  "deliveryDate": "2026-03-22",
                  "items": []
                }
                """;

        mockMvc.perform(post("/t/demo/bookings")
                        .header("X-Telegram-Init-Data", "stub-init-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());

        assertEquals(0, bookingService.createCalls);
    }

    @Test
    void getMyBookingsReturnsServicePayload() throws Exception {
        bookingService.myBookings = List.of(response(201L, BookingStatus.CONFIRMED));

        mockMvc.perform(get("/t/demo/bookings/my")
                        .header("X-Telegram-Init-Data", "stub-init-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(201))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        assertEquals(1, bookingService.getMyBookingsCalls);
    }

    @Test
    void getBookingDelegatesIdAndUser() throws Exception {
        bookingService.singleBookingResponse = response(301L, BookingStatus.PAYMENT_PENDING);

        mockMvc.perform(get("/t/demo/bookings/301")
                        .header("X-Telegram-Init-Data", "stub-init-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));

        assertEquals(1, bookingService.getMyBookingCalls);
        assertEquals(301L, bookingService.lastBookingId);
    }

    @Test
    void cancelBookingDelegatesIdAndUser() throws Exception {
        bookingService.singleBookingResponse = response(401L, BookingStatus.CANCELLED);

        mockMvc.perform(post("/t/demo/bookings/401/cancel")
                        .header("X-Telegram-Init-Data", "stub-init-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(1, bookingService.cancelCalls);
        assertEquals(401L, bookingService.lastBookingId);
    }

    @Test
    void confirmPaymentDelegatesIdAndUser() throws Exception {
        bookingService.singleBookingResponse = response(501L, BookingStatus.PAYMENT_PENDING);

        mockMvc.perform(post("/t/demo/bookings/501/confirm-payment")
                        .header("X-Telegram-Init-Data", "stub-init-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"));

        assertEquals(1, bookingService.confirmPaymentCalls);
        assertEquals(501L, bookingService.lastBookingId);
    }

    private static BookingResponse response(Long id, BookingStatus status) {
        return new BookingResponse(
                id,
                BookingType.ORDER,
                status,
                null,
                "Alex",
                "+1000000000",
                "Main st. 1",
                new java.math.BigDecimal("12.50"),
                "USD",
                java.time.LocalDate.of(2026, 3, 22),
                "note",
                List.of(new BookingItemResponse("Pizza", 1, new java.math.BigDecimal("12.50"), "USD")),
                OffsetDateTime.of(2026, 3, 20, 12, 0, 0, 0, ZoneOffset.UTC)
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
                    return new TelegramUser(4242L, "Test", "User", "testuser");
                }
            };
        }
    }

    static class RecordingBookingService extends BookingService {
        private int createCalls;
        private int getMyBookingsCalls;
        private int getMyBookingCalls;
        private int cancelCalls;
        private int confirmPaymentCalls;
        private Long lastUserId;
        private Long lastBookingId;
        private CreateBookingRequest lastCreateRequest;
        private BookingResponse createResponse;
        private BookingResponse singleBookingResponse;
        private List<BookingResponse> myBookings = List.of();

        RecordingBookingService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public BookingResponse createFoodOrder(CreateBookingRequest request, Long telegramUserId) {
            createCalls++;
            lastCreateRequest = request;
            lastUserId = telegramUserId;
            return createResponse;
        }

        @Override
        public List<BookingResponse> getMyBookings(Long telegramUserId) {
            getMyBookingsCalls++;
            lastUserId = telegramUserId;
            return myBookings;
        }

        @Override
        public BookingResponse getMyBooking(Long bookingId, Long telegramUserId) {
            getMyBookingCalls++;
            lastBookingId = bookingId;
            lastUserId = telegramUserId;
            return singleBookingResponse;
        }

        @Override
        public BookingResponse cancelMyBooking(Long bookingId, Long telegramUserId) {
            cancelCalls++;
            lastBookingId = bookingId;
            lastUserId = telegramUserId;
            return singleBookingResponse;
        }

        @Override
        public BookingResponse confirmMyBookingPayment(Long bookingId, Long telegramUserId) {
            confirmPaymentCalls++;
            lastBookingId = bookingId;
            lastUserId = telegramUserId;
            return singleBookingResponse;
        }

        void reset() {
            createCalls = 0;
            getMyBookingsCalls = 0;
            getMyBookingCalls = 0;
            cancelCalls = 0;
            confirmPaymentCalls = 0;
            lastUserId = null;
            lastBookingId = null;
            lastCreateRequest = null;
            createResponse = null;
            singleBookingResponse = null;
            myBookings = List.of();
        }
    }
}
