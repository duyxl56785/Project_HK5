-- Seller-editable description, kept apart from the moderated Product identity columns.
CREATE TABLE product_details (
    product_id UUID PRIMARY KEY REFERENCES products(id) ON DELETE RESTRICT,
    description VARCHAR(5000) NOT NULL DEFAULT ''
);

-- Stored, validated images (max five ordered slots per Product). Bytes live in ImageStorage by key.
CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    storage_key VARCHAR(36) COLLATE "C" NOT NULL UNIQUE,
    media_type VARCHAR(16) NOT NULL CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp')),
    width INTEGER NOT NULL CHECK (width BETWEEN 1 AND 8192),
    height INTEGER NOT NULL CHECK (height BETWEEN 1 AND 8192),
    size_bytes INTEGER NOT NULL CHECK (size_bytes BETWEEN 1 AND 2097152),
    display_order INTEGER NOT NULL CHECK (display_order BETWEEN 1 AND 5),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT product_images_slot_uk UNIQUE (product_id, display_order)
);

-- F15: one review per (order, product), only by the buyer of a DELIVERED order (checked via Order).
CREATE TABLE product_reviews (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    customer_id VARCHAR(128) COLLATE "C" NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(2000),
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT product_reviews_order_product_uk UNIQUE (order_id, product_id)
);
CREATE INDEX product_reviews_product_idx ON product_reviews(product_id, created_at DESC);
