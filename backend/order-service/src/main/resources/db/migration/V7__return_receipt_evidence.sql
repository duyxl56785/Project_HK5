-- TV4 #156: seller attests physical receipt before a Return can become RECEIVED.
-- This is an audit reference only.  It deliberately does not call Inventory INCREASE: the
-- Order->Inventory credential allowlist excludes INCREASE until a separate restock contract is approved.
ALTER TABLE return_requests ADD COLUMN receipt_reference varchar(128);
ALTER TABLE return_requests ADD CONSTRAINT ck_return_receipt_reference
    CHECK ((status = 'RECEIVED') = (receipt_reference IS NOT NULL));
