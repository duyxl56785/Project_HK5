-- Purchases a customer may review, opened by delivered events from Order. The key makes redelivery harmless.
CREATE TABLE reviewable_items (
    order_id uuid NOT NULL,
    product_id uuid NOT NULL,
    customer_id varchar(200) NOT NULL,
    opened_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (order_id, product_id)
);
