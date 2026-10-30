-- Apply after Product migration ordering is accepted; separate callback and writer logins.
GRANT USAGE ON SCHEMA public TO product_terminal_reader, product_reservation_writer;
GRANT SELECT ON authority_write_operations, authority_write_grants TO product_terminal_reader, product_reservation_writer;
GRANT INSERT(request_id,binding_hash,state) ON authority_write_operations TO product_reservation_writer;
GRANT UPDATE(state) ON authority_write_operations TO product_reservation_writer;
GRANT INSERT(request_id,participant,authority_epoch) ON authority_write_grants TO product_reservation_writer;
GRANT USAGE,SELECT ON SEQUENCE authority_write_operations_epoch_seq TO product_reservation_writer;
