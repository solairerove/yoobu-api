ALTER TABLE booking_item
    ADD COLUMN currency VARCHAR(10);

UPDATE booking_item AS bi
SET currency = COALESCE(NULLIF(TRIM(tc.value), ''), 'USD')
FROM booking AS b
LEFT JOIN tenant_config AS tc
    ON tc.tenant_id = b.tenant_id
    AND tc.key = 'currency'
WHERE bi.booking_id = b.id;

ALTER TABLE booking_item
    ALTER COLUMN currency SET NOT NULL;
