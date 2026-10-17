CREATE TABLE inventory_item (
    sku VARCHAR(128) PRIMARY KEY,
    available_quantity BIGINT NOT NULL DEFAULT 0 CHECK (available_quantity >= 0),
    reserved_quantity BIGINT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory_operation (
    operation_id VARCHAR(128) PRIMARY KEY,
    operation_type VARCHAR(24) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_item_updated_at ON inventory_item (updated_at);
