-- Nullable only for rows created before these requirements existed; the API requires them on apply and resubmit.
ALTER TABLE seller_profiles
    ADD COLUMN business_type VARCHAR(16),
    ADD COLUMN id_number VARCHAR(12),
    ADD COLUMN tax_code VARCHAR(14),
    ADD COLUMN contact_email VARCHAR(254),
    ADD COLUMN bank_name VARCHAR(100),
    ADD COLUMN bank_account_number VARCHAR(19),
    ADD COLUMN bank_account_holder VARCHAR(100),
    ADD COLUMN terms_version VARCHAR(16),
    ADD COLUMN terms_accepted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE seller_profiles
    ADD CONSTRAINT ck_seller_profiles_business_type
        CHECK (business_type IS NULL OR business_type IN ('INDIVIDUAL', 'HOUSEHOLD', 'COMPANY'));
