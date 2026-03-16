package com.yoobu.api;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoobu.api.booking.BookingStatus;
import com.yoobu.api.catalog.ServiceStatus;
import com.yoobu.api.catalog.dto.AdminUpsertServiceRequest;
import com.yoobu.api.tenant.TenantType;
import com.yoobu.api.tenant.dto.CreateTenantRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import org.springframework.test.web.servlet.ResultActions;
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

    protected static final String DEFAULT_TENANT_TIMEZONE = "Europe/Warsaw";
    protected static final String SUPERADMIN_USERNAME = "test-superadmin";
    protected static final String SUPERADMIN_PASSWORD = "test-password";
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

    protected String superAdminAuth() {
        return basicAuth(SUPERADMIN_USERNAME, SUPERADMIN_PASSWORD);
    }

    protected String tenantUserId(long userId) {
        return telegramUserId(userId);
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
        return tenant(slug, name, TenantType.FOOD_ORDER, botToken, adminUsername, adminPassword);
    }

    protected CreateTenantRequest tenant(
            String slug,
            String name,
            TenantType type,
            String botToken,
            String adminUsername,
            String adminPassword
    ) {
        return new CreateTenantRequest(
                slug,
                name,
                type,
                botToken,
                123456789L,
                DEFAULT_TENANT_TIMEZONE,
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
                ServiceStatus.ACTIVE
        );
    }

    protected String telegramUserId(long userId) {
        return Long.toString(userId);
    }

    protected LocalDate tomorrow() {
        return tomorrow(DEFAULT_TENANT_TIMEZONE);
    }

    protected LocalDate tomorrow(String timezone) {
        return LocalDate.now(ZoneId.of(timezone)).plusDays(1);
    }

    protected LocalDate yesterday(String timezone) {
        return LocalDate.now(ZoneId.of(timezone)).minusDays(1);
    }

    protected JsonNode createFoodOrderTenant(
            String slug,
            String name,
            String botToken,
            String adminUsername,
            String adminPassword
    ) throws Exception {
        return createTenant(slug, name, TenantType.FOOD_ORDER, botToken, adminUsername, adminPassword);
    }

    protected JsonNode createTenant(
            String slug,
            String name,
            TenantType type,
            String botToken,
            String adminUsername,
            String adminPassword
    ) throws Exception {
        return readJson(mockMvc.perform(post("/superadmin/tenants")
                        .header(AUTHORIZATION, superAdminAuth())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                tenant(slug, name, type, botToken, adminUsername, adminPassword))))
                .andExpect(status().isOk())
                .andReturn());
    }

    protected JsonNode createService(
            String slug,
            String adminUsername,
            String adminPassword,
            String name,
            String price
    ) throws Exception {
        return readJson(mockMvc.perform(post("/admin/" + slug + "/services")
                        .header(AUTHORIZATION, basicAuth(adminUsername, adminPassword))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceRequest(name, price))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected JsonNode createBooking(String slug, long telegramUserId, long serviceId, int quantity) throws Exception {
        return readJson(mockMvc.perform(post("/t/" + slug + "/bookings")
                        .header("X-Telegram-User-Id", tenantUserId(telegramUserId))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(serviceId, quantity)))
                .andExpect(status().isOk())
                .andReturn());
    }

    protected JsonNode createBooking(String slug, long telegramUserId, long serviceId, int quantity, LocalDate deliveryDate)
            throws Exception {
        return readJson(mockMvc.perform(post("/t/" + slug + "/bookings")
                        .header("X-Telegram-User-Id", tenantUserId(telegramUserId))
                        .contentType(APPLICATION_JSON)
                        .content(bookingPayload(serviceId, quantity, deliveryDate)))
                .andExpect(status().isOk())
                .andReturn());
    }

    protected void updateBookingStatus(
            String slug,
            String adminUsername,
            String adminPassword,
            long bookingId,
            BookingStatus statusValue
    ) throws Exception {
        mockMvc.perform(put("/admin/" + slug + "/bookings/" + bookingId + "/status")
                        .header(AUTHORIZATION, basicAuth(adminUsername, adminPassword))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "%s"
                                }
                                """.formatted(statusValue.name())))
                .andExpect(status().isOk());
    }

    protected void updateBookingStatus(
            String slug,
            String adminUsername,
            String adminPassword,
            long bookingId,
            String statusValue
    ) throws Exception {
        mockMvc.perform(put("/admin/" + slug + "/bookings/" + bookingId + "/status")
                        .header(AUTHORIZATION, basicAuth(adminUsername, adminPassword))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "%s"
                                }
                                """.formatted(statusValue)))
                .andExpect(status().isOk());
    }

    protected String bookingPayload(long serviceId, int quantity) {
        return bookingPayload(serviceId, quantity, tomorrow());
    }

    protected String bookingPayload(long serviceId, int quantity, LocalDate deliveryDate) {
        return """
                {
                  "customerName": "Alice",
                  "customerPhone": "+48123456789",
                  "deliveryDate": "%s",
                  "note": "Leave at the door",
                  "items": [
                    {
                      "serviceId": %d,
                      "quantity": %d
                    }
                  ]
                }
                """.formatted(deliveryDate, serviceId, quantity);
    }

    protected int auditLogCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
        return count != null ? count : 0;
    }

    protected JsonNode latestAuditLog() throws Exception {
        return latestAuditLog(null, null);
    }

    protected JsonNode latestAuditLog(String entity, String action) throws Exception {
        StringBuilder sql = new StringBuilder("""
                SELECT row_to_json(entry)
                FROM (
                    SELECT id, tenant_id, entity, entity_id, action, actor_id, old_value, new_value, created_at
                    FROM audit_log
                """);

        if (entity != null || action != null) {
            sql.append(" WHERE ");
            if (entity != null) {
                sql.append("entity = '").append(entity).append("'");
            }
            if (action != null) {
                if (entity != null) {
                    sql.append(" AND ");
                }
                sql.append("action = '").append(action).append("'");
            }
        }

        sql.append(" ORDER BY id DESC LIMIT 1) entry");
        String json = jdbcTemplate.queryForObject(sql.toString(), String.class);
        return objectMapper.readTree(json);
    }

    protected JsonNode oldAuditValue(JsonNode auditLog) throws Exception {
        return auditValue(auditLog, "old_value");
    }

    protected JsonNode newAuditValue(JsonNode auditLog) throws Exception {
        return auditValue(auditLog, "new_value");
    }

    protected ResultActions getWithSuperAdminAuth(String path) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .header(AUTHORIZATION, superAdminAuth()));
    }

    protected ResultActions getWithTenantAdminAuth(String path, String username, String password) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .header(AUTHORIZATION, basicAuth(username, password)));
    }

    private JsonNode auditValue(JsonNode auditLog, String fieldName) throws Exception {
        return objectMapper.readTree(auditLog.get(fieldName).asText());
    }
}
