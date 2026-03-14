ALTER TABLE service
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

UPDATE service
SET status = CASE
    WHEN deleted_at IS NOT NULL THEN 'DELETED'
    WHEN active THEN 'ACTIVE'
    ELSE 'INACTIVE'
END
WHERE status IS NULL;

ALTER TABLE service
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

DROP INDEX IF EXISTS idx_service_tenant;

CREATE INDEX idx_service_tenant ON service(tenant_id, status, sort_order, id) WHERE status <> 'DELETED';

ALTER TABLE service
    DROP COLUMN IF EXISTS active;
