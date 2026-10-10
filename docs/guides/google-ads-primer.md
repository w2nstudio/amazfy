# Google Ads primer for Amazfy developers

Read this before working on the Google connector or the reporting module. It explains how Google Ads is organised and how Amazfy maps it to Amazon products. No prior advertising knowledge is assumed.

## The structure

```
Google login (john@gmail.com)            ← a person's sign-in; can access several Google Ads accounts
 └─ Google Ads account (123-456-7890)    ← billing card, currency, time zone
     └─ Campaign                         ← daily budget, target countries, campaign type
         └─ Ad group                     ← one set of keywords
             ├─ Keywords                 ← the searches that can show the ads
             └─ Ads (1–3)                ← headlines, descriptions, final URL
```

- **Google login vs Google Ads account.** A login is just a person. One login can manage several Google Ads accounts. Agencies use a *manager account* (MCC) to manage many.
- **Google Ads account.** Holds the payment method, currency and time zone. The seller pays Google here. Amazfy never touches billing.
- **Campaign.** Holds the **daily budget** and targeting (countries, languages). Every ad must sit in an ad group inside a campaign, even when there is only one ad.
- **Ad group.** Holds **keywords**. All ads in an ad group share its keywords, and Google picks which ad to show for a matching search.
- **Ad.** What the shopper sees. Amazfy uses **responsive search ads**: the seller writes up to 15 headlines (30 characters each) and 4 descriptions (90 characters each), and Google combines them automatically. An ad group can have up to 3 active responsive search ads, normally different wordings for the same product. Google shows the better performer more often.
- **Final URL.** Where a click goes. For Amazfy sellers this is an Amazon page.

### Example

```
Campaign "AquaLife – US Search"   (budget $40/day, United States)
 ├─ Ad group "hydrogen bottle"    keywords: hydrogen water bottle, h2 bottle
 │   ├─ Ad 1 "Hydrogen Water Bottle – Boost Your Hydration" → amazon.com/dp/B0AAA11111
 │   └─ Ad 2 "Portable H2 Bottle – 4.8★ on Amazon"          → amazon.com/dp/B0AAA11111
 ├─ Ad group "cleaning kit"       keywords: bottle cleaning kit
 │   └─ Ad 1 "Bottle Cleaning Kit – Fits All Bottles"       → amazon.com/dp/B0AAA22222
 └─ Ad group "brand"              keywords: aqualife
     └─ Ad 1 "AquaLife Official Store"                       → amazon.com/stores/AquaLife/...
```

## Things that surprise newcomers

- **A campaign can hold many products, even unrelated ones.** Google does not restrict it. Sellers organise campaigns in different ways: one per product (separate budgets), or one per brand with an ad group per product.
- **One ad group can technically hold ads for different products,** but it works badly: the keywords are shared, so a search for "hydrogen water bottle" might show the cleaning kit ad, and Google's quality rating (Quality Score) drops, raising cost per click. Good practice, and the Amazfy wizard's rule: **one product per ad group**.
- **Cost is reported at every level:** campaign, ad group, ad and keyword, per day. Amazfy reads cost **per ad per day** and sums upward.

## How Amazfy maps ads to Amazon products

The **ad** is the unit Amazfy links to a product, because each ad has one final URL.

| Final URL | Amazfy links the ad to | How |
| --- | --- | --- |
| `amazon.com/dp/B0AAA11111…` | Product with ASIN B0AAA11111 | Automatic: ASIN parsed from the URL |
| `amazon.com/stores/AquaLife/…` (Brand Store) | Brand "AquaLife" (brand-level spend) | Seller picks the brand |
| Short link (`amzn.to/…`) or other | Product or brand | Seller picks |

A **Brand Store** is a brand's mini-website inside Amazon showing all its products. A shopper landing there may buy any of them, so that ad's cost cannot be assigned to one product. Amazfy reports it as brand-level spend.

Once ads are linked, every number is a sum:

- Product spend = sum of spend of ads linked to the product
- Campaign spend = sum of spend of ads in the campaign
- Brand spend = its products' spend + its brand-level spend

## How Amazon sales get back to the right ad

1. Amazon Attribution gives Amazfy a **macro tag** for Google: tracking parameters with placeholders such as `{campaignid}` and `{adgroupid}`.
2. Amazfy adds the tag to the ads' **tracking template**, so every click carries its campaign, ad group and ad IDs to Amazon.
3. When the shopper buys within 14 days, Amazon Attribution records the sale against those IDs.
4. Amazfy pulls the Attribution report daily and joins sales to Google spend by those IDs.

Still to verify with a real seller: whether Attribution reports Google sales per ad or only per ad group. Either works with the data model (see the requirements, section 4).

## API notes

- Google Ads API calls are made with the **official Java client library**, using a developer access level tied to Amazfy's Google Cloud project (Test → Explorer → Basic → Standard).
- Metrics are read with GAQL queries (Google Ads Query Language) on resources such as `ad_group_ad`, segmented by `segments.date`.
- Data for recent days can still change. Re-sync the last few days on every run.
- Daily operation limits apply per access level (15,000 on Basic). Read in bulk; never one request per ad.

## Further reading

- [Google Ads API: access levels](https://developers.google.com/google-ads/api/docs/api-policy/access-levels)
- [Google Ads API: onboarding guide](https://developers.google.com/google-ads/api/docs/get-started/onboarding)
- [Amazon Attribution guide](https://advertising.amazon.com/library/guides/basics-of-amazon-attribution)
