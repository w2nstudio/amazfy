# Business glossary: Amazon and Google Ads

Plain-language explanations of business terms as they come up while building Amazfy. New terms are added at the end.

**Amazon seller account** — A business's account in Amazon Seller Central, where it lists and sells products. Amazon gives one per region (North America, Europe, …). In Amazfy it is a **seller account** (in the code: `Account`): one seller account in Amazfy = one Amazon seller account. The person who logs in is a **user**, not an account.

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

**Detail page** — Amazon's page for one listing, where shoppers see the photos, price and "Add to Cart". Its short address is the marketplace's site + `/dp/` + the ASIN, e.g. `https://www.amazon.com/dp/B0AAA11111` ("dp" stands for detail page). Amazfy shows this link for each product; later, ads send shoppers to this page (with an Attribution tag added).

**Region** — A group of Amazon marketplaces that one Amazon seller account covers. Amazfy supports two: **North America** (`NA`: amazon.com, amazon.ca) and **Europe** (`EU`: amazon.co.uk, .de, .fr, .it, .es). Amazon has more (e.g. Far East: Japan, Australia), not supported in Phase 1. In Amazfy each seller account has one region, chosen when it is added and fixed afterwards; its products can only be on that region's marketplaces.

**Unified account** — Amazon's name for one seller account that sells in several marketplaces of a region. Example: "AquaLife Inc. – North America" registers once and sells on amazon.com and amazon.ca, so the same ASIN can be two Amazfy products there (US and CA, with sales in USD and CAD). A seller who also sells in Europe has a second Amazon seller account, so a second seller account in Amazfy.

**Micros** — How Google Ads sends money amounts: millionths of the currency unit, as whole numbers. $12.50 is `12500000` micros. Amazfy stores spend the same way, so no amount is ever rounded; it is converted to dollars (or euros, …) only for display.

**Status (enabled / paused / removed)** — The state of a Google campaign, ad group or ad. *Enabled* runs, *paused* is stopped for now and can be restarted, *removed* is deleted on Google's side but still has its past spend. Amazfy keeps removed ones so old reports stay complete.

**Not linked to a product or brand** — A report row for Google ads Amazfy knows, but whose link it can't read, e.g. the final URL is a short link (`amzn.to/123456`) or the product isn't in Amazfy yet. Amazfy knows what the ad **cost**, but not which product it advertises. Shown at brand and product level, and clicking it opens its campaigns and ads. At campaign level there is no such row: these ads sit inside their campaign. Fixed by adding the product, or by linking the ad on the Ads page. The requirements call these *unmatched ads*.

**Not matched to an ad** — A report row for Amazon sales whose campaign, ad group and ad ids match none of the seller account's Google ads: Amazfy knows the **sale**, but not which ad brought it, so its spend is unknown (ROAS and CPA show "—"). Usual causes: the ad was removed on Google before Amazfy's first sync, Google isn't synced yet, the report only goes down to ad groups, or the report is from another seller account. Shown last when viewing the whole account, without a drill-down, since it belongs to no brand, product or ad. The next Google sync or a corrected report fixes it.

**Not linked vs not matched** — Opposite problems: *not linked* is the Google side (ad known, product unknown; has spend), *not matched* is the Amazon side (sale known, ad unknown; no spend). Both rows exist so the totals stay complete: the rows of a level add up to the dashboard tiles, and no money spent or earned disappears. Sample data has one of each kind: a short-link ad per brand ($5 a day each) and one unknown Attribution line ($40 a day). Side by side, with where each shows up and how each gets fixed: ["Not linked" and "Not matched" report rows](report-rows-not-linked-not-matched.md).

**Click-through** — In Attribution reports: one click on a tagged link that brought someone to Amazon. Usually a little lower than Google's click count for the same ad (some people leave before Amazon's page loads); conversion rate uses Amazon's number.

**Creative** — Amazon Attribution's word for an ad. In a report a *creative* row is one Google ad, a *campaign* and *ad group* row the Google campaign and ad group, identified by Google's ids when Amazon's Google macro tag is used.

**Publisher** — In Attribution: the channel the traffic came from, e.g. "Google Ads". Set when the Attribution tag is created.

**Promoted product vs brand halo** — Attribution counts purchases of the *promoted product* (the one the ad linked to) separately from *brand halo* purchases: other products of the same brand bought after the click (the shopper clicked the filter ad and bought the heater). Amazfy's numbers are the promoted product's; brand halo may be added later.

**New-to-brand** — A purchase by a shopper who hadn't bought from the brand on Amazon in the last 12 months. Attribution reports it separately; shows whether ads bring new customers. Not stored in Phase 1 yet.

**ACoS** — Advertising cost of sales = spend ÷ sales × 100, the number Amazon sellers know from Amazon's own ads. It is ROAS turned around: ROAS 4.0 = ACoS 25%. Lower ACoS is better; higher ROAS is better.

**Break-even ROAS** — The ROAS at which ads pay for themselves: 1 ÷ profit margin. With a 30% margin (a $40 filter leaves $12 after product cost, Amazon fees and shipping), break-even is 1 ÷ 0.30 ≈ 3.33: ROAS 4.0 earns money, ROAS 2.5 loses money even though each $1 brings $2.50 in sales. Amazfy doesn't know margins in Phase 1, so it shows ROAS; the seller judges it against their margin.

**Drill-down** — Going from a total to its parts: all brands → one brand's products → one product's campaigns → ad groups → ads. Amazfy's reports work this way (requirement A12), so the seller sees where money works and where it doesn't. Budgets are set per campaign on Google, so the campaign level is where "spend more / spend less" is decided; the ad level shows which ad text works.


**Ad testing (A/B testing)** — Running two or more ads with different text in the same ad group, for the same product, to find which words sell better. Google rotates them and favours the one with more clicks; Amazfy shows which one brings more Amazon *sales*. Example: ad A and ad B both spend $20 a day, A sells twice a day (ROAS 4.00), B once (ROAS 2.00): the seller pauses or rewrites B.

**Match type** — How closely a search must match a keyword for the ad to show. **Exact** (`[aquarium filter 40l]`): searches with the same meaning, e.g. "40 litre aquarium filter"; fewer clicks, higher conversion rate. **Phrase** (`"aquarium filter"`): searches that include that meaning, e.g. "silent aquarium filter for 40l". **Broad** (`aquarium filter`): related searches too, e.g. "best filter for small fish tank"; many more clicks, lower conversion rate, finds new searches. Sellers often keep exact and broad keywords in separate ad groups to compare them.

**Search terms report** — Google's list of what people actually typed before an ad showed or was clicked. Shows which searches a broad keyword really matched: good ones become exact keywords, wasteful ones negative keywords.

**Negative keyword** — A word or search that must *not* show the ad, e.g. "pump" or "used" for a filter ad. Stops spending on searches that don't sell.

**Test account** — A practice Google Ads account that can't run ads or charge anything. Google shows it as Canceled, and its clicks and cost stay empty. Example: `AquaLife test` holds a practice campaign so Amazfy's connection can be tried for free.

**Client account** — The Google Ads account that holds campaigns, usually sitting under a manager account. Example: a seller's `AquaLife` account, managed from an agency's manager account.

**Exact match keyword** — A keyword in square brackets, which targets searches with the same meaning as that keyword. Example: `[aquarium filter 40l]` can show an ad for "40l aquarium filter", but not for "aquarium heater".

**Refresh token (Google)** — A long-lived key Google gives Amazfy when a seller allows access to their Google Ads. Amazfy trades it for short-lived access keys whenever it reads the account, and stores it encrypted. Example: AquaLife clicks "Connect Google Ads" once, and the daily sync keeps working without asking them to sign in again.

**Search campaign** — A Google Ads campaign whose text ads show on Google's search results, triggered by keywords. Other types show elsewhere, such as Display on websites or Shopping with product photos. Amazfy's Phase 1 brings in Search campaigns only. Example: AquaLife's "Aquarium filter 40L" campaign shows when someone searches for an aquarium filter.

**Daily budget** — What a Google campaign aims to spend per day on average. Google may spend more on a busy day and less on a quiet one, balancing it out over the month. Amazfy reads it in micros. Example: a $15 daily budget is `15000000` micros.

**Not matched to an ad, once Google is synced** — Bringing in campaigns also brings in removed ads, so a removed ad no longer causes this row. Lines already imported don't change with a sync: uploading the report again matches them. Example: AquaLife uploads a report, then brings in campaigns, and uploads the same report again to match its lines.
