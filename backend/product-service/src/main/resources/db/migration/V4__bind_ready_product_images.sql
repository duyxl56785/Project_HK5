ALTER TABLE product_image_metadata ADD COLUMN ready_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE product_image_metadata DROP CONSTRAINT product_image_state_valid;
ALTER TABLE product_image_metadata DROP CONSTRAINT product_image_storage_disabled;

ALTER TABLE product_image_metadata ADD CONSTRAINT product_image_storage_key_valid CHECK (
    storage_key IS NULL OR (
        storage_key = id::text
        AND storage_key ~ '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
    )
);
ALTER TABLE product_image_metadata ADD CONSTRAINT product_image_state_valid CHECK (
    (state = 'PENDING_STORAGE' AND display_order BETWEEN 1 AND 5 AND display_order IS NOT NULL
        AND cancelled_at IS NULL AND ready_at IS NULL AND storage_key IS NULL)
    OR (state = 'READY' AND display_order BETWEEN 1 AND 5 AND display_order IS NOT NULL
        AND cancelled_at IS NULL AND ready_at IS NOT NULL AND ready_at >= created_at
        AND storage_key IS NOT NULL)
    OR (state = 'CANCELLED' AND display_order IS NULL AND cancelled_at IS NOT NULL AND cancelled_at >= created_at
        AND ((storage_key IS NULL AND ready_at IS NULL)
            OR (storage_key IS NOT NULL AND ready_at IS NOT NULL AND ready_at >= created_at AND ready_at <= cancelled_at)))
);

DROP INDEX product_image_pending_content_uk;
CREATE UNIQUE INDEX product_image_pending_content_uk
    ON product_image_metadata(product_id, content_sha256) WHERE state IN ('PENDING_STORAGE', 'READY');

CREATE OR REPLACE FUNCTION protect_product_image_reservation() RETURNS TRIGGER
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
    IF OLD.state IN ('PENDING_STORAGE', 'READY') AND NEW.state IN ('PENDING_STORAGE', 'READY')
       AND NEW.display_order IS DISTINCT FROM OLD.display_order THEN
        RAISE EXCEPTION 'Live image display slot is immutable'
            USING ERRCODE = '23514', CONSTRAINT = 'product_image_slot_immutable';
    END IF;
    IF OLD.state = 'READY'
       AND (NEW.state NOT IN ('READY', 'CANCELLED')
            OR ROW(NEW.storage_key, NEW.ready_at) IS DISTINCT FROM ROW(OLD.storage_key, OLD.ready_at)) THEN
        RAISE EXCEPTION 'Ready image storage binding is immutable'
            USING ERRCODE = '23514', CONSTRAINT = 'product_image_ready_immutable';
    END IF;
    IF OLD.state = 'CANCELLED'
       AND ROW(NEW.state, NEW.display_order, NEW.cancelled_at, NEW.storage_key, NEW.ready_at)
           IS DISTINCT FROM ROW(OLD.state, OLD.display_order, OLD.cancelled_at, OLD.storage_key, OLD.ready_at) THEN
        RAISE EXCEPTION 'Cancelled image reservation is terminal'
            USING ERRCODE = '23514', CONSTRAINT = 'product_image_cancelled_terminal';
    END IF;
    RETURN NEW;
END;
$$;
