package com.yoobu.api.admin;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoobu.api.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class TenantAdminCatalogAndBookingIT extends IntegrationTestSupport {

    private static final String TENANT_SLUG = "food-tenant";
    private static final String ADMIN_USERNAME = "food-admin";
    private static final String ADMIN_PASSWORD = "food-secret";

    @Test
    void tenantCanCreateServiceAndCustomerCanViewAndBookIt() throws Exception {
        createFoodOrderTenant(TENANT_SLUG, "Food Tenant", "food-bot-token", ADMIN_USERNAME, ADMIN_PASSWORD);

        JsonNode service = createService(TENANT_SLUG, ADMIN_USERNAME, ADMIN_PASSWORD, "Pizza", "12.50");
        long serviceId = service.get("id").asLong();
        String deliveryDate = tomorrow().toString();

        tenantPublicGet(TENANT_SLUG, "/services")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(serviceId))
                .andExpect(jsonPath("$[0].name").value("Pizza"))
                .andExpect(jsonPath("$[0].price").value(12.5));
        String bookingRequest = """
                {
                  "customerName": "Alice",
                  "customerPhone": "+48123456789",
                  "deliveryAddress": "123 Nguyen Van Linh, Hai Chau",
                  "deliveryDate": "%s",
                  "note": "Leave at the door",
                  "items": [
                    {
                      "serviceId": %d,
                      "quantity": 2
                    }
                  ]
                }
                """.formatted(deliveryDate, serviceId);

        tenantPublicPostJson(TENANT_SLUG, "/bookings", 777L, bookingRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORDER"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.customerName").value("Alice"))
                .andExpect(jsonPath("$.deliveryAddress").value("123 Nguyen Van Linh, Hai Chau"))
                .andExpect(jsonPath("$.totalPrice").value(25.0))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].serviceName").value("Pizza"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.5))
                .andExpect(jsonPath("$.currency").value("USD"));

        tenantAdminGet(TENANT_SLUG, "/bookings", ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Alice"))
                .andExpect(jsonPath("$[0].items[0].serviceName").value("Pizza"));

        tenantAdminGet(TENANT_SLUG, "/bookings?status=NEW&deliveryDate=" + deliveryDate, ADMIN_USERNAME, ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andExpect(jsonPath("$[0].deliveryDate").value(deliveryDate))
                .andExpect(jsonPath("$[0].customerName").value("Alice"));
    }

}
