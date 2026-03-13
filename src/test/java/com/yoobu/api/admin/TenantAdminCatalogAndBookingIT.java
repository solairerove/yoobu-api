package com.yoobu.api.admin;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void tenantCanCreateServiceAndCustomerCanViewAndBookIt() throws Exception {
        createFoodOrderTenant("food-tenant", "Food Tenant", "food-bot-token", "food-admin", "food-secret");

        JsonNode service = createService("food-tenant", "food-admin", "food-secret", "Pizza", "12.50");
        long serviceId = service.get("id").asLong();

        mockMvc.perform(get("/t/food-tenant/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(serviceId))
                .andExpect(jsonPath("$[0].name").value("Pizza"))
                .andExpect(jsonPath("$[0].price").value(12.5));
        String bookingRequest = """
                {
                  "customerName": "Alice",
                  "customerPhone": "+48123456789",
                  "deliveryDate": "%s",
                  "note": "Leave at the door",
                  "items": [
                    {
                      "serviceId": %d,
                      "quantity": 2
                    }
                  ]
                }
                """.formatted(tomorrow(), serviceId);

        mockMvc.perform(post("/t/food-tenant/bookings")
                        .header("X-Telegram-User-Id", telegramUserId(777))
                        .contentType(APPLICATION_JSON)
                        .content(bookingRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORDER"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.customerName").value("Alice"))
                .andExpect(jsonPath("$.totalPrice").value(25.0))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].serviceName").value("Pizza"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.5));

        mockMvc.perform(get("/admin/food-tenant/bookings")
                        .header(AUTHORIZATION, basicAuth("food-admin", "food-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Alice"))
                .andExpect(jsonPath("$[0].items[0].serviceName").value("Pizza"));
    }

}
