-- Catalog module: products, i.e. Amazon listings, each identified by ASIN + marketplace and belonging to one brand.
-- account_id is filled and filtered by Hibernate (@TenantId), like brands.

-- Lets products reference (brand, account) pairs below; id alone is already unique, so this costs nothing logically.
ALTER TABLE brands ADD CONSTRAINT brands_id_account_unique UNIQUE (id, account_id);

CREATE TABLE products (
    id          uuid        PRIMARY KEY DEFAULT uuidv7(),
    account_id  uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    brand_id    uuid        NOT NULL,
    asin        text        NOT NULL,                       -- stored upper-case, e.g. B0AAA11111
    marketplace text        NOT NULL,                       -- Amazon's code: US, CA, UK, DE, FR, IT, ES
    name        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    -- The brand must belong to the same account: a plain brand_id reference would accept another seller's brand.
    -- RESTRICT: a brand with products can't be deleted (the API answers 409 before it gets here).
    CONSTRAINT products_brand_same_account FOREIGN KEY (brand_id, account_id) REFERENCES brands (id, account_id) ON DELETE RESTRICT,
    CONSTRAINT products_asin_format CHECK (asin ~ '^B0[0-9A-Z]{8}$'),
    CONSTRAINT products_marketplace_known CHECK (marketplace IN ('US', 'CA', 'UK', 'DE', 'FR', 'IT', 'ES')),
    CONSTRAINT products_name_length CHECK (length(trim(name)) BETWEEN 1 AND 200)
);
-- One product per listing in an account: the same ASIN may exist once per marketplace
CREATE UNIQUE INDEX products_account_asin_marketplace_unique ON products (account_id, asin, marketplace);
-- "This brand's products" and the brand product counts
CREATE INDEX products_account_brand ON products (account_id, brand_id);
