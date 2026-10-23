-- Catalog module: the seller's brands (registered in Amazon Brand Registry). Products follow in the next migration.
-- The first account-owned table: account_id is filled and filtered by Hibernate (@TenantId), never by hand.
CREATE TABLE brands (
    id         uuid        PRIMARY KEY DEFAULT uuidv7(),
    account_id uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    name       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT brands_name_length CHECK (length(trim(name)) BETWEEN 1 AND 100)
);
-- One brand name per account, ignoring upper/lower case ("AquaLife" and "aqualife" are the same brand).
-- Also serves "the account's brands" lookups, since account_id comes first.
CREATE UNIQUE INDEX brands_account_name_unique ON brands (account_id, lower(name));
