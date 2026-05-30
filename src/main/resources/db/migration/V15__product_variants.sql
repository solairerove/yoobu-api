CREATE TABLE product_variant (
    id         BIGSERIAL     PRIMARY KEY,
    service_id BIGINT        NOT NULL REFERENCES service(id),
    size       VARCHAR(20),
    color      VARCHAR(50),
    price      NUMERIC(10,2) NOT NULL,
    stock      INT           NOT NULL DEFAULT 0,
    version    BIGINT        NOT NULL DEFAULT 0,
    sort_order INT           NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT product_variant_stock_non_negative CHECK (stock >= 0)
);

CREATE INDEX product_variant_service_idx ON product_variant(service_id);

ALTER TABLE booking_item ADD COLUMN variant_id    BIGINT REFERENCES product_variant(id);
ALTER TABLE booking_item ADD COLUMN variant_size  VARCHAR(20);
ALTER TABLE booking_item ADD COLUMN variant_color VARCHAR(50);
