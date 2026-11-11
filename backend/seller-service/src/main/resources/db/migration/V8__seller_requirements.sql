-- Nullable only for rows created before these requirements existed; the API requires them on apply and resubmit.
-- Rerunnable: scripts/demo/rehearse-v7-upgrade.mjs replays V7 and every later migration on a live database.
ALTER TABLE seller_profiles
    ADD COLUMN IF NOT EXISTS business_type VARCHAR(16),
    ADD COLUMN IF NOT EXISTS id_number VARCHAR(12),
    ADD COLUMN IF NOT EXISTS tax_code VARCHAR(14),
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(254),
    ADD COLUMN IF NOT EXISTS bank_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(19),
    ADD COLUMN IF NOT EXISTS bank_account_holder VARCHAR(100),
    ADD COLUMN IF NOT EXISTS terms_version VARCHAR(16),
    ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE seller_profiles DROP CONSTRAINT IF EXISTS ck_seller_profiles_business_type;
ALTER TABLE seller_profiles
    ADD CONSTRAINT ck_seller_profiles_business_type
        CHECK (business_type IS NULL OR business_type IN ('INDIVIDUAL', 'HOUSEHOLD', 'COMPANY'));
