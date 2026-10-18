CREATE TABLE seller_profiles (
    id UUID PRIMARY KEY,
    owner_reference VARCHAR(128) COLLATE "C" NOT NULL,
    status VARCHAR(16) NOT NULL,
    review_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_seller_profiles_owner_reference UNIQUE (owner_reference),
    CONSTRAINT seller_profiles_owner_not_blank CHECK (owner_reference ~ '[^[:space:]]'),
    CONSTRAINT seller_profiles_status_valid CHECK (
        status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED')
    ),
    CONSTRAINT seller_profiles_reason_matches_status CHECK (
        (status IN ('REJECTED', 'SUSPENDED') AND review_reason IS NOT NULL
            AND review_reason ~ '[^[:space:]]')
        OR (status IN ('PENDING_REVIEW', 'APPROVED') AND review_reason IS NULL)
    ),
    CONSTRAINT seller_profiles_version_nonnegative CHECK (version >= 0)
);
