-- TEST-ONLY schema proposal. Not on the Flyway runtime path; Store boundary/currency contract are pending.
CREATE TABLE catalog_product_candidate (
    id UUID PRIMARY KEY,
    store_reference TEXT COLLATE "C" NOT NULL CHECK (store_reference ~ '[^[:space:]]'),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    inventory_sku VARCHAR(128) COLLATE "C" NOT NULL,
    unit_price NUMERIC NOT NULL CHECK (unit_price > 0 AND unit_price <> 'NaN'::numeric
        AND unit_price <> 'Infinity'::numeric),
    currency TEXT COLLATE "C" NOT NULL CHECK (currency ~ '[^[:space:]]'),
    status VARCHAR(16) NOT NULL CHECK (status IN
        ('DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'INACTIVE')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT candidate_product_time_order CHECK (updated_at >= created_at),
    CONSTRAINT candidate_global_sku UNIQUE (inventory_sku),
    CONSTRAINT candidate_sku_not_blank CHECK (inventory_sku ~ '[^[:space:]]'),
    CONSTRAINT candidate_sku_no_outer_space CHECK (inventory_sku = btrim(inventory_sku))
);
