-- Attribution module: what Amazon Attribution reports happened on Amazon after clicks on the seller's tagged ads.
-- One row per day and per Attribution campaign / ad group / creative in one marketplace, from the CSV import (M3)
-- or Amazon's API (later). Columns follow the report's documented "promoted product" numbers, counted by Amazon
-- within its 14-day window; brand halo and new-to-brand numbers can be added as columns when the dashboard needs them.
-- account_id is filled and filtered by Hibernate (@TenantId).

CREATE TABLE attribution_daily (
    id                   uuid        PRIMARY KEY DEFAULT uuidv7(),
    account_id           uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    source               text        NOT NULL,          -- CSV or API: where the row came from last
    marketplace          text        NOT NULL,          -- Attribution reports per marketplace; sales are in its currency
    date                 date        NOT NULL,
    -- The row's identifiers exactly as Attribution reports them. With Amazon's Google macro tag these are Google's
    -- campaign, ad group and ad ids, which is how a row is matched to an ad. creative_external_id is empty when a
    -- report only goes down to ad groups (open risk in the requirements, section 4).
    publisher            text        NOT NULL,          -- e.g. "Google Ads"
    campaign_external_id text        NOT NULL,
    ad_group_external_id text        NOT NULL,
    creative_external_id text,
    -- The matched Google ad; empty while unmatched. Unmatched rows are kept and shown to the seller. Removing the ad
    -- makes the row unmatched again (SET NULL on ad_id only) instead of losing Amazon's numbers.
    ad_id                uuid,
    click_throughs       bigint      NOT NULL,          -- clicks on the tagged link that reached Amazon
    detail_page_views    bigint      NOT NULL,
    add_to_carts         bigint      NOT NULL,
    purchases            bigint      NOT NULL,          -- orders
    units_sold           bigint      NOT NULL,
    sales                numeric(14, 2) NOT NULL,       -- in the marketplace's currency, e.g. USD for US
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT attribution_daily_ad_same_account FOREIGN KEY (ad_id, account_id)
        REFERENCES ads (id, account_id) ON DELETE SET NULL (ad_id),
    CONSTRAINT attribution_daily_source_known CHECK (source IN ('CSV', 'API')),
    CONSTRAINT attribution_daily_marketplace_known CHECK (marketplace IN ('US', 'CA', 'UK', 'DE', 'FR', 'IT', 'ES')),
    CONSTRAINT attribution_daily_not_negative CHECK (
        click_throughs >= 0 AND detail_page_views >= 0 AND add_to_carts >= 0
        AND purchases >= 0 AND units_sold >= 0 AND sales >= 0
    )
);
-- One row per reported line and day: a re-import or re-sync updates it (upsert), never duplicates it.
-- NULLS NOT DISTINCT: two ad-group-level rows (no creative) for the same day count as the same row.
CREATE UNIQUE INDEX attribution_daily_reported_line_unique ON attribution_daily
    (account_id, marketplace, publisher, campaign_external_id, ad_group_external_id, creative_external_id, date)
    NULLS NOT DISTINCT;
-- Reports read one account's days in a date range; matching looks rows up by ad
CREATE INDEX attribution_daily_account_date ON attribution_daily (account_id, date);
CREATE INDEX attribution_daily_ad ON attribution_daily (ad_id);
