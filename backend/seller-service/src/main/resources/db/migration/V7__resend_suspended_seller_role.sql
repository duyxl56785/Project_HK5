-- Suspended Sellers now keep the SELLER role to finish open orders, but earlier suspensions revoked it at Auth.
-- Auth ignores a revision it already applied, so each one takes a new version for the grant.
UPDATE seller_profiles SET version = version + 1 WHERE status = 'SUSPENDED';

INSERT INTO seller_outbox (id, kind, seller_id, payload, created_at)
SELECT gen_random_uuid(), 'ROLE_SYNC', id,
       json_build_object('accountId', owner_reference, 'granted', true, 'revision', version)::text, now()
FROM seller_profiles
WHERE status = 'SUSPENDED';
