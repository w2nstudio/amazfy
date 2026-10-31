-- Channel module: a seller account's permission to read Google Ads (requirement A4). One per seller account; connecting
-- again replaces it. The refresh token is encrypted by the application (common/crypto/SecretCipher), so a database leak
-- alone doesn't expose it. Disconnecting deletes the row.
CREATE TABLE google_ads_connections (
    account_id              uuid        PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    encrypted_refresh_token text        NOT NULL,
    connected_at            timestamptz NOT NULL
);
