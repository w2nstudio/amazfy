-- The daily sync (requirement A9). sync_attempted_at: when the job last took this connection, so a failure waits before
-- it's tried again, and two servers never take the same one. sync_problem: why the last sync failed; empty after one
-- that worked. CONNECTION_EXPIRED waits for the seller to connect again; READ_FAILED is tried again by itself.
ALTER TABLE google_ads_connections
    ADD COLUMN sync_attempted_at timestamptz,
    ADD COLUMN sync_problem      text,
    ADD CONSTRAINT google_ads_connections_sync_problem_known
        CHECK (sync_problem IN ('CONNECTION_EXPIRED', 'READ_FAILED'));
