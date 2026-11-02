-- Pending reservations only. No file is stored or published by this migration.
-- READY/storage key binding requires the separately reviewed ImageStorage integration.
CREATE TABLE product_image_metadata (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    content_sha256 TEXT COLLATE "C" NOT NULL,
    media_type TEXT COLLATE "C" NOT NULL,
    size_bytes INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    display_order INTEGER,
    state TEXT COLLATE "C" NOT NULL,
    storage_key TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT product_image_product_fk FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT product_image_operation_uk UNIQUE (product_id, operation_id),
    -- Five distinct slots are a DB-enforced cap, including direct/concurrent SQL writes.
    CONSTRAINT product_image_slot_uk UNIQUE (product_id, display_order),
    CONSTRAINT product_image_digest_valid CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT product_image_media_valid CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT product_image_size_valid CHECK (size_bytes BETWEEN 1 AND 2097152),
    CONSTRAINT product_image_dimensions_valid CHECK (
        width BETWEEN 1 AND 8192 AND height BETWEEN 1 AND 8192
        AND width::BIGINT * height::BIGINT <= 16000000
    ),
    CONSTRAINT product_image_state_valid CHECK (
        (state = 'PENDING_STORAGE' AND display_order BETWEEN 1 AND 5
            AND display_order IS NOT NULL AND cancelled_at IS NULL)
        OR (state = 'CANCELLED' AND display_order IS NULL
            AND cancelled_at IS NOT NULL AND cancelled_at >= created_at)
    ),
    CONSTRAINT product_image_storage_disabled CHECK (storage_key IS NULL),
    CONSTRAINT product_image_version_nonnegative CHECK (version >= 0)
);

CREATE UNIQUE INDEX product_image_pending_content_uk
    ON product_image_metadata(product_id, content_sha256) WHERE state = 'PENDING_STORAGE';

CREATE FUNCTION protect_product_image_reservation() RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF ROW(NEW.id, NEW.product_id, NEW.operation_id, NEW.content_sha256,
           NEW.media_type, NEW.size_bytes, NEW.width, NEW.height, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.id, OLD.product_id, OLD.operation_id, OLD.content_sha256,
           OLD.media_type, OLD.size_bytes, OLD.width, OLD.height, OLD.created_at) THEN
        RAISE EXCEPTION 'Product image reservation identity and validated content are immutable'
            USING ERRCODE = '23514', CONSTRAINT = 'product_image_identity_immutable';
    END IF;
    IF OLD.state = 'PENDING_STORAGE' AND NEW.state = 'PENDING_STORAGE'
       AND NEW.display_order IS DISTINCT FROM OLD.display_order THEN
        RAISE EXCEPTION 'Pending image display slot is immutable'
            USING ERRCODE = '23514', CONSTRAINT = 'product_image_slot_immutable';
    END IF;
    IF OLD.state = 'CANCELLED'
       AND ROW(NEW.state, NEW.display_order, NEW.cancelled_at)
           IS DISTINCT FROM ROW(OLD.state, OLD.display_order, OLD.cancelled_at) THEN
        RAISE EXCEPTION 'Cancelled image reservation is terminal'
            USING ERRCODE = '23514', CONSTRAINT = 'product_image_cancelled_terminal';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER product_image_reservation_immutable
BEFORE UPDATE ON product_image_metadata
FOR EACH ROW EXECUTE FUNCTION protect_product_image_reservation();
