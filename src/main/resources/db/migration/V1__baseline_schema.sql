CREATE TABLE tenant (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(30) NOT NULL,
    bot_token VARCHAR(255),
    owner_telegram_id BIGINT,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_config (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    key VARCHAR(100) NOT NULL,
    value TEXT,
    UNIQUE (tenant_id, key)
);

CREATE TABLE service (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2),
    unit VARCHAR(50) DEFAULT 'шт',
    duration_minutes INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE booking (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(id),
    type VARCHAR(20) NOT NULL,
    telegram_user_id BIGINT NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_phone VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    note TEXT,
    total_price NUMERIC(10, 2),
    delivery_date DATE,
    slot_id BIGINT,
    service_id BIGINT REFERENCES service(id),
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE booking_item (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES booking(id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES service(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL
);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    entity VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id BIGINT,
    old_value TEXT,
    new_value TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_service_tenant ON service(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_booking_tenant ON booking(tenant_id);
CREATE INDEX idx_booking_tenant_date ON booking(tenant_id, delivery_date);
CREATE INDEX idx_booking_tenant_status ON booking(tenant_id, status);
CREATE INDEX idx_booking_telegram_user ON booking(tenant_id, telegram_user_id);
CREATE INDEX idx_audit_tenant_entity ON audit_log(tenant_id, entity, entity_id);
