package com.yoobu.api.tenant;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentQrUrlValidator {

    private static final String INVALID_PAYMENT_QR_URL_MESSAGE =
            "paymentQrUrl must be a valid absolute http(s) URL";

    public String normalizePaymentQrUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedValue = value.trim();
        URI uri = parseOrThrow(normalizedValue);

        if (!StringUtils.hasText(uri.getScheme())) {
            throw invalidPaymentQrUrl();
        }

        String normalizedScheme = uri.getScheme().toLowerCase();
        if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
            throw invalidPaymentQrUrl();
        }

        if (!StringUtils.hasText(uri.getHost())) {
            throw invalidPaymentQrUrl();
        }

        return normalizedValue;
    }

    private URI parseOrThrow(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw invalidPaymentQrUrl();
        }
    }

    private ResponseStatusException invalidPaymentQrUrl() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PAYMENT_QR_URL_MESSAGE);
    }
}
