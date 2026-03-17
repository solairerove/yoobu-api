package com.yoobu.api;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public abstract class IntegrationTestSupport {

    private static final String TELEGRAM_USER_ID_HEADER = "X-Telegram-User-Id";
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
        registry.add("app.superadmin.username", () -> SUPERADMIN_USERNAME);
        registry.add("app.superadmin.password", () -> SUPERADMIN_PASSWORD);
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
        return readJson(performJson(
                withSuperAdminAuth(post("/superadmin/tenants")),
                tenant(slug, name, type, botToken, adminUsername, adminPassword)
        )
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
        return readJson(performJson(
                withBasicAuth(post(adminPath(slug, "/services")), adminUsername, adminPassword),
                serviceRequest(name, price)
        )
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected JsonNode createBooking(String slug, long telegramUserId, long serviceId, int quantity) throws Exception {
        return readJson(performJson(
                withTelegramUser(post(publicPath(slug, "/bookings")), telegramUserId),
                bookingPayload(serviceId, quantity)
        )
                .andExpect(status().isOk())
                .andReturn());
    }

    protected JsonNode createBooking(String slug, long telegramUserId, long serviceId, int quantity, LocalDate deliveryDate)
            throws Exception {
        return readJson(performJson(
                withTelegramUser(post(publicPath(slug, "/bookings")), telegramUserId),
                bookingPayload(serviceId, quantity, deliveryDate)
        )
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
        updateBookingStatus(slug, adminUsername, adminPassword, bookingId, statusValue.name());
    }

    protected void updateBookingStatus(
            String slug,
            String adminUsername,
            String adminPassword,
            long bookingId,
            String statusValue
    ) throws Exception {
        performJson(
                withBasicAuth(put(adminPath(slug, "/bookings/" + bookingId + "/status")), adminUsername, adminPassword),
                """
                        {
                          "status": "%s"
                        }
                        """.formatted(statusValue)
        )
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
        return mockMvc.perform(withSuperAdminAuth(get(path)));
    }

    protected ResultActions getWithTenantAdminAuth(String path, String username, String password) throws Exception {
        return mockMvc.perform(withBasicAuth(get(path), username, password));
    }

    protected ResultActions superAdminGet(String path) throws Exception {
        return getWithSuperAdminAuth(path);
    }

    protected ResultActions superAdminPostJson(String path, Object body) throws Exception {
        return performJson(withSuperAdminAuth(post(path)), body);
    }

    protected ResultActions superAdminPutJson(String path, Object body) throws Exception {
        return performJson(withSuperAdminAuth(put(path)), body);
    }

    protected ResultActions superAdminPostForm(String path, Map<String, String> form) throws Exception {
        return mockMvc.perform(withForm(withSuperAdminAuth(post(path)), form));
    }

    protected ResultActions tenantAdminGet(String slug, String path, String username, String password) throws Exception {
        return getWithTenantAdminAuth(adminPath(slug, path), username, password);
    }

    protected ResultActions tenantAdminPostJson(
            String slug,
            String path,
            String username,
            String password,
            Object body
    ) throws Exception {
        return performJson(withBasicAuth(post(adminPath(slug, path)), username, password), body);
    }

    protected ResultActions tenantAdminPutJson(
            String slug,
            String path,
            String username,
            String password,
            Object body
    ) throws Exception {
        return performJson(withBasicAuth(put(adminPath(slug, path)), username, password), body);
    }

    protected ResultActions tenantAdminDelete(String slug, String path, String username, String password) throws Exception {
        return mockMvc.perform(withBasicAuth(delete(adminPath(slug, path)), username, password));
    }

    protected ResultActions tenantAdminPostForm(
            String slug,
            String path,
            String username,
            String password,
            Map<String, String> form
    ) throws Exception {
        return mockMvc.perform(withForm(
                withBasicAuth(post(adminPath(slug, path)), username, password),
                form
        ));
    }

    protected ResultActions tenantPublicGet(String slug, String path) throws Exception {
        return mockMvc.perform(get(publicPath(slug, path)));
    }

    protected ResultActions tenantPublicGetAsUser(String slug, String path, long userId) throws Exception {
        return mockMvc.perform(withTelegramUser(get(publicPath(slug, path)), userId));
    }

    protected ResultActions tenantPublicPostJson(String slug, String path, long userId, String body) throws Exception {
        return performJson(withTelegramUser(post(publicPath(slug, path)), userId), body);
    }

    protected String adminPath(String slug, String path) {
        return tenantPath("/admin", slug, path);
    }

    protected String publicPath(String slug, String path) {
        return tenantPath("/t", slug, path);
    }

    protected String panelPath(String slug, String path) {
        return tenantPath("/admin", slug, "/panel" + normalizePath(path));
    }

    private String tenantPath(String root, String slug, String path) {
        return root + "/" + slug + normalizePath(path);
    }

    private String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private MockHttpServletRequestBuilder withForm(
            MockHttpServletRequestBuilder builder,
            Map<String, String> form
    ) {
        form.forEach(builder::param);
        return builder;
    }

    private MockHttpServletRequestBuilder withSuperAdminAuth(MockHttpServletRequestBuilder builder) {
        return builder.header(AUTHORIZATION, superAdminAuth());
    }

    private MockHttpServletRequestBuilder withBasicAuth(
            MockHttpServletRequestBuilder builder,
            String username,
            String password
    ) {
        return builder.header(AUTHORIZATION, basicAuth(username, password));
    }

    private MockHttpServletRequestBuilder withTelegramUser(MockHttpServletRequestBuilder builder, long userId) {
        return builder.header(TELEGRAM_USER_ID_HEADER, tenantUserId(userId));
    }

    private ResultActions performJson(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return mockMvc.perform(builder
                .contentType(APPLICATION_JSON)
                .content(jsonBody(body)));
    }

    private String jsonBody(Object body) throws Exception {
        return body instanceof String rawJson ? rawJson : objectMapper.writeValueAsString(body);
    }

    private JsonNode auditValue(JsonNode auditLog, String fieldName) throws Exception {
        return objectMapper.readTree(auditLog.get(fieldName).asText());
    }
}
