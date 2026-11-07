-- TV4 #156: V7 may already exist in a shared Flyway history, so strengthen its
-- invariant in a new migration rather than rewriting V7.  A physical receipt
-- attestation is meaningful only when it contains a non-whitespace character.
ALTER TABLE return_requests DROP CONSTRAINT ck_return_receipt_reference;
ALTER TABLE return_requests ADD CONSTRAINT ck_return_receipt_reference
    CHECK (
        (status = 'RECEIVED' AND receipt_reference IS NOT NULL
            AND receipt_reference ~ '[^[:space:]]')
        OR (status <> 'RECEIVED' AND receipt_reference IS NULL)
    );
