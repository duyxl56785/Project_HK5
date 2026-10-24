DO $$
BEGIN
    IF current_setting('server_encoding') <> 'UTF8' THEN
        RAISE EXCEPTION 'Product persistence requires UTF8 server encoding';
    END IF;
END;
$$;

-- Java String.length counts supplementary Unicode characters as two UTF-16 units.
CREATE FUNCTION product_utf16_length(value TEXT) RETURNS INTEGER
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT coalesce(sum(CASE WHEN ascii(ch) > 65535 THEN 2 ELSE 1 END), 0)::INTEGER
    FROM unnest(string_to_array(value, NULL)) AS characters(ch);
$$;

CREATE TABLE products (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    category_id UUID NOT NULL,
    inventory_sku TEXT COLLATE "C" NOT NULL,
    name TEXT NOT NULL,
    unit_price NUMERIC NOT NULL,
    currency TEXT COLLATE "C" NOT NULL,
    status VARCHAR(16) COLLATE "C" NOT NULL,
    review_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT products_category_fk FOREIGN KEY (category_id)
        REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT products_inventory_sku_uk UNIQUE (inventory_sku),
    CONSTRAINT products_sku_length CHECK (product_utf16_length(inventory_sku) BETWEEN 1 AND 128),
    -- Character.isWhitespace OR Character.isSpaceChar in Java 21 contains these 28 code points.
    CONSTRAINT products_sku_boundary CHECK (
        ascii(left(inventory_sku, 1)) NOT IN (
            9, 10, 11, 12, 13, 28, 29, 30, 31, 32, 160, 5760,
            8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202,
            8232, 8233, 8239, 8287, 12288
        )
        AND ascii(right(inventory_sku, 1)) NOT IN (
            9, 10, 11, 12, 13, 28, 29, 30, 31, 32, 160, 5760,
            8192, 8193, 8194, 8195, 8196, 8197, 8198, 8199, 8200, 8201, 8202,
            8232, 8233, 8239, 8287, 12288
        )
    ),
    -- Domain name/reason strip uses Java isWhitespace, which excludes the three non-breaking spaces.
    CONSTRAINT products_name_valid CHECK (
        product_utf16_length(name) BETWEEN 1 AND 255
        AND name = btrim(name,
            U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000') COLLATE "C"
    ),
    CONSTRAINT products_price_valid CHECK (
        unit_price >= 1 AND unit_price <= 1000000000 AND unit_price = trunc(unit_price)
    ),
    CONSTRAINT products_currency_valid CHECK (currency = 'VND'),
    CONSTRAINT products_status_valid CHECK (status IN (
        'DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'INACTIVE'
    )),
    CONSTRAINT products_reason_valid CHECK (
        (status IN ('REJECTED', 'SUSPENDED') AND review_reason IS NOT NULL
            AND char_length(review_reason) BETWEEN 1 AND 500
            AND review_reason = btrim(review_reason,
                U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\1680\2000\2001\2002\2003\2004\2005\2006\2008\2009\200A\2028\2029\205F\3000') COLLATE "C")
        OR (status IN ('DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'INACTIVE') AND review_reason IS NULL)
    ),
    CONSTRAINT products_version_nonnegative CHECK (version >= 0)
);

CREATE INDEX products_category_id_idx ON products(category_id);

CREATE FUNCTION reject_product_sku_change() RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.inventory_sku IS DISTINCT FROM OLD.inventory_sku THEN
        RAISE EXCEPTION 'Product inventory SKU is immutable'
            USING ERRCODE = '23514', CONSTRAINT = 'products_sku_immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER products_sku_immutable
BEFORE UPDATE OF inventory_sku ON products
FOR EACH ROW EXECUTE FUNCTION reject_product_sku_change();
