-- Provision these NOLOGIN groups first; attach separate worker/operator logins.
GRANT USAGE ON SCHEMA public TO product_recovery_worker,product_recovery_operator;
GRANT SELECT ON authority_write_operations,authority_write_grants,product_reservation_recovery TO product_recovery_worker;
GRANT UPDATE(state) ON authority_write_operations TO product_recovery_worker;
GRANT UPDATE(attempts,state,token,lease_until,due_at,last_code) ON product_reservation_recovery TO product_recovery_worker;
GRANT SELECT ON authority_write_operations,product_reservation_recovery TO product_recovery_operator;
GRANT INSERT(request_id,binding_hash,operation_epoch,intent,due_at) ON product_reservation_recovery TO product_recovery_operator;
GRANT UPDATE(attempts,state,token,lease_until,due_at,last_code) ON product_reservation_recovery TO product_recovery_operator;
GRANT INSERT(request_id,operation_epoch,actor,action) ON product_reservation_recovery_audit TO product_recovery_operator;
GRANT USAGE,SELECT ON SEQUENCE product_reservation_recovery_audit_id_seq TO product_recovery_operator;
