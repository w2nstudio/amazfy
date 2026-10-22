# Business glossary: Amazon and Google Ads

Plain-language explanations of business terms as they come up while building Amazfy. New terms are added at the end.

**Amazon seller account** — A business's account in Amazon Seller Central, where it lists and sells products. Amazon gives one per region (North America, Europe, …). In Amazfy, one Amazfy account = one Amazon seller account.

**Brand / Brand Registry** — A brand is a registered trademark a seller sells under (e.g. "AquaLife"). Brand Registry is Amazon's program that confirms the seller owns the brand. One seller account can have several brands. Amazon Attribution is only available to Brand Registry sellers.

**ASIN** — Amazon Standard Identification Number: Amazon's 10-character product ID, starting with "B0" (e.g. B0AAA11111). Each variation (size, colour) has its own ASIN. The same product usually keeps its ASIN across marketplaces, but not always, so Amazfy identifies a product by ASIN + marketplace.

**Marketplace** — An Amazon country site: amazon.com (US), amazon.co.uk (UK), amazon.de (DE), …

**Brand Store** — A brand's mini-website inside Amazon (amazon.com/stores/…) showing all its products. An ad linking there can't be credited to one product, so Amazfy reports it as brand-level spend.

**Amazon Attribution** — Amazon's free measurement tool that shows which sales on Amazon came from ads outside Amazon (Google, Meta, …). It works by adding a tracking tag to the ad's link.

**Attribution** — Crediting a sale to the ad that caused it. A shopper clicks a Google ad, then buys on Amazon; Amazon "attributes" that sale to the ad.

**Attribution window (14 days)** — How long after a click a purchase still counts for that ad. With 14 days: click on 1 March, buy on 10 March → counted; buy on 20 March → not counted. Because of this, sales for recent days keep changing for two weeks.

**Attribution tag / macro tag** — Tracking parameters Amazon generates and the seller adds to an ad's link. A macro tag contains placeholders like {campaignid} that Google fills in automatically on each click, so one tag works for every campaign.

**Attribution CSV report** — A spreadsheet file of Attribution results the seller can download from the Amazon Attribution console. Amazfy's fallback when API access isn't available.

**Google login vs Google Ads account** — A Google login is a person (john@gmail.com). A Google Ads account (ID like 123-456-7890) holds the ads, the payment card, the currency and the time zone. One login can manage several Google Ads accounts.

**Campaign** — A container of ads in Google Ads that holds the daily budget and targeting (countries, languages).

**Ad group** — A group inside a campaign that holds keywords. All ads in an ad group share its keywords.

**Keyword** — A search term that can trigger an ad, e.g. "hydrogen water bottle".

**Ad / responsive search ad** — What the shopper sees. A responsive search ad has up to 15 headlines (30 characters) and 4 descriptions (90 characters); Google mixes them automatically. Up to 3 active per ad group.

**Final URL** — The page a click on an ad leads to. For Amazfy sellers, an Amazon product page or Brand Store.

**Tracking template** — A Google Ads setting that adds tracking parameters to the final URL on each click. Amazfy puts the Amazon Attribution tag here.

**Quality Score** — Google's 1–10 rating of how well an ad and its landing page match the keywords. A low score means a higher cost per click and fewer impressions.

**Manager account (MCC)** — A Google Ads account that manages other Google Ads accounts. Used by agencies, and by Amazfy for test accounts.

**Spend** — Money paid to the ad network (Google) for clicks or impressions.

**Impressions / clicks** — Impressions: how many times an ad was shown. Clicks: how many times it was clicked.

**ROAS** — Return on ad spend = sales ÷ spend. $600 spend producing $1,710 Amazon sales → ROAS 2.85 (each $1 of ads brought $2.85 of sales).

**CPA** — Cost per acquisition = spend ÷ purchases. $600 spend, 50 orders → CPA $12.

**Conversion rate** — Purchases ÷ clicks × 100. 1,000 clicks, 35 orders → 3.5%.

**Product (listing)** — In Amazfy, one Amazon listing in one marketplace: ASIN + marketplace + a name the seller recognises, always under one brand. The same ASIN sold on amazon.com and amazon.co.uk is two products, because sales, prices and currency differ per marketplace. Example: `B0AAA11111` in US, "Aquarium filter 40L", brand AquaLife.

**Variation** — A version of a product that differs in size, colour or similar (e.g. filter 40L and filter 80L). On Amazon each variation has its own ASIN, so in Amazfy each variation the seller advertises is its own product.
