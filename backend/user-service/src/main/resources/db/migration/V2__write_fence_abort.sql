-- Tombstones for writes Auth stopped after revoking authority, see WriteFence.
CREATE TABLE write_fence_abort (
    operation_id uuid PRIMARY KEY,
    aborted_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT ON write_fence_abort TO user_app;
