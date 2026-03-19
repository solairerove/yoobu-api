package com.yoobu.api.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yoobu.api.IntegrationTestSupport;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditLogIndexPlanIT extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restorePlannerDefaults() {
        jdbcTemplate.execute("RESET enable_seqscan");
    }

    @Test
    void explainUsesCreatedAtOrderIndexForGenericAuditFeed() throws Exception {
        seedAuditData();

        String plan = explain("""
                SELECT id
                FROM audit_log
                ORDER BY created_at DESC, id DESC
                LIMIT 20
                """);

        assertPlanContainsIndex(plan, "idx_audit_created_at_id");
    }

    @Test
    void explainUsesTenantCompositeIndexForTenantFilteredAuditFeed() throws Exception {
        long tenantId = seedAuditData();

        String plan = explain("""
                SELECT id
                FROM audit_log
                WHERE tenant_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 20
                """, tenantId);

        assertPlanContainsIndex(plan, "idx_audit_tenant_created_at_id");
    }

    @Test
    void explainUsesActionCompositeIndexForActionFilteredAuditFeed() throws Exception {
        seedAuditData();

        String plan = explain("""
                SELECT id
                FROM audit_log
                WHERE action = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 20
                """, "CREATE");

        assertPlanContainsIndex(plan, "idx_audit_action_created_at_id");
    }

    private long seedAuditData() throws Exception {
        long tenantId = createFoodOrderTenant("audit-plan", "Audit Plan", "bot-plan", "plan-admin", "plan-secret")
                .get("id").asLong();

        createService("audit-plan", "plan-admin", "plan-secret", "Pizza", "12.50");
        return tenantId;
    }

    private String explain(String sql, Object... args) {
        jdbcTemplate.execute("SET enable_seqscan = off");
        List<String> rows = jdbcTemplate.query(
                "EXPLAIN " + sql,
                (resultSet, rowNum) -> resultSet.getString(1),
                args
        );
        return rows.stream().collect(Collectors.joining("\n"));
    }

    private void assertPlanContainsIndex(String plan, String indexName) {
        assertTrue(
                plan.toLowerCase(Locale.ROOT).contains(indexName.toLowerCase(Locale.ROOT)),
                () -> "Expected plan to use index " + indexName + " but got:\n" + plan
        );
    }
}
