-- Builds the report totals (V11) for numbers written before V11 existed, so reports show them after the update.
-- From then on, whatever writes the daily tables keeps the totals up to date through RefreshReportTotals.
-- Same statements as ReportTotalsSql.rebuild, for every account at once; the totals tables are emptied first, so the
-- result is exactly the totals of the daily rows.

TRUNCATE ad_spend_monthly, attribution_monthly, account_spend_daily, account_attribution_daily;

INSERT INTO ad_spend_monthly (account_id, ad_id, month, currency, impressions, clicks, cost_micros)
SELECT account_id, ad_id, date_trunc('month', date::timestamp)::date, currency, sum(impressions), sum(clicks), sum(cost_micros)
FROM ad_spend_daily
GROUP BY account_id, ad_id, date_trunc('month', date::timestamp)::date, currency;

INSERT INTO attribution_monthly (account_id, ad_id, month, marketplace, click_throughs, detail_page_views, add_to_carts,
                                 purchases, units_sold, sales)
SELECT account_id, ad_id, date_trunc('month', date::timestamp)::date, marketplace, sum(click_throughs),
       sum(detail_page_views), sum(add_to_carts), sum(purchases), sum(units_sold), sum(sales)
FROM attribution_daily
GROUP BY account_id, ad_id, date_trunc('month', date::timestamp)::date, marketplace;

INSERT INTO account_spend_daily (account_id, date, currency, impressions, clicks, cost_micros)
SELECT account_id, date, currency, sum(impressions), sum(clicks), sum(cost_micros)
FROM ad_spend_daily
GROUP BY account_id, date, currency;

INSERT INTO account_attribution_daily (account_id, date, marketplace, click_throughs, detail_page_views, add_to_carts,
                                       purchases, units_sold, sales)
SELECT account_id, date, marketplace, sum(click_throughs), sum(detail_page_views), sum(add_to_carts), sum(purchases),
       sum(units_sold), sum(sales)
FROM attribution_daily
GROUP BY account_id, date, marketplace;
