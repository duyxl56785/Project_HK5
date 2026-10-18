CREATE TABLE stores (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(128) COLLATE "C" NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stores_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT uk_stores_seller_id UNIQUE (seller_id),
    CONSTRAINT uk_stores_slug UNIQUE (slug),
    CONSTRAINT stores_name_valid CHECK (name ~ '[^[:space:]]' AND name = btrim(name)),
    CONSTRAINT stores_slug_canonical CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT stores_status_valid CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT stores_version_nonnegative CHECK (version >= 0),
    CONSTRAINT stores_timestamps_valid CHECK (
        isfinite(created_at) AND isfinite(updated_at) AND updated_at >= created_at
    )
);
