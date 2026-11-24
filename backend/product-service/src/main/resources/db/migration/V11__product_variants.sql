-- Size/color variants. Each variant is a whole Product row (own SKU, price, stock, lifecycle), so Cart, Order
-- and Inventory keep working by productId/SKU. This table only groups the rows behind the first one (the lead).
CREATE TABLE product_variants (
    product_id UUID PRIMARY KEY REFERENCES products(id) ON DELETE RESTRICT,
    group_id UUID NOT NULL REFERENCES product_variants(product_id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK (position BETWEEN 1 AND 30),
    size VARCHAR(20),
    color VARCHAR(30),
    CONSTRAINT product_variants_option_present CHECK (size IS NOT NULL OR color IS NOT NULL),
    CONSTRAINT product_variants_size_valid CHECK (size IS NULL OR (size <> '' AND size = btrim(size))),
    CONSTRAINT product_variants_color_valid CHECK (color IS NULL OR (color <> '' AND color = btrim(color))),
    CONSTRAINT product_variants_lead_first CHECK ((product_id = group_id) = (position = 1)),
    CONSTRAINT product_variants_position_uk UNIQUE (group_id, position)
);

CREATE UNIQUE INDEX product_variants_option_uk
    ON product_variants (group_id, lower(coalesce(size, '')), lower(coalesce(color, '')));
