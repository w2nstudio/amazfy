-- Reporting module: totals added up in advance (rollups), so reports read far fewer rows than the daily tables hold.
-- A large seller has 5,000 ads × 366 days = 1.83 million daily rows a year per table; adding them up on every request
-- took 0.5–1.8 s (ReportPerformanceTest). The same year is ~60,000 monthly rows per ad, or 366 rows per account.
--
-- Derived data only: built from ad_spend_daily and attribution_daily by RefreshReportTotals, never edited directly.
-- Whatever writes those tables, or deletes ads, refreshes the affected period in the same transaction (see CLAUDE.md).

-- Spend per ad and calendar month (month = its first day). Deleting an ad deletes its daily spend, so its months too.
CREATE TABLE ad_spend_monthly (
    account_id  uuid   NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    ad_id       uuid   NOT NULL,
    month       date   NOT NULL,
    currency    text   NOT NULL,
    impressions bigint NOT NULL,
    clicks      bigint NOT NULL,
    cost_micros bigint NOT NULL,
    PRIMARY KEY (ad_id, month, currency),
    CONSTRAINT ad_spend_monthly_ad_same_account FOREIGN KEY (ad_id, account_id) REFERENCES ads (id, account_id) ON DELETE CASCADE,
    CONSTRAINT ad_spend_monthly_first_day CHECK (extract(day FROM month) = 1)
);
CREATE INDEX ad_spend_monthly_account_month ON ad_spend_monthly (account_id, month);

-- Attribution numbers per matched ad (or unmatched: ad_id empty) and calendar month. No foreign key on ad_id: when an
-- ad is deleted, its daily rows become unmatched, which only a refresh can mirror here (it merges them into the
-- unmatched totals).
CREATE TABLE attribution_monthly (
    account_id        uuid           NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    ad_id             uuid,
    month             date           NOT NULL,
    marketplace       text           NOT NULL,
    click_throughs    bigint         NOT NULL,
    detail_page_views bigint         NOT NULL,
    add_to_carts      bigint         NOT NULL,
    purchases         bigint         NOT NULL,
    units_sold        bigint         NOT NULL,
    sales             numeric(16, 2) NOT NULL,
    CONSTRAINT attribution_monthly_first_day CHECK (extract(day FROM month) = 1)
);
CREATE UNIQUE INDEX attribution_monthly_unique ON attribution_monthly (account_id, ad_id, month, marketplace) NULLS NOT DISTINCT;
CREATE INDEX attribution_monthly_account_month ON attribution_monthly (account_id, month);

-- The whole account's spend per day: the summary and the trend chart without a drill-down filter.
CREATE TABLE account_spend_daily (
    account_id  uuid   NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    date        date   NOT NULL,
    currency    text   NOT NULL,
    impressions bigint NOT NULL,
    clicks      bigint NOT NULL,
    cost_micros bigint NOT NULL,
    PRIMARY KEY (account_id, date, currency)
);

-- The whole account's Attribution numbers per day, unmatched ones included.
CREATE TABLE account_attribution_daily (
    account_id        uuid           NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    date              date           NOT NULL,
    marketplace       text           NOT NULL,
    click_throughs    bigint         NOT NULL,
    detail_page_views bigint         NOT NULL,
    add_to_carts      bigint         NOT NULL,
    purchases         bigint         NOT NULL,
    units_sold        bigint         NOT NULL,
    sales             numeric(16, 2) NOT NULL,
    PRIMARY KEY (account_id, date, marketplace)
);
