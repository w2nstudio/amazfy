-- Channel module: the seller's ads on advertising channels (Google Ads in Phase 1) and their daily spend.
-- Structure as on Google: campaign → ad group → ad. Rows come from the channel (sync in M4, sample data before),
-- so each keeps the channel's own id (external_id). account_id is filled and filtered by Hibernate (@TenantId).
-- Child tables reference (id, account_id) pairs, so a row can never point into another seller account.

-- Lets ads reference (product, account) pairs; id alone is already unique, so this costs nothing logically
ALTER TABLE products ADD CONSTRAINT products_id_account_unique UNIQUE (id, account_id);

CREATE TABLE campaigns (
    id          uuid        PRIMARY KEY DEFAULT uuidv7(),
    account_id  uuid        NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    channel     text        NOT NULL,                   -- GOOGLE; META, TIKTOK later
    external_id text        NOT NULL,                   -- the channel's id, e.g. Google's campaign id "21345678901"
    name        text        NOT NULL,
    status      text        NOT NULL,                   -- as on the channel: ENABLED, PAUSED or REMOVED
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT campaigns_id_account_unique UNIQUE (id, account_id),
    CONSTRAINT campaigns_channel_known CHECK (channel IN ('GOOGLE')),
    CONSTRAINT campaigns_status_known CHECK (status IN ('ENABLED', 'PAUSED', 'REMOVED'))
);
-- A sync finds "its" campaign again by the channel's id
CREATE UNIQUE INDEX campaigns_account_channel_external_unique ON campaigns (account_id, channel, external_id);

CREATE TABLE ad_groups (
    id          uuid        PRIMARY KEY DEFAULT uuidv7(),
    account_id  uuid        NOT NULL,
    campaign_id uuid        NOT NULL,
    external_id text        NOT NULL,
    name        text        NOT NULL,
    status      text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ad_groups_id_account_unique UNIQUE (id, account_id),
    CONSTRAINT ad_groups_campaign_same_account FOREIGN KEY (campaign_id, account_id)
        REFERENCES campaigns (id, account_id) ON DELETE CASCADE,
    CONSTRAINT ad_groups_status_known CHECK (status IN ('ENABLED', 'PAUSED', 'REMOVED'))
);
CREATE UNIQUE INDEX ad_groups_campaign_external_unique ON ad_groups (campaign_id, external_id);

-- The ad is the unit of linking: its final URL decides what it advertises (see requirements, section 4).
-- product_id: the link is a product page (amazon.com/dp/<ASIN>); brand_id: a Brand Store link, linked to a brand by
-- the seller (brand-level spend); both empty: not matched yet. Removing the product or brand makes the ad unmatched
-- again (SET NULL on that column only) instead of deleting the ad and its spend history.
CREATE TABLE ads (
    id          uuid        PRIMARY KEY DEFAULT uuidv7(),
    account_id  uuid        NOT NULL,
    ad_group_id uuid        NOT NULL,
    external_id text        NOT NULL,
    final_url   text        NOT NULL,                   -- where the ad sends people, e.g. https://www.amazon.com/dp/B0AAA11111
    status      text        NOT NULL,
    product_id  uuid,
    brand_id    uuid,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ads_id_account_unique UNIQUE (id, account_id),
    CONSTRAINT ads_ad_group_same_account FOREIGN KEY (ad_group_id, account_id)
        REFERENCES ad_groups (id, account_id) ON DELETE CASCADE,
    CONSTRAINT ads_product_same_account FOREIGN KEY (product_id, account_id)
        REFERENCES products (id, account_id) ON DELETE SET NULL (product_id),
    CONSTRAINT ads_brand_same_account FOREIGN KEY (brand_id, account_id)
        REFERENCES brands (id, account_id) ON DELETE SET NULL (brand_id),
    -- A product already belongs to a brand; brand_id is only for ads without a single product
    CONSTRAINT ads_product_or_brand CHECK (product_id IS NULL OR brand_id IS NULL),
    CONSTRAINT ads_status_known CHECK (status IN ('ENABLED', 'PAUSED', 'REMOVED'))
);
CREATE UNIQUE INDEX ads_ad_group_external_unique ON ads (ad_group_id, external_id);
-- "This product's ads" and "this brand's ads" in the reports
CREATE INDEX ads_account_product ON ads (account_id, product_id);
CREATE INDEX ads_account_brand ON ads (account_id, brand_id);

-- What each ad cost per day, as the channel reports it. One row per ad and day: a re-sync updates it (upsert).
-- Money in micros (1 USD = 1 000 000), as Google sends it: whole numbers, no rounding. Currency: the Google Ads
-- account's, which may differ from the marketplace's; amounts in different currencies are never added up.
CREATE TABLE ad_spend_daily (
    account_id  uuid        NOT NULL,
    ad_id       uuid        NOT NULL,
    date        date        NOT NULL,
    impressions bigint      NOT NULL,
    clicks      bigint      NOT NULL,
    cost_micros bigint      NOT NULL,
    currency    text        NOT NULL,                   -- ISO code, e.g. USD
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (ad_id, date),
    CONSTRAINT ad_spend_daily_ad_same_account FOREIGN KEY (ad_id, account_id)
        REFERENCES ads (id, account_id) ON DELETE CASCADE,
    CONSTRAINT ad_spend_daily_not_negative CHECK (impressions >= 0 AND clicks >= 0 AND cost_micros >= 0),
    CONSTRAINT ad_spend_daily_currency_code CHECK (currency ~ '^[A-Z]{3}$')
);
-- Reports read one account's days in a date range
CREATE INDEX ad_spend_daily_account_date ON ad_spend_daily (account_id, date);
