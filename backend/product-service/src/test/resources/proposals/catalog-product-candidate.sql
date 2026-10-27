-- TEST-ONLY schema proposal. Not on the Flyway runtime path; production money/mapping remain gated.
CREATE TABLE catalog_product_candidate (
    id UUID PRIMARY KEY,
    store_reference TEXT COLLATE "C" NOT NULL CHECK (store_reference ~ '[^[:space:]]'),
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    -- VARCHAR(n) silently truncates excess ASCII spaces before CHECK; exact keys must never be rewritten.
    inventory_sku TEXT COLLATE "C" NOT NULL,
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
    -- Preserve the prototype's PostgreSQL character limit; Java UTF-16 parity is a production adapter gate.
    CONSTRAINT candidate_sku_length CHECK (char_length(inventory_sku) <= 128),
    CONSTRAINT candidate_sku_not_blank CHECK (inventory_sku ~ '[^[:space:]]'),
    -- Java 21 Character.isWhitespace OR isSpaceChar at either boundary; reject, never rewrite the key.
    CONSTRAINT candidate_sku_no_outer_space CHECK (inventory_sku = btrim(inventory_sku,
        U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'))
);
