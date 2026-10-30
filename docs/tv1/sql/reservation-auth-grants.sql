-- Apply as DB owner after Auth migrations; create this NOLOGIN group in provisioning.
GRANT USAGE ON SCHEMA public TO auth_reservation_rpc;
GRANT SELECT(id,status) ON accounts TO auth_reservation_rpc;
GRANT SELECT(account_id,role) ON account_role_grants TO auth_reservation_rpc;
GRANT SELECT ON authority_fence_clock, authority_write_reservations TO auth_reservation_rpc;
GRANT UPDATE(epoch) ON authority_fence_clock TO auth_reservation_rpc;
GRANT INSERT(request_id,binding_hash,epoch,state) ON authority_write_reservations TO auth_reservation_rpc;
GRANT UPDATE(state,terminal_state) ON authority_write_reservations TO auth_reservation_rpc;
