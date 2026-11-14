CREATE TABLE promotion_campaigns (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 120),
    kind TEXT NOT NULL CHECK (kind IN ('FLASH_SALE', 'SUPER_SALE', 'SELLER_CLEARANCE')),
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'PAUSED', 'CANCELLED')),
    store_id UUID,
    flash_date DATE,
    super_sale_date DATE,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT promotion_campaign_window CHECK (isfinite(starts_at) AND isfinite(ends_at) AND starts_at < ends_at),
    CONSTRAINT promotion_campaign_schedule CHECK (
        (kind = 'FLASH_SALE' AND store_id IS NULL AND flash_date IS NOT NULL AND isfinite(flash_date)
            AND super_sale_date IS NULL AND extract(isodow FROM flash_date) = 6
            AND starts_at = ((flash_date + TIME '22:00') AT TIME ZONE 'Asia/Ho_Chi_Minh')
            AND ends_at = ((flash_date + 1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'))
        OR (kind = 'SUPER_SALE' AND store_id IS NULL AND flash_date IS NULL
            AND super_sale_date IS NOT NULL AND isfinite(super_sale_date)
            AND starts_at = (super_sale_date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
            AND ends_at = ((super_sale_date + 1)::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'))
        OR (kind = 'SELLER_CLEARANCE' AND store_id IS NOT NULL AND flash_date IS NULL AND super_sale_date IS NULL
            AND ends_at - starts_at BETWEEN INTERVAL '1 hour' AND INTERVAL '720 hours'
            AND (submitted_at IS NULL OR (isfinite(submitted_at) AND submitted_at <= starts_at))
            AND (status NOT IN ('APPROVED', 'PAUSED') OR submitted_at IS NOT NULL))
    )
);

CREATE UNIQUE INDEX promotion_one_flash_per_saturday ON promotion_campaigns (flash_date)
    WHERE kind = 'FLASH_SALE' AND status <> 'CANCELLED';

-- DATE must be cast explicitly to timestamp without time zone to select the immutable overload.
CREATE UNIQUE INDEX promotion_one_super_per_month
    ON promotion_campaigns (date_trunc('month', super_sale_date::timestamp without time zone))
    WHERE kind = 'SUPER_SALE' AND status <> 'CANCELLED';

CREATE INDEX promotion_campaign_store_idx ON promotion_campaigns (store_id) WHERE store_id IS NOT NULL;

CREATE TABLE promotion_campaign_products (
    campaign_id UUID NOT NULL REFERENCES promotion_campaigns(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    promotional_unit_price NUMERIC NOT NULL CHECK (
        promotional_unit_price BETWEEN 1 AND 1000000000 AND promotional_unit_price = trunc(promotional_unit_price)
    ),
    reason TEXT,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (campaign_id, product_id),
    CONSTRAINT promotion_registration_rejection_reason CHECK (
        status <> 'REJECTED' OR (reason IS NOT NULL AND char_length(btrim(reason)) BETWEEN 1 AND 500)
    )
);

CREATE INDEX promotion_approved_product_idx ON promotion_campaign_products (product_id, campaign_id)
    WHERE status = 'APPROVED';

CREATE TABLE promotion_campaign_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES promotion_campaigns(id) ON DELETE RESTRICT,
    product_id UUID,
    actor TEXT NOT NULL CHECK (char_length(btrim(actor)) > 0),
    action TEXT NOT NULL CHECK (char_length(btrim(action)) > 0),
    from_status TEXT,
    to_status TEXT NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (campaign_id, product_id) REFERENCES promotion_campaign_products(campaign_id, product_id)
        ON DELETE RESTRICT
);

CREATE INDEX promotion_audit_campaign_idx ON promotion_campaign_audit (campaign_id, id);

CREATE FUNCTION reject_promotion_audit_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Promotion audit is append-only' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER promotion_audit_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON promotion_campaign_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_promotion_audit_mutation();
