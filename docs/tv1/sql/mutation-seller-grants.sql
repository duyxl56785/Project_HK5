-- Provision a NOLOGIN seller_mutation_worker group and a separate worker login first.
-- This profile is not runtime activation or an Auth commit-proof adapter.
GRANT USAGE ON SCHEMA public TO seller_mutation_worker;
GRANT SELECT,INSERT,UPDATE,DELETE ON seller_profiles,stores TO seller_mutation_worker;
GRANT SELECT,INSERT ON seller_status_outbox,seller_mutation_receipts TO seller_mutation_worker;
GRANT SELECT,INSERT,UPDATE ON authority_pending_revocations TO seller_mutation_worker;
GRANT SELECT ON authority_write_reservations,authority_fence_clock TO seller_mutation_worker;
GRANT EXECUTE ON FUNCTION authority_fence_enter_mutation() TO seller_mutation_worker;
