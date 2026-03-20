package com.yoobu.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PaymentQrUrlValidatorTest {

    private final PaymentQrUrlValidator validator = new PaymentQrUrlValidator();

    @Test
    void normalizePaymentQrUrlReturnsNullForNullOrBlankValues() {
        assertNull(validator.normalizePaymentQrUrl(null));
        assertNull(validator.normalizePaymentQrUrl(""));
        assertNull(validator.normalizePaymentQrUrl("   "));
    }

    @Test
    void normalizePaymentQrUrlAcceptsValidHttpAndHttpsUrls() {
        assertEquals(
                "https://cdn.example.com/payment-qr.png",
                validator.normalizePaymentQrUrl("https://cdn.example.com/payment-qr.png")
        );
        assertEquals(
                "http://cdn.example.com/payment-qr.png?version=2",
                validator.normalizePaymentQrUrl("http://cdn.example.com/payment-qr.png?version=2")
        );
        assertEquals(
                "HTTPS://cdn.example.com/path/to/qr",
                validator.normalizePaymentQrUrl("HTTPS://cdn.example.com/path/to/qr")
        );
    }

    @Test
    void normalizePaymentQrUrlTrimsSurroundingWhitespace() {
        assertEquals(
                "https://cdn.example.com/payment-qr.png",
                validator.normalizePaymentQrUrl("  https://cdn.example.com/payment-qr.png  ")
        );
    }

    @Test
    void normalizePaymentQrUrlRejectsRelativeUrls() {
        assertInvalid("/images/qr.png");
        assertInvalid("images/qr.png");
    }

    @Test
    void normalizePaymentQrUrlRejectsUnsupportedSchemes() {
        assertInvalid("ftp://cdn.example.com/payment-qr.png");
        assertInvalid("file:///tmp/payment-qr.png");
        assertInvalid("javascript:alert(1)");
    }

    @Test
    void normalizePaymentQrUrlRejectsUrlsWithoutHost() {
        assertInvalid("https:///payment-qr.png");
        assertInvalid("https://");
        assertInvalid("http://");
    }

    @Test
    void normalizePaymentQrUrlRejectsMalformedUrls() {
        assertInvalid("https://exa mple.com/payment-qr.png");
        assertInvalid("://cdn.example.com/payment-qr.png");
        assertInvalid("https://[invalid");
    }

    private void assertInvalid(String value) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.normalizePaymentQrUrl(value)
        );
        assertEquals(400, exception.getStatusCode().value());
        assertEquals("paymentQrUrl must be a valid absolute http(s) URL", exception.getReason());
    }
}
