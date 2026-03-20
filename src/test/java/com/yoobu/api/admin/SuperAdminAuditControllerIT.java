package com.yoobu.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import com.yoobu.api.booking.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class SuperAdminAuditControllerIT extends IntegrationTestSupport {

    private static final String AUDIT_PATH = "/superadmin/audit";

    @Test
    void superAdminCanReadAuditLogWithFiltersAndPagination() throws Exception {
        long tenantOneId = createFoodOrderTenant("audit-one", "Audit One", "bot-1", "admin-1", "secret-1")
                .get("id").asLong();
        long tenantTwoId = createFoodOrderTenant("audit-two", "Audit Two", "bot-2", "admin-2", "secret-2")
                .get("id").asLong();

        long serviceId = createService("audit-one", "admin-1", "secret-1", "Pizza", "12.50").get("id").asLong();
        long bookingId = createBooking("audit-one", 1001L, serviceId, 1).get("id").asLong();
        confirmBookingPayment("audit-one", bookingId, 1001L);
        updateBookingStatus("audit-one", "admin-1", "secret-1", bookingId, BookingStatus.CONFIRMED);

        superAdminGet(AUDIT_PATH + "?tenantId=" + tenantOneId + "&entity=booking&action=UPDATE_STATUS&page=0&size=5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].tenantId").value(tenantOneId))
                .andExpect(jsonPath("$.items[0].entity").value("booking"))
                .andExpect(jsonPath("$.items[0].action").value("UPDATE_STATUS"))
                .andExpect(jsonPath("$.items[0].actorId").value("admin-1"))
                .andExpect(jsonPath("$.items[0].entityId").value(bookingId));

        JsonNode updateStatusAudit = readJson(superAdminGet(
                AUDIT_PATH + "?tenantId=" + tenantOneId + "&entity=booking&action=UPDATE_STATUS&page=0&size=5"
        ).andExpect(status().isOk()).andReturn()).get("items").get(0);
        assertAuditStatus(updateStatusAudit.get("oldValue"), "PAYMENT_PENDING");
        assertAuditStatus(updateStatusAudit.get("newValue"), "CONFIRMED");

        superAdminGet(AUDIT_PATH + "?tenantId=" + tenantTwoId + "&page=0&size=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void superAdminAuditEndpointRequiresSuperAdminCredentials() throws Exception {
        mockMvc.perform(get(AUDIT_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"Yoobu Super Admin\""));
    }

    @Test
    void superAdminAuditEndpointCapsPageSizeToFifty() throws Exception {
        createFoodOrderTenant("audit-cap", "Audit Cap", "bot-cap", "cap-admin", "cap-secret");

        superAdminGet(AUDIT_PATH + "?page=0&size=500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void superAdminCanExportAuditLogAsCsv() throws Exception {
        long tenantId = createFoodOrderTenant("audit-export", "Audit Export", "bot-export", "export-admin", "secret-1")
                .get("id").asLong();
        long serviceId = createService("audit-export", "export-admin", "secret-1", "Pizza", "12.50").get("id").asLong();
        long bookingId = createBooking("audit-export", 1002L, serviceId, 1).get("id").asLong();
        confirmBookingPayment("audit-export", bookingId, 1002L);
        updateBookingStatus("audit-export", "export-admin", "secret-1", bookingId, BookingStatus.CONFIRMED);

        superAdminGet(AUDIT_PATH + "/export?tenantId=" + tenantId + "&entity=booking&action=UPDATE_STATUS")
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment; filename=\"audit-log-")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("createdAt,tenantId,entity,entityId,action,actorId,changesSummary")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"UPDATE_STATUS\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("status: PAYMENT_PENDING -> CONFIRMED")));
    }

    private void assertAuditStatus(JsonNode payload, String expectedStatus) throws Exception {
        if (payload == null || payload.isNull()) {
            throw new AssertionError("Audit payload is null");
        }

        JsonNode normalized = payload;
        if (payload.isTextual()) {
            normalized = objectMapper.readTree(payload.asText());
        }

        if (!normalized.has("status")) {
            throw new AssertionError("Audit payload does not include status: " + normalized);
        }

        org.junit.jupiter.api.Assertions.assertEquals(expectedStatus, normalized.get("status").asText());
    }
}
