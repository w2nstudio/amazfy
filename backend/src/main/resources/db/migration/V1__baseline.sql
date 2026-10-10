-- Baseline migration: proves Flyway runs against the database.
-- Real tables arrive with M1 (users, accounts, memberships) as V2, V3, ...
-- Never edit a migration after it has been merged; add a new one instead.
SELECT 1;
