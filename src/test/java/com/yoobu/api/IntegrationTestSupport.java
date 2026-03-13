package com.yoobu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoobu.api.catalog.dto.AdminUpsertServiceRequest;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public abstract class IntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE booking_item, booking, service, tenant_config, tenant, audit_log RESTART IDENTITY CASCADE");
    }

    protected String basicAuth(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected CreateTenantRequest foodOrderTenant(
            String slug,
            String name,
            String botToken,
            String adminUsername,
            String adminPassword
    ) {
        return new CreateTenantRequest(
                slug,
                name,
                TenantType.FOOD_ORDER,
                botToken,
                123456789L,
                "Europe/Warsaw",
                "#112233",
                "https://cdn.example.com/logo.png",
                "Hello from test",
                adminUsername,
                adminPassword
        );
    }

    protected AdminUpsertServiceRequest serviceRequest(String name, String price) {
        return new AdminUpsertServiceRequest(
                name,
                name + " description",
                new BigDecimal(price),
                "pcs",
                null,
                0,
                true
        );
    }

    protected String telegramUserId(long userId) {
        return Long.toString(userId);
    }

    protected LocalDate tomorrow() {
        return LocalDate.now().plusDays(1);
    }
}
