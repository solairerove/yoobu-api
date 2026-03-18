package com.yoobu.api.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.booking.BookingStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class SuperAdminPanelIT extends IntegrationTestSupport {

    private static final String PANEL_ROOT = "/superadmin/panel";
    private static final String PANEL_TENANTS_PATH = PANEL_ROOT + "/tenants";
    private static final String PANEL_AUDIT_PATH = PANEL_ROOT + "/audit";

    @Test
    void superAdminPanelListsAndCreatesTenants() throws Exception {
        superAdminGet(PANEL_ROOT)
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_TENANTS_PATH));

        superAdminPostForm(PANEL_TENANTS_PATH, Map.ofEntries(
                Map.entry("slug", "panel-tenant"),
                Map.entry("name", "Panel Tenant"),
                Map.entry("type", "FOOD_ORDER"),
                Map.entry("botToken", "panel-bot"),
                Map.entry("ownerTelegramId", "123456"),
                Map.entry("timezone", "Asia/Ho_Chi_Minh"),
                Map.entry("primaryColor", "#112233"),
                Map.entry("logoUrl", "https://cdn.example.com/logo.png"),
                Map.entry("welcomeMessage", "Hello from panel"),
                Map.entry("checkoutPhoneHint", "+84..."),
                Map.entry("checkoutNoteHint", "No onion, gate code, delivery code"),
                Map.entry("adminUsername", "panel-admin"),
                Map.entry("adminPassword", "panel-secret")
        ))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_TENANTS_PATH));

        JsonNode tenants = readJson(superAdminGet("/superadmin/tenants")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn());
        long tenantId = tenants.get(0).get("id").asLong();

        superAdminGet(PANEL_TENANTS_PATH)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Panel Tenant")))
                .andExpect(content().string(containsString("panel-tenant")))
                .andExpect(content().string(containsString("/admin/panel-tenant/panel")));

        superAdminGet(PANEL_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Panel Tenant")))
                .andExpect(content().string(containsString("panel-admin")))
                .andExpect(content().string(containsString("+84...")))
                .andExpect(content().string(containsString("No onion, gate code, delivery code")))
                .andExpect(content().string(containsString("Edit tenant")))
                .andExpect(content().string(containsString("/t/panel-tenant/services")));
    }

    @Test
    void superAdminPanelCanEditTenantAndRotateCredentials() throws Exception {
        long tenantId = createFoodOrderTenant("panel-edit", "Panel Before", "bot-before", "panel-admin", "panel-secret")
                .get("id").asLong();

        superAdminGet(PANEL_TENANTS_PATH + "/" + tenantId + "/edit")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit tenant")))
                .andExpect(content().string(containsString("panel-edit")));

        superAdminPostForm(PANEL_TENANTS_PATH + "/" + tenantId, Map.ofEntries(
                Map.entry("slug", "panel-edit"),
                Map.entry("name", "Panel After"),
                Map.entry("type", "FOOD_ORDER"),
                Map.entry("botToken", ""),
                Map.entry("ownerTelegramId", ""),
                Map.entry("timezone", "Asia/Ho_Chi_Minh"),
                Map.entry("primaryColor", ""),
                Map.entry("logoUrl", "https://cdn.example.com/panel-updated.png"),
                Map.entry("welcomeMessage", "Updated from panel"),
                Map.entry("checkoutPhoneHint", "+1 555..."),
                Map.entry("checkoutNoteHint", "Ring bell twice"),
                Map.entry("adminUsername", "panel-admin-2"),
                Map.entry("adminPassword", "panel-secret-2"),
                Map.entry("active", "true")
        ))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", PANEL_TENANTS_PATH + "/" + tenantId));

        superAdminGet(PANEL_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Panel After")))
                .andExpect(content().string(containsString("panel-admin-2")))
                .andExpect(content().string(containsString("Yes")))
                .andExpect(content().string(containsString("https://cdn.example.com/panel-updated.png")))
                .andExpect(content().string(containsString("+1 555...")))
                .andExpect(content().string(containsString("Ring bell twice")));

        tenantAdminGet("panel-edit", "/services", "panel-admin", "panel-secret")
                .andExpect(status().isUnauthorized());

        tenantAdminGet("panel-edit", "/services", "panel-admin-2", "panel-secret-2")
                .andExpect(status().isOk());
    }

    @Test
    void superAdminPanelRejectsDuplicateSlugOnCreateForm() throws Exception {
        createFoodOrderTenant("panel-duplicate", "Existing Tenant", "bot-existing", "existing-admin", "existing-secret");

        superAdminPostForm(PANEL_TENANTS_PATH, Map.ofEntries(
                Map.entry("slug", "panel-duplicate"),
                Map.entry("name", "New Panel Tenant"),
                Map.entry("type", "FOOD_ORDER"),
                Map.entry("botToken", "panel-bot"),
                Map.entry("ownerTelegramId", "123456"),
                Map.entry("timezone", "Asia/Ho_Chi_Minh"),
                Map.entry("primaryColor", "#112233"),
                Map.entry("logoUrl", "https://cdn.example.com/logo.png"),
                Map.entry("welcomeMessage", "Hello from panel"),
                Map.entry("checkoutPhoneHint", "+84..."),
                Map.entry("checkoutNoteHint", "No onion, gate code, delivery code"),
                Map.entry("adminUsername", "panel-admin"),
                Map.entry("adminPassword", "panel-secret")
        ))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tenant slug already exists")));
    }

    @Test
    void superAdminPanelAuditPageRendersAndSupportsTenantFilter() throws Exception {
        long tenantId = createFoodOrderTenant("panel-audit", "Panel Audit", "bot-audit", "audit-admin", "audit-secret")
                .get("id").asLong();
        long serviceId = createService("panel-audit", "audit-admin", "audit-secret", "Pizza", "12.50")
                .get("id").asLong();
        long bookingId = createBooking("panel-audit", 707L, serviceId, 1)
                .get("id").asLong();
        updateBookingStatus("panel-audit", "audit-admin", "audit-secret", bookingId, BookingStatus.CONFIRMED);

        superAdminGet(PANEL_AUDIT_PATH)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Audit log")))
                .andExpect(content().string(containsString("Status updated")))
                .andExpect(content().string(containsString("audit-admin")))
                .andExpect(content().string(containsString("status: NEW -&gt; CONFIRMED")));

        superAdminGet(PANEL_AUDIT_PATH + "?tenantId=" + tenantId + "&entity=booking&action=UPDATE_STATUS")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Booking #" + bookingId)))
                .andExpect(content().string(containsString("Status updated")));

        superAdminGet(PANEL_TENANTS_PATH + "/" + tenantId)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/superadmin/panel/audit?tenantId=" + tenantId)))
                .andExpect(content().string(containsString("Audit log")));
    }
}
