CREATE TABLE authority_reservation_closures (
    request_id UUID PRIMARY KEY,
    binding_hash TEXT NOT NULL CHECK (binding_hash ~ '^[0-9a-f]{64}$'),
    operation_epoch BIGINT NOT NULL CHECK (operation_epoch>0),
    terminal_state TEXT NOT NULL CHECK (terminal_state IN ('COMMITTED','ABORTED')),
    closed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE FUNCTION authority_reservation_closure_immutable() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='55000', MESSAGE='Reservation closure is immutable';
END $$;
CREATE TRIGGER authority_reservation_closures_immutable BEFORE UPDATE OR DELETE ON authority_reservation_closures FOR EACH ROW EXECUTE FUNCTION authority_reservation_closure_immutable();
CREATE TRIGGER authority_reservation_closures_no_truncate BEFORE TRUNCATE ON authority_reservation_closures FOR EACH STATEMENT EXECUTE FUNCTION authority_reservation_closure_immutable();
GRANT SELECT, INSERT ON authority_reservation_closures TO auth_app;
