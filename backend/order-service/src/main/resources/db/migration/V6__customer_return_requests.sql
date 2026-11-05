-- TV3 W4 #155: buyer Return MVP, separate from SellerOrder fulfillment and CheckoutDraft retention.
-- Full SellerOrder only; no refund, no reverse shipping, no blind Inventory RELEASE/restock.
-- Match Java String.length() / browser maxLength: non-BMP emoji count as two UTF-16 units.
CREATE FUNCTION hk5_order_return_utf16_length(input_text text) RETURNS integer
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $return_utf16$
    SELECT COALESCE(sum(CASE WHEN ascii(substr(input_text, n, 1)) > 65535 THEN 2 ELSE 1 END), 0)::integer
    FROM generate_series(1, char_length(input_text)) AS n;
$return_utf16$;

-- Composite owner FK: the Store order must really belong to this root Order, even
-- for direct SQL writers and later Seller/Inventory integrations.
ALTER TABLE seller_orders
    ADD CONSTRAINT uq_seller_orders_return_owner UNIQUE (id, order_id);

CREATE TABLE return_requests (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES orders(id),
    seller_order_id uuid NOT NULL,
    CONSTRAINT fk_return_seller_order_owner
        FOREIGN KEY (seller_order_id, order_id) REFERENCES seller_orders(id, order_id),
    customer_id varchar(200) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_fingerprint char(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    reason varchar(500) NOT NULL CHECK (hk5_order_return_utf16_length(btrim(reason)) BETWEEN 1 AND 500),
    status varchar(16) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'RECEIVED')),
    requested_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    reviewed_by varchar(200),
    reviewed_at timestamptz,
    received_by varchar(200),
    received_at timestamptz,
    CONSTRAINT uq_return_customer_key UNIQUE(customer_id,idempotency_key),
    CONSTRAINT ck_return_review CHECK (
        (status='REQUESTED' AND reviewed_by IS NULL AND reviewed_at IS NULL AND received_by IS NULL AND received_at IS NULL)
        OR (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND received_by IS NULL AND received_at IS NULL)
        OR (status='RECEIVED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND received_by IS NOT NULL AND received_at IS NOT NULL)
    )
);
-- Exactly one active Return per Store order. A REJECTED request is an audited closed request.
-- RECEIVED remains settlement-blocking until a separately approved Finance workflow exists.
CREATE UNIQUE INDEX uq_return_active_seller_order ON return_requests(seller_order_id)
    WHERE status IN ('REQUESTED','APPROVED','RECEIVED');
CREATE INDEX idx_return_requests_order ON return_requests(order_id,requested_at DESC);
CREATE INDEX idx_return_requests_store_order ON return_requests(seller_order_id,requested_at DESC);
