-- R06: a Review comment is at most 1000 characters, counted in UTF-16 units like the API (@Size) and the frontend.
-- NOT VALID keeps comments written before this limit; every new or changed row is checked.
ALTER TABLE product_reviews ADD CONSTRAINT product_reviews_comment_length
    CHECK (product_utf16_length(comment) <= 1000) NOT VALID;
