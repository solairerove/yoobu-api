-- Move currency from booking_item to booking; add payment_qr_url to booking

ALTER TABLE booking ADD COLUMN currency VARCHAR(10);

-- Backfill from first item, fall back to 'USD' for bookings with no items
UPDATE booking b
SET currency = COALESCE(
    (SELECT bi.currency FROM booking_item bi WHERE bi.booking_id = b.id ORDER BY bi.id LIMIT 1),
    'USD'
);

ALTER TABLE booking ALTER COLUMN currency SET NOT NULL;

-- Payment QR URL stored at booking creation time (null for historical bookings)
ALTER TABLE booking ADD COLUMN payment_qr_url TEXT;

-- booking_item.currency is no longer the source of truth; make it nullable for new rows
ALTER TABLE booking_item ALTER COLUMN currency DROP NOT NULL;
