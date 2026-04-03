package com.yoobu.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaCleanupIT extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void booking_table_should_not_have_service_id_column() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'booking' AND column_name = 'service_id'",
                Integer.class
        );
        assertEquals(0, count, "booking.service_id was dropped in V11 — column must not exist");
    }

    @Test
    void booking_table_should_not_have_slot_id_column() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'booking' AND column_name = 'slot_id'",
                Integer.class
        );
        assertEquals(0, count, "booking.slot_id was dropped in V13 — column must not exist");
    }

    @Test
    void booking_item_table_should_not_have_currency_column() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'booking_item' AND column_name = 'currency'",
                Integer.class
        );
        assertEquals(0, count, "booking_item.currency was dropped in V12 — column must not exist");
    }
}
