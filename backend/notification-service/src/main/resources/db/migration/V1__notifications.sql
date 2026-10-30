-- In-app notifications (P1). recipient is the Auth account id (JWT sub); event_key de-duplicates redelivery.
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient VARCHAR(128) COLLATE "C" NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    link VARCHAR(300),
    event_key VARCHAR(200) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX notifications_recipient_idx ON notifications(recipient, created_at DESC);
