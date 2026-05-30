CREATE TABLE product_variant_image (
    id          BIGSERIAL PRIMARY KEY,
    variant_id  BIGINT NOT NULL REFERENCES product_variant(id) ON DELETE CASCADE,
    image_url   TEXT NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_variant_image_variant_id ON product_variant_image(variant_id);

INSERT INTO product_variant_image (variant_id, image_url, sort_order, created_at)
SELECT id, image_url, 0, now()
FROM product_variant
WHERE image_url IS NOT NULL;
