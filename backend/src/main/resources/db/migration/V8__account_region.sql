-- Account module: each seller account belongs to one Amazon region, like the Amazon seller account it stands for.
-- NA (North America: US, CA) or EU (Europe: UK, DE, FR, IT, ES). Products may only use their region's marketplaces
-- (checked by CatalogService). Fixed once set: the region of an Amazon seller account never changes.

-- Accounts created before this migration become NA, then the default goes: new accounts must state their region.
ALTER TABLE accounts ADD COLUMN region text NOT NULL DEFAULT 'NA';
ALTER TABLE accounts ALTER COLUMN region DROP DEFAULT;
ALTER TABLE accounts ADD CONSTRAINT accounts_region_known CHECK (region IN ('NA', 'EU'));
