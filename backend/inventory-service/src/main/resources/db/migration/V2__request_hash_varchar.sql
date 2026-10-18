ALTER TABLE inventory_operation
    ALTER COLUMN request_hash TYPE VARCHAR(64)
    USING rtrim(request_hash)::VARCHAR(64);
