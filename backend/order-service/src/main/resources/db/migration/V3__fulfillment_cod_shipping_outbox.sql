-- Shipping snapshot captured at checkout (COD only in this release).
ALTER TABLE orders
    ADD COLUMN shipping_name varchar(120),
    ADD COLUMN shipping_phone varchar(32),
    ADD COLUMN shipping_address varchar(500),
    ADD COLUMN payment_method varchar(16) NOT NULL DEFAULT 'COD' CHECK (payment_method IN ('COD'));

-- Per-Store fulfillment, COD collection and settlement. Amounts stay the immutable checkout snapshot.
ALTER TABLE seller_orders
    ADD COLUMN status varchar(32) NOT NULL DEFAULT 'PLACED'
        CHECK (status IN ('PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_HANDOFF', 'DISPATCHED', 'DELIVERED', 'CANCELLED', 'REJECTED')),
    ADD COLUMN cod_status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (cod_status IN ('PENDING', 'COLLECTED', 'VOID')),
    ADD COLUMN status_reason varchar(500),
    ADD COLUMN delivered_at timestamptz,
    ADD COLUMN cod_collected_at timestamptz,
    ADD COLUMN cod_collected_by varchar(200),
    ADD COLUMN settled_at timestamptz,
    ADD COLUMN settled_by varchar(200),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_seller_orders_cod_requires_delivery CHECK (cod_status <> 'COLLECTED' OR status = 'DELIVERED'),
    ADD CONSTRAINT ck_seller_orders_settle_requires_cod CHECK (settled_at IS NULL OR cod_status = 'COLLECTED');
CREATE INDEX idx_seller_orders_store_status ON seller_orders(store_id, status);
CREATE INDEX idx_seller_orders_order ON seller_orders(order_id);

-- Immutable catalog labels at checkout so history survives later Product edits.
ALTER TABLE order_items
    ADD COLUMN sku varchar(128),
    ADD COLUMN product_name varchar(255);
CREATE INDEX idx_order_items_seller_order ON order_items(seller_order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);

-- Durable relay state: side effects (Inventory, Notification) are retried until delivered or dead.
ALTER TABLE outbox_events
    ADD COLUMN published_at timestamptz,
    ADD COLUMN attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    ADD COLUMN last_error varchar(300),
    ADD COLUMN dead boolean NOT NULL DEFAULT false;
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE published_at IS NULL AND dead = false;
