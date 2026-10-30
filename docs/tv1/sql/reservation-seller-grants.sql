-- Apply as DB owner after Seller migrations; no authority mutation privilege.
GRANT USAGE ON SCHEMA public TO seller_reservation_rpc;
GRANT SELECT(id,owner_reference,status,version) ON seller_profiles TO seller_reservation_rpc;
GRANT SELECT(id,seller_id,status,version) ON stores TO seller_reservation_rpc;
GRANT SELECT ON authority_fence_clock, authority_write_reservations TO seller_reservation_rpc;
GRANT UPDATE(epoch) ON authority_fence_clock TO seller_reservation_rpc;
GRANT INSERT(request_id,binding_hash,epoch,state) ON authority_write_reservations TO seller_reservation_rpc;
GRANT UPDATE(state,terminal_state) ON authority_write_reservations TO seller_reservation_rpc;
GRANT SELECT,INSERT ON authority_reservation_closures TO seller_reservation_rpc;
GRANT SELECT(state) ON authority_pending_revocations TO seller_reservation_rpc;
