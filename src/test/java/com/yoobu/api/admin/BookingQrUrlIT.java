package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class BookingQrUrlIT extends IntegrationTestSupport {

    private static final String ADMIN_USERNAME = "qr-admin";
    private static final String ADMIN_PASSWORD = "qr-secret";

    @Test
    void bookingPaymentQrUrl_staticFallback_usesStaticUrl() throws Exception {
        String staticQrUrl = "https://cdn.example.com/payment-qr.png";
        createTenantWithPayment("qr-static", null, null, staticQrUrl);
        long serviceId = createService("qr-static", ADMIN_USERNAME, ADMIN_PASSWORD, "Pho", "50.00")
                .get("id").asLong();

        JsonNode booking = createBooking("qr-static", 101L, serviceId, 1);

        String paymentQrUrl = booking.get("paymentQrUrl").asText();
        assertEquals(staticQrUrl, paymentQrUrl);
    }

    @Test
    void bookingPaymentQrUrl_dynamicVietQr_buildsCorrectUrl() throws Exception {
        createTenantWithPayment("qr-dynamic", "970436", "1059202107", null);
        long serviceId = createService("qr-dynamic", ADMIN_USERNAME, ADMIN_PASSWORD, "Bun Bo", "80.00")
                .get("id").asLong();

        JsonNode booking = createBooking("qr-dynamic", 202L, serviceId, 1);

        String paymentQrUrl = booking.get("paymentQrUrl").asText();
        long bookingId = booking.get("id").asLong();

        assertNotNull(paymentQrUrl);
        assertTrue(paymentQrUrl.startsWith("https://img.vietqr.io/image/970436-1059202107-compact2.png"),
                "Expected VietQR base URL, got: " + paymentQrUrl);
        assertTrue(paymentQrUrl.contains("amount=80"),
                "Expected amount=80 in URL, got: " + paymentQrUrl);
        assertTrue(paymentQrUrl.contains("addInfo=NUM-" + bookingId),
                "Expected addInfo=NUM-" + bookingId + " in URL, got: " + paymentQrUrl);
    }

    private void createTenantWithPayment(
            String slug,
            String bankBin,
            String accountNumber,
            String paymentQrUrl
    ) throws Exception {
        CreateTenantRequest request = new CreateTenantRequest(
                slug,
                slug + " Tenant",
                TenantType.FOOD_ORDER,
                "bot-token-" + slug,
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                null,
                "#112233",
                null,
                "Welcome",
                "Your name",
                "+84...",
                "Note",
                "Address",
                paymentQrUrl,
                bankBin,
                accountNumber,
                ADMIN_USERNAME,
                ADMIN_PASSWORD,
                null,
                null
        );

        mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/superadmin/tenants")
                        .header("Authorization", superAdminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isOk());
    }
}
