CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id UUID,
    CONSTRAINT categories_name_not_blank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT categories_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT categories_parent_fk FOREIGN KEY (parent_id)
        REFERENCES categories (id) ON DELETE RESTRICT
);

CREATE INDEX categories_parent_id_idx ON categories (parent_id);
