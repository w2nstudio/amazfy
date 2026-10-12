-- Auth module: one-time tokens sent by email, for "verify your email" and "reset your password" links.
-- Like refresh tokens, only a SHA-256 hash is stored, so a database leak doesn't expose usable links.
-- A token works once (used_at is set) and only until expires_at.
CREATE TABLE one_time_tokens (
    id         uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    text        NOT NULL,
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,                                -- NULL until the link is clicked; also set when a newer token replaces it
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT one_time_tokens_purpose_known CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);
-- "Invalidate this user's open tokens of one purpose" when a new one is sent or the password changes
CREATE INDEX one_time_tokens_user_id_purpose ON one_time_tokens (user_id, purpose);
