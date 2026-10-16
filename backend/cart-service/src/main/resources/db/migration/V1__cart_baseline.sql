CREATE TABLE carts (
    id uuid PRIMARY KEY,
    customer_id varchar(200) NOT NULL UNIQUE,
    revision bigint NOT NULL DEFAULT 0,
    checkout_claim varchar(128),
    updated_at timestamptz NOT NULL
);
CREATE TABLE cart_items (
    id uuid PRIMARY KEY,
    cart_id uuid NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id varchar(200) NOT NULL,
    store_id varchar(200) NOT NULL,
    seller_id varchar(200) NOT NULL,
    quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 1000),
    unit_price_snapshot numeric(19,2) NOT NULL CHECK (unit_price_snapshot >= 0),
    CONSTRAINT uq_cart_product UNIQUE (cart_id, product_id)
);
CREATE INDEX idx_cart_items_store ON cart_items(store_id);
