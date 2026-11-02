-- UC-17: an Admin hides a review with a reason. Nullable because reviews hidden before this have none.
ALTER TABLE product_reviews ADD COLUMN hidden_reason VARCHAR(500);
