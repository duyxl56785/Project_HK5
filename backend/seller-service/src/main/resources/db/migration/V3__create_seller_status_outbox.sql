CREATE TABLE seller_status_outbox (
    event_id UUID PRIMARY KEY,
    seller_id UUID NOT NULL,
    owner_reference VARCHAR(128) COLLATE "C" NOT NULL,
    seller_status VARCHAR(16) NOT NULL,
    source_version BIGINT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uk_seller_status_outbox_version UNIQUE (seller_id, source_version),
    CONSTRAINT seller_status_outbox_owner_not_blank CHECK (owner_reference ~ '[^[:space:]]'),
    CONSTRAINT seller_status_outbox_version_nonnegative CHECK (source_version >= 0),
    CONSTRAINT seller_status_outbox_status_valid CHECK (
        seller_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED')
    )
);

-- Local snapshots only: no Auth wire, role grant, publisher or delivery ACK is activated.
INSERT INTO seller_status_outbox(event_id, seller_id, owner_reference, seller_status, source_version)
SELECT gen_random_uuid(), id, owner_reference, status, version FROM seller_profiles;
