-- Account module: seller accounts and who can access them.

-- One seller business = exactly one Amazon seller account (see CLAUDE.md, Domain naming).
-- Every account-owned table added later carries account_id referencing this table.
CREATE TABLE accounts (
    id         uuid        PRIMARY KEY DEFAULT uuidv7(),
    name       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT accounts_name_length CHECK (length(trim(name)) BETWEEN 1 AND 100)
);

-- Links users and accounts (many-to-many) with a role. Phase 1 creates OWNER only.
CREATE TABLE memberships (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    role       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, account_id),
    CONSTRAINT memberships_role_known CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'))
);
-- "Who has access to this account?" (the primary key already covers "which accounts does this user have?")
CREATE INDEX memberships_account_id ON memberships (account_id);
