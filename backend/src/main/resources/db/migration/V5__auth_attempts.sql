-- Auth module: recent attempts, for brute-force limits (failed logins, reset and verification emails).
-- key_hash = SHA-256 of "<limit name>:<subject>", e.g. "login-email:john@aqualife.com" or "login-ip:203.0.113.7",
-- so emails and IP addresses (personal data) are never stored in plain text.
-- Rows older than a day are deleted by an hourly job; no limit looks further back.
CREATE TABLE auth_attempts (
    id           uuid        PRIMARY KEY DEFAULT uuidv7(),
    key_hash     text        NOT NULL,
    attempted_at timestamptz NOT NULL
);
-- "How many attempts for this key since ...?" (newest first)
CREATE INDEX auth_attempts_key_hash_attempted_at ON auth_attempts (key_hash, attempted_at DESC);
-- The clean-up job deletes by age
CREATE INDEX auth_attempts_attempted_at ON auth_attempts (attempted_at);
