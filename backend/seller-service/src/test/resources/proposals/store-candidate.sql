-- TEST ONLY. S01 has not approved this local Seller/Store database boundary.
-- Ownership is derived from seller_profiles, not duplicated or accepted from a client.
CREATE TABLE store_candidate (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES seller_profiles(id) ON DELETE RESTRICT,
    slug VARCHAR(128) COLLATE "C" NOT NULL,
    name VARCHAR(255) NOT NULL,
    open BOOLEAN NOT NULL,
    CONSTRAINT candidate_one_store_per_seller UNIQUE (seller_id),
    CONSTRAINT candidate_store_slug_unique UNIQUE (slug),
    CONSTRAINT candidate_store_slug_canonical CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT candidate_store_name_valid CHECK (name ~ '[^[:space:]]' AND name = btrim(name))
);
