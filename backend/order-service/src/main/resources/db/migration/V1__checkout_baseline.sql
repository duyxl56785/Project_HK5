CREATE TABLE orders (
    id uuid PRIMARY KEY,
    customer_id varchar(200) NOT NULL,
    status varchar(32) NOT NULL,
    total numeric(19,2) NOT NULL CHECK (total >= 0),
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE TABLE seller_orders (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES orders(id),
    seller_id varchar(200) NOT NULL,
    store_id varchar(200) NOT NULL,
    gross numeric(19,2) NOT NULL CHECK (gross >= 0),
    platform_fee numeric(19,2) NOT NULL CHECK (platform_fee >= 0),
    UNIQUE (order_id,store_id)
);
CREATE TABLE order_items (
    id uuid PRIMARY KEY,
    seller_order_id uuid NOT NULL REFERENCES seller_orders(id),
    product_id varchar(200) NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price_snapshot numeric(19,2) NOT NULL CHECK (unit_price_snapshot >= 0),
    line_total numeric(19,2) NOT NULL CHECK (line_total >= 0)
);
CREATE TABLE checkout_attempts (
    id uuid PRIMARY KEY,
    customer_id varchar(200) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    fingerprint varchar(64) NOT NULL,
    state varchar(32) NOT NULL,
    order_id uuid REFERENCES orders(id),
    created_at timestamptz NOT NULL,
    CONSTRAINT uq_checkout_customer_key UNIQUE(customer_id,idempotency_key)
);
CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    topic varchar(128) NOT NULL,
    aggregate_id uuid NOT NULL,
    payload text NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);
