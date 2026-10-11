-- Auth module: people, how they log in, and their refresh tokens.
-- IDs use PostgreSQL 18's uuidv7(): unique like any UUID, but time-ordered, so new rows append to indexes.

-- A person who logs in. Never called "account" (see CLAUDE.md, Domain naming).
CREATE TABLE users (
    id                uuid        PRIMARY KEY DEFAULT uuidv7(),
    email             text        NOT NULL,                 -- stored lower-case by the application
    email_verified_at timestamptz,                          -- NULL until the user clicks the verification link
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_email_not_blank CHECK (length(trim(email)) > 0)
);
-- One user per email, ignoring upper/lower case
CREATE UNIQUE INDEX users_email_unique ON users (lower(email));

-- How a user logs in. A user can have several: password, google, facebook (later), or a managed provider.
-- password: subject = the user's id, password_hash = bcrypt hash
-- google/facebook: subject = the provider's stable user id ("sub"), password_hash = NULL
CREATE TABLE user_identities (
    id            uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider      text        NOT NULL,
    subject       text        NOT NULL,
    password_hash text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_identities_provider_known CHECK (provider IN ('password', 'google', 'facebook')),
    CONSTRAINT user_identities_password_has_hash CHECK ((provider = 'password') = (password_hash IS NOT NULL)),
    CONSTRAINT user_identities_provider_subject_unique UNIQUE (provider, subject),
    CONSTRAINT user_identities_one_per_provider UNIQUE (user_id, provider)
);

-- Long-lived refresh tokens. Only a SHA-256 hash is stored, never the token itself.
-- Rotation: each use revokes the token and issues a new one in the same family.
-- Reuse of a revoked token means it was stolen: the whole family is revoked.
CREATE TABLE refresh_tokens (
    id          uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   uuid        NOT NULL,                       -- shared by a token and all its rotated successors
    token_hash  text        NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,                                -- set on rotation, logout or reuse detection
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_id ON refresh_tokens (family_id);
