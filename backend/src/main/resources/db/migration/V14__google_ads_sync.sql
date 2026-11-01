-- What a sync of campaigns, ad groups and ads brings in beyond V9 (requirement A6).
-- channel_account_id: the channel's account a campaign came from (Google's customer id). Seller accounts can change
-- Google Ads account: campaigns from the earlier one stay, since their spend is real, and this tells them apart.
-- Empty for sample data. daily_budget_micros: empty for a budget over a custom period, or sample data.
ALTER TABLE campaigns
    ADD COLUMN channel_account_id  text,
    ADD COLUMN daily_budget_micros bigint,
    ADD COLUMN budget_currency     text,
    ADD CONSTRAINT campaigns_budget_not_negative CHECK (daily_budget_micros >= 0),
    ADD CONSTRAINT campaigns_budget_currency_code CHECK (budget_currency ~ '^[A-Z]{3}$');

-- When the chosen Google Ads account was last synced; empty until the first sync after choosing it
ALTER TABLE google_ads_connections
    ADD COLUMN synced_at timestamptz;
