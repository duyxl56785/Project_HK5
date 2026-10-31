-- Nullable only for rows created before contact details existed; the API requires them.
ALTER TABLE seller_profiles
    ADD COLUMN representative_name VARCHAR(100),
    ADD COLUMN phone VARCHAR(20),
    ADD COLUMN pickup_address VARCHAR(255);
