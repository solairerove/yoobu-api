package com.yoobu.api.admin;

import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yoobu.api.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "app.superadmin.username=test-superadmin",
        "app.superadmin.password=test-password"
})
class TenantIsolationIT extends IntegrationTestSupport {

    private static final String TENANT_ONE = "tenant-one";
    private static final String TENANT_TWO = "tenant-two";

    @Test
    void tenantsDoNotIntercrossServicesOrAdminAccess() throws Exception {
        createFoodOrderTenant(TENANT_ONE, "Tenant One", "bot-one", "admin-one", "secret-one");
        createFoodOrderTenant(TENANT_TWO, "Tenant Two", "bot-two", "admin-two", "secret-two");

        tenantAdminPostJson(TENANT_ONE, "/services", "admin-one", "secret-one", serviceRequest("Burger", "9.99"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Burger"));

        tenantAdminGet(TENANT_TWO, "/services", "admin-one", "secret-one")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Tenant Admin: " + TENANT_TWO + "\""))
                .andExpect(status().reason("Invalid admin credentials"));

        tenantAdminPostJson(TENANT_TWO, "/services", "admin-one", "secret-one", serviceRequest("Sneaky", "1.00"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Basic realm=\"Yoobu Tenant Admin: " + TENANT_TWO + "\""))
                .andExpect(status().reason("Invalid admin credentials"));

        tenantPublicGet(TENANT_ONE, "/services")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Burger"));

        tenantPublicGet(TENANT_TWO, "/services")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
