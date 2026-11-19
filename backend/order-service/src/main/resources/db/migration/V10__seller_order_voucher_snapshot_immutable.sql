-- MD21: preserve the checkout voucher receipt, including its absence, after INSERT.
CREATE FUNCTION protect_seller_order_voucher_snapshot() RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.voucher_snapshot IS DISTINCT FROM OLD.voucher_snapshot THEN
        RAISE EXCEPTION 'SellerOrder voucher snapshot is immutable'
            USING ERRCODE = '23514', CONSTRAINT = 'seller_order_voucher_snapshot_immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER seller_order_voucher_snapshot_immutable
BEFORE UPDATE ON seller_orders
FOR EACH ROW EXECUTE FUNCTION protect_seller_order_voucher_snapshot();
