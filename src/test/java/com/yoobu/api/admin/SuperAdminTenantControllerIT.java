package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import com.yoobu.api.tenant.dto.UpdateTenantRequest;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class SuperAdminTenantControllerIT extends IntegrationTestSupport {

    private static final String SUPERADMIN_TENANTS_PATH = "/superadmin/tenants";

    @Test
    void superAdminCanAuthenticateAgainstProtectedEndpoint() throws Exception {
        superAdminGet(SUPERADMIN_TENANTS_PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void superAdminCannotAuthenticateWithInvalidCredentials() throws Exception {
        getWithTenantAdminAuth(SUPERADMIN_TENANTS_PATH, SUPERADMIN_USERNAME, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Super Admin\""))
                .andExpect(status().reason("Invalid superadmin credentials"));
    }

    @Test
    void superAdminCannotAccessProtectedEndpointWithoutCredentials() throws Exception {
        mockMvc.perform(get(SUPERADMIN_TENANTS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Super Admin\""))
                .andExpect(status().reason("Missing Basic authorization header"));
    }

    @Test
    void superAdminCanCreateTenantAndReadItBack() throws Exception {
        var request = foodOrderTenant("tenant-it", "Tenant Integration Test", "bot-token", "admin", "secret");

        superAdminPostJson(SUPERADMIN_TENANTS_PATH, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.slug").value("tenant-it"))
                .andExpect(jsonPath("$.name").value("Tenant Integration Test"))
                .andExpect(jsonPath("$.type").value("FOOD_ORDER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$.createdAt").isString());

        superAdminGet(SUPERADMIN_TENANTS_PATH)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("tenant-it"))
                .andExpect(jsonPath("$[0].name").value("Tenant Integration Test"))
                .andExpect(jsonPath("$[0].type").value("FOOD_ORDER"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$[0].createdAt").isString());

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.currency").value("USD"))
                .andExpect(jsonPath("$.config.checkout_name_hint").value("Your full name"));

        var auditLog = latestAuditLog("tenant", "CREATE");
        assertEquals(1, auditLogCount());
        assertEquals("tenant", auditLog.get("entity").asText());
        assertEquals("CREATE", auditLog.get("action").asText());
        assertEquals(1L, auditLog.get("tenant_id").asLong());
        assertEquals(1L, auditLog.get("entity_id").asLong());
        assertEquals(SUPERADMIN_USERNAME, auditLog.get("actor_id").asText());
        assertEquals(true, newAuditValue(auditLog).get("botTokenConfigured").asBoolean());
        assertEquals("admin", newAuditValue(auditLog).get("adminUsername").asText());
    }

    @Test
    void superAdminCanCheckTenantSlugAvailabilityBeforeCreation() throws Exception {
        createFoodOrderTenant("availability-tenant", "Availability Tenant", "bot", "admin", "secret");

        superAdminGet("/superadmin/tenants/slug-availability?slug=available-tenant")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("available-tenant"))
                .andExpect(jsonPath("$.available").value(true));

        superAdminGet("/superadmin/tenants/slug-availability?slug=availability-tenant")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("availability-tenant"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void superAdminCannotCreateTwoTenantsWithSameSlug() throws Exception {
        var request = foodOrderTenant("duplicate-tenant", "Duplicate Tenant", "bot-token", "admin", "secret");

        superAdminPostJson(SUPERADMIN_TENANTS_PATH, request)
                .andExpect(status().isOk());

        superAdminPostJson(SUPERADMIN_TENANTS_PATH, request)
                .andExpect(status().isConflict())
                .andExpect(status().reason("Tenant slug already exists"));
    }

    @Test
    void superAdminRejectsInvalidPaymentQrUrlOnCreate() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest(
                "invalid-payment-qr-create",
                "Invalid Payment QR Create",
                TenantType.FOOD_ORDER,
                "bot-token",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                "#112233",
                "https://cdn.example.com/logo.png",
                "Hello from test",
                "Your full name",
                "+84...",
                "No onion, gate code, delivery code",
                "not-a-url",
                "admin",
                "secret",
                null,
                null
        );

        superAdminPostJson(SUPERADMIN_TENANTS_PATH, request)
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("paymentQrUrl must be a valid absolute http(s) URL"));
    }

    @Test
    void superAdminRejectsInvalidPaymentQrUrlOnUpdate() throws Exception {
        long tenantId = createFoodOrderTenant("invalid-payment-qr-update", "Tenant Before", "bot-before", "admin-before", "secret-before")
                .get("id").asLong();

        UpdateTenantRequest request = new UpdateTenantRequest(
                "Tenant After",
                TenantType.FOOD_ORDER,
                "bot-after",
                999999L,
                "Asia/Ho_Chi_Minh",
                "THB",
                "#445566",
                "https://cdn.example.com/updated-logo.png",
                "Updated welcome",
                "Contact person",
                "+1 555...",
                "Ring bell twice",
                "bad-url",
                "admin-after",
                "secret-after",
                true,
                null,
                null
        );

        superAdminPutJson(SUPERADMIN_TENANTS_PATH + "/" + tenantId, request)
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("paymentQrUrl must be a valid absolute http(s) URL"));
    }

    @Test
    void superAdminCanGetAndUpdateTenantWithoutChangingSlug() throws Exception {
        long tenantId = createFoodOrderTenant("tenant-edit", "Tenant Before", "bot-before", "admin-before", "secret-before")
                .get("id").asLong();

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-edit"))
                .andExpect(jsonPath("$.name").value("Tenant Before"))
                .andExpect(jsonPath("$.config.admin_username").value("admin-before"));

        UpdateTenantRequest request = new UpdateTenantRequest(
                "Tenant After",
                TenantType.APPOINTMENT,
                "bot-after",
                999999L,
                "Asia/Ho_Chi_Minh",
                "THB",
                "#445566",
                "https://cdn.example.com/updated-logo.png",
                "Updated welcome",
                "Contact person",
                "+1 555...",
                "Ring bell twice",
                "https://cdn.example.com/payment-qr-updated.png",
                "admin-after",
                "secret-after",
                true,
                null,
                null
        );

        superAdminPutJson(SUPERADMIN_TENANTS_PATH + "/" + tenantId, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantId))
                .andExpect(jsonPath("$.slug").value("tenant-edit"))
                .andExpect(jsonPath("$.name").value("Tenant After"))
                .andExpect(jsonPath("$.type").value("APPOINTMENT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-edit"))
                .andExpect(jsonPath("$.name").value("Tenant After"))
                .andExpect(jsonPath("$.type").value("APPOINTMENT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.botToken").value("bot-after"))
                .andExpect(jsonPath("$.ownerTelegramId").value(999999L))
                .andExpect(jsonPath("$.config.admin_username").value("admin-after"))
                .andExpect(jsonPath("$.config.currency").value("THB"))
                .andExpect(jsonPath("$.config.primary_color").value("#445566"))
                .andExpect(jsonPath("$.config.logo_url").value("https://cdn.example.com/updated-logo.png"))
                .andExpect(jsonPath("$.config.welcome_message").value("Updated welcome"))
                .andExpect(jsonPath("$.config.checkout_name_hint").value("Contact person"))
                .andExpect(jsonPath("$.config.checkout_phone_hint").value("+1 555..."))
                .andExpect(jsonPath("$.config.checkout_note_hint").value("Ring bell twice"))
                .andExpect(jsonPath("$.config.payment_qr_url").value("https://cdn.example.com/payment-qr-updated.png"));

        tenantAdminGet("tenant-edit", "/services", "admin-before", "secret-before")
                .andExpect(status().isUnauthorized());

        tenantAdminGet("tenant-edit", "/services", "admin-after", "secret-after")
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Tenant does not support food ordering"));

        var auditLog = latestAuditLog("tenant", "UPDATE");
        assertEquals("UPDATE", auditLog.get("action").asText());
        assertEquals(tenantId, auditLog.get("entity_id").asLong());
        assertEquals(SUPERADMIN_USERNAME, auditLog.get("actor_id").asText());
        assertEquals("Tenant Before", oldAuditValue(auditLog).get("name").asText());
        assertEquals("Tenant After", newAuditValue(auditLog).get("name").asText());
        assertEquals("APPOINTMENT", newAuditValue(auditLog).get("type").asText());
    }

    @Test
    void superAdminCanKeepExistingPasswordWhileUpdatingOtherTenantFields() throws Exception {
        long tenantId = createFoodOrderTenant("tenant-keep-pass", "Keep Password", "bot-token", "admin-before", "secret-before")
                .get("id").asLong();

        UpdateTenantRequest request = new UpdateTenantRequest(
                "Keep Password Updated",
                TenantType.FOOD_ORDER,
                "bot-token-updated",
                444444L,
                "Asia/Ho_Chi_Minh",
                "USD",
                "#778899",
                "https://cdn.example.com/keep-pass.png",
                "Password unchanged",
                "Receiver name",
                "+66...",
                "Leave at lobby",
                "https://cdn.example.com/payment-qr-updated.png",
                "admin-after",
                "",
                true,
                null,
                null
        );

        superAdminPutJson(SUPERADMIN_TENANTS_PATH + "/" + tenantId, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-keep-pass"))
                .andExpect(jsonPath("$.name").value("Keep Password Updated"));

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-keep-pass"))
                .andExpect(jsonPath("$.config.admin_username").value("admin-after"))
                .andExpect(jsonPath("$.config.currency").value("USD"))
                .andExpect(jsonPath("$.botToken").value("bot-token-updated"))
                .andExpect(jsonPath("$.config.primary_color").value("#778899"))
                .andExpect(jsonPath("$.config.checkout_name_hint").value("Receiver name"))
                .andExpect(jsonPath("$.config.checkout_phone_hint").value("+66..."))
                .andExpect(jsonPath("$.config.checkout_note_hint").value("Leave at lobby"))
                .andExpect(jsonPath("$.config.payment_qr_url").value("https://cdn.example.com/payment-qr-updated.png"));

        tenantAdminGet("tenant-keep-pass", "/services", "admin-before", "secret-before")
                .andExpect(status().isUnauthorized());

        tenantAdminGet("tenant-keep-pass", "/services", "admin-after", "secret-before")
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCanClearOptionalTenantFieldsAndDeactivateTenant() throws Exception {
        long tenantId = createFoodOrderTenant("tenant-clear", "Tenant Clear", "bot-clear", "admin-clear", "secret-clear")
                .get("id").asLong();

        UpdateTenantRequest request = new UpdateTenantRequest(
                "Tenant Cleared",
                TenantType.FOOD_ORDER,
                "",
                null,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "admin-clear",
                "",
                false,
                null,
                null
        );

        superAdminPutJson(SUPERADMIN_TENANTS_PATH + "/" + tenantId, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-clear"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"));

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-clear"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.botToken").value("bot-clear"))
                .andExpect(jsonPath("$.ownerTelegramId").doesNotExist())
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.config.admin_username").value("admin-clear"))
                .andExpect(jsonPath("$.config.currency").value("USD"))
                .andExpect(jsonPath("$.config.primary_color").doesNotExist())
                .andExpect(jsonPath("$.config.logo_url").doesNotExist())
                .andExpect(jsonPath("$.config.welcome_message").doesNotExist())
                .andExpect(jsonPath("$.config.checkout_name_hint").doesNotExist())
                .andExpect(jsonPath("$.config.checkout_phone_hint").doesNotExist())
                .andExpect(jsonPath("$.config.checkout_note_hint").doesNotExist())
                .andExpect(jsonPath("$.config.payment_qr_url").doesNotExist());

        ResponseStatusException publicAccessFailure = assertTenantNotFound(() ->
                tenantPublicGet("tenant-clear", "/services"));
        assertEquals("Tenant not found", publicAccessFailure.getReason());

        ResponseStatusException adminAccessFailure = assertTenantNotFound(() ->
                tenantAdminGet("tenant-clear", "/services", "admin-clear", "secret-clear"));
        assertEquals("Tenant not found", adminAccessFailure.getReason());
    }

    @Test
    void superAdminCanCreateTenantWithCutoffAndReadItBack() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest(
                "tenant-with-cutoff",
                "Cutoff Tenant",
                TenantType.FOOD_ORDER,
                "bot-token",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                null, null, null, null, null, null,
                null,
                "admin",
                "secret",
                18,
                30
        );

        long tenantId = readJson(superAdminPostJson(SUPERADMIN_TENANTS_PATH, request)
                .andExpect(status().isOk())
                .andReturn()).get("id").asLong();

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.cutoff_hour").value("18"))
                .andExpect(jsonPath("$.config.cutoff_minute").value("30"));

        var auditLog = latestAuditLog("tenant", "CREATE");
        assertEquals("18", newAuditValue(auditLog).get("cutoffHour").asText());
        assertEquals("30", newAuditValue(auditLog).get("cutoffMinute").asText());
    }

    @Test
    void superAdminCanUpdateTenantToSetCutoff() throws Exception {
        long tenantId = createFoodOrderTenant("tenant-set-cutoff", "Set Cutoff", "bot", "admin", "secret")
                .get("id").asLong();

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.cutoff_hour").doesNotExist())
                .andExpect(jsonPath("$.config.cutoff_minute").doesNotExist());

        UpdateTenantRequest request = new UpdateTenantRequest(
                "Set Cutoff",
                TenantType.FOOD_ORDER,
                "bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                null, null, null, null, null, null,
                null,
                "admin",
                "",
                true,
                12,
                0
        );

        superAdminPutJson(SUPERADMIN_TENANTS_PATH + "/" + tenantId, request)
                .andExpect(status().isOk());

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.cutoff_hour").value("12"))
                .andExpect(jsonPath("$.config.cutoff_minute").value("0"));

        var auditLog = latestAuditLog("tenant", "UPDATE");
        assertTrue(newAuditValue(auditLog).get("cutoffHour").isTextual());
        assertEquals("12", newAuditValue(auditLog).get("cutoffHour").asText());
        assertEquals("0", newAuditValue(auditLog).get("cutoffMinute").asText());
    }

    @Test
    void superAdminCanUpdateTenantToClearCutoff() throws Exception {
        CreateTenantRequest createRequest = new CreateTenantRequest(
                "tenant-clear-cutoff",
                "Clear Cutoff",
                TenantType.FOOD_ORDER,
                "bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                null, null, null, null, null, null,
                null,
                "admin",
                "secret",
                18,
                0
        );

        long tenantId = readJson(superAdminPostJson(SUPERADMIN_TENANTS_PATH, createRequest)
                .andExpect(status().isOk())
                .andReturn()).get("id").asLong();

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/" + tenantId)
                .andExpect(jsonPath("$.config.cutoff_hour").value("18"));

        UpdateTenantRequest clearRequest = new UpdateTenantRequest(
                "Clear Cutoff",
                TenantType.FOOD_ORDER,
                "bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                null, null, null, null, null, null,
                null,
                "admin",
                "",
                true,
                null,
                null
        );

        superAdminPutJson(SUPERADMIN_TENANTS_PATH + "/" + tenantId, clearRequest)
                .andExpect(status().isOk());

        superAdminGet(SUPERADMIN_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.cutoff_hour").doesNotExist())
                .andExpect(jsonPath("$.config.cutoff_minute").doesNotExist());
    }

    @Test
    void superAdminRejectsPartialCutoffHourOnlyOnCreate() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest(
                "tenant-partial-hour",
                "Partial Hour",
                TenantType.FOOD_ORDER,
                "bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                null, null, null, null, null, null,
                null,
                "admin",
                "secret",
                18,
                null
        );

        superAdminPostJson(SUPERADMIN_TENANTS_PATH, request)
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Both cutoffHour and cutoffMinute must be set, or both must be empty"));
    }

    @Test
    void superAdminRejectsPartialCutoffMinuteOnlyOnUpdate() throws Exception {
        long tenantId = createFoodOrderTenant("tenant-partial-minute", "Partial Minute", "bot", "admin", "secret")
                .get("id").asLong();

        UpdateTenantRequest request = new UpdateTenantRequest(
                "Partial Minute",
                TenantType.FOOD_ORDER,
                "bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                null, null, null, null, null, null,
                null,
                "admin",
                "",
                true,
                null,
                45
        );

        superAdminPutJson(SUPERADMIN_TENANTS_PATH + "/" + tenantId, request)
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Both cutoffHour and cutoffMinute must be set, or both must be empty"));
    }

    @Test
    void superAdminRejectsOutOfRangeCutoffHour() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest(
                "tenant-bad-hour",
                "Bad Hour",
                TenantType.FOOD_ORDER,
                "bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                null, null, null, null, null, null,
                null,
                "admin",
                "secret",
                24,
                0
        );

        superAdminPostJson(SUPERADMIN_TENANTS_PATH, request)
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("cutoffHour must be 0-23"));
    }

    @Test
    void superAdminRejectsOutOfRangeCutoffMinute() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest(
                "tenant-bad-minute",
                "Bad Minute",
                TenantType.FOOD_ORDER,
                "bot",
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
                "USD",
                null, null, null, null, null, null,
                null,
                "admin",
                "secret",
                12,
                60
        );

        superAdminPostJson(SUPERADMIN_TENANTS_PATH, request)
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("cutoffMinute must be 0-59"));
    }

    private ResponseStatusException assertTenantNotFound(Executable executable) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, executable);
        assertEquals(404, exception.getStatusCode().value());
        return exception;
    }
}
