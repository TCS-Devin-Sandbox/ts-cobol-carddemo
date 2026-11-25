CREATE TABLE pending_auth_unload_frame (
    frame_number BIGINT PRIMARY KEY,
    account_id NUMERIC(11,0),
    auth_date_inv NUMERIC(5,0),
    auth_time_inv NUMERIC(9,0),
    frame_bytes BYTEA NOT NULL,
    CHECK (
        (account_id IS NULL AND auth_date_inv IS NULL AND auth_time_inv IS NULL
            AND octet_length(frame_bytes) IN (88, 140))
        OR (account_id IS NOT NULL AND
            (auth_date_inv IS NULL) = (auth_time_inv IS NULL)
            AND octet_length(frame_bytes) = 36)
    )
);
