-- R06: check new or changed comments in UTF-16 units, matching @Size and the frontend.
-- Legacy comments over 1000 remain readable and can still be moderated without rewriting their text.
-- A NOT VALID CHECK would reject every UPDATE of a legacy row, including Admin hide/show.
CREATE FUNCTION product_review_comment_limit() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    comment_changed BOOLEAN;
BEGIN
    IF TG_OP = 'INSERT' THEN
        comment_changed := TRUE;
    ELSE
        comment_changed := NEW.comment IS DISTINCT FROM OLD.comment;
    END IF;

    IF comment_changed AND NEW.comment IS NOT NULL AND product_utf16_length(NEW.comment) > 1000 THEN
        RAISE EXCEPTION 'Review comment exceeds 1000 UTF-16 units'
            USING ERRCODE = '23514', CONSTRAINT = 'product_reviews_comment_length';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER product_reviews_comment_length
BEFORE INSERT OR UPDATE ON product_reviews
FOR EACH ROW EXECUTE FUNCTION product_review_comment_limit();
