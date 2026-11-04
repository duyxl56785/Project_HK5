-- Candidate provider ledger for per-Store/SellerOrder reservations.
-- No HTTP endpoint is exposed by this migration. The wire and authorization remain owner-gated.
CREATE TABLE inventory_reservation (
    reservation_id VARCHAR(128) PRIMARY KEY,
    store_id VARCHAR(128) NOT NULL,
    seller_id VARCHAR(128) NOT NULL,
    seller_order_id VARCHAR(128) NOT NULL,
    initial_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_reservation_store_order UNIQUE (store_id, seller_order_id)
);

ALTER TABLE inventory_operation
    ADD COLUMN reservation_id VARCHAR(128) NULL;

ALTER TABLE inventory_operation
    ADD CONSTRAINT fk_inventory_operation_reservation
    FOREIGN KEY (reservation_id) REFERENCES inventory_reservation (reservation_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE UNIQUE INDEX uq_inventory_reservation_reserve_operation
    ON inventory_operation (reservation_id)
    WHERE reservation_id IS NOT NULL AND operation_type = 'RESERVE';

CREATE TABLE inventory_reservation_line (
    reservation_id VARCHAR(128) NOT NULL REFERENCES inventory_reservation (reservation_id),
    sku VARCHAR(128) NOT NULL REFERENCES inventory_item (sku),
    initial_quantity BIGINT NOT NULL CHECK (initial_quantity > 0),
    consumed_quantity BIGINT NOT NULL DEFAULT 0 CHECK (consumed_quantity >= 0),
    released_quantity BIGINT NOT NULL DEFAULT 0 CHECK (released_quantity >= 0),
    held_quantity BIGINT NOT NULL CHECK (held_quantity >= 0),
    PRIMARY KEY (reservation_id, sku),
    CHECK (initial_quantity = consumed_quantity + released_quantity + held_quantity)
);

CREATE TABLE inventory_reservation_operation_line (
    operation_id VARCHAR(128) NOT NULL REFERENCES inventory_operation (operation_id),
    sku VARCHAR(128) NOT NULL REFERENCES inventory_item (sku),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (operation_id, sku)
);
