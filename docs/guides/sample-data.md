# Sample data and expected results

Local development only. The dashboard's sample data panel (owner, `npm run dev`) or `POST /accounts/{id}/dev/sample-data` in `api/amazfy.http` replaces the seller account's campaigns, ads, spend and Attribution numbers with one of two sets. `SampleDataTest` checks both against this page.

| Set | Button | For |
| --- | --- | --- |
| **Full sample** (`set=FULL`, the default) | **Full sample: 90 days** | Checking reports: round numbers, the same every day, so every tile, table row and chart value can be checked with a calculator. Described first, below |
| **Small demo** (`set=SMALL_DEMO`) | **Small demo: Leafwell Garden, 4 days** | Demos: one gardening brand over 4 days, uneven like a real shop. See [Small demo: Leafwell Garden](#small-demo-leafwell-garden) at the end |

Both replace whatever sample data was there before; products stay.

## What it creates

- **90 days of numbers, up to yesterday** (the day before you click Fill). The days after that stay empty, so **fill again on the day you test**.
- **Three sample products** on the region's first marketplace (US for North America, UK for Europe), created with their brands if missing. Your own products are left alone and get no ads, so they show no numbers.

| Brand | Product | ASIN | Price |
|---|---|---|---|
| AquaLife | Aquarium filter 40L | B0AQL00001 | $40 |
| AquaLife | Aquarium heater 100W | B0AQL00002 | $30 |
| ClearTank | Glass cleaner 500ml | B0CLT00001 | $12 |

- **Google:** per product, one campaign with two ad groups (*exact*, *broad*), each with two ads. Ad **A** sells twice a day, ad **B** once, at the same spend. Per brand, one *Brand Store* campaign with two ads: one links to the brand's store (brand-level, no single product), the other uses a short link (`amzn.to/…`) that Amazfy can't read (not linked).
- **Amazon Attribution:** a line for every ad, plus one line for an ad Amazfy doesn't know (not matched).
- **Fixed rules:** 50 cents a click; 20 impressions per click; 9 of 10 clicks reach Amazon (click-throughs); every purchase is one unit.

In euros or pounds the same numbers apply in that currency.

## Per ad, every day

| Ad | Spend | Clicks | Click-throughs | Purchases | Sales | ROAS | CPA | Conversion rate |
|---|---|---|---|---|---|---|---|---|
| Filter ad A (each ad group) | $20 | 40 | 36 | 2 | $80 | 4.00 | $10.00 | 5.56% |
| Filter ad B (each ad group) | $20 | 40 | 36 | 1 | $40 | 2.00 | $20.00 | 2.78% |
| Heater ad A | $10 | 20 | 18 | 2 | $60 | 6.00 | $5.00 | 11.11% |
| Heater ad B | $10 | 20 | 18 | 1 | $30 | 3.00 | $10.00 | 5.56% |
| Cleaner ad A | $5 | 10 | 9 | 2 | $24 | 4.80 | $2.50 | 22.22% |
| Cleaner ad B | $5 | 10 | 9 | 1 | $12 | 2.40 | $5.00 | 11.11% |
| AquaLife Brand Store ad | $5 | 10 | 9 | 1 | $40 | 8.00 | $5.00 | 11.11% |
| ClearTank Brand Store ad | $5 | 10 | 9 | 1 | $12 | 2.40 | $5.00 | 11.11% |
| Short-link ad (one per brand) | $5 | 10 | 9 | 0 | $0 | 0.00 | — | 0.00% |
| Unmatched Attribution line | — | — | 9 | 1 | $40 | — | — | 11.11% |

## Expected results

Every number is **per day**: for a range, multiply amounts and counts by its days (ROAS, CPA and conversion rate stay the same).

**Dashboard tiles (whole seller account):** spend **$160**, sales **$584**, ROAS **3.65**, purchases **21**, CPA **$7.62**, conversion rate **7.07%** (21 of 297 click-throughs); Google clicks 320, units 21.

| Range | Spend | Sales | Purchases | Click-throughs |
|---|---|---|---|---|
| Last 7 days | $1,120 | $4,088 | 147 | 2,079 |
| Last 30 days | $4,800 | $17,520 | 630 | 8,910 |
| Last 90 days | $14,400 | $52,560 | 1,890 | 26,730 |

**View by Brand:**

| Row | Spend | Sales | ROAS | Purchases | CPA | Conv. rate |
|---|---|---|---|---|---|---|
| AquaLife | $125 | $460 | 3.68 | 13 | $9.62 | 5.78% |
| ClearTank | $25 | $84 | 3.36 | 7 | $3.57 | 15.56% |
| Not linked to a product or brand | $10 | $0 | 0.00 | 0 | — | 0.00% |
| Not matched to an ad | $0 | $40 | — | 1 | — | 11.11% |

**View by Product** (or click AquaLife: its two products and its brand-level row):

| Row | Spend | Sales | ROAS | Purchases | CPA | Conv. rate |
|---|---|---|---|---|---|---|
| Aquarium filter 40L | $80 | $240 | 3.00 | 6 | $13.33 | 4.17% |
| Aquarium heater 100W | $40 | $180 | 4.50 | 6 | $6.67 | 8.33% |
| Glass cleaner 500ml | $20 | $72 | 3.60 | 6 | $3.33 | 16.67% |
| AquaLife – brand-level | $5 | $40 | 8.00 | 1 | $5.00 | 11.11% |
| ClearTank – brand-level | $5 | $12 | 2.40 | 1 | $5.00 | 11.11% |
| Not linked + Not matched | as above | | | | | |

**View by Campaign:**

| Row | Spend | Sales | ROAS | Purchases | CPA | Conv. rate |
|---|---|---|---|---|---|---|
| AquaLife – Aquarium filter 40L (US) | $80 | $240 | 3.00 | 6 | $13.33 | 4.17% |
| AquaLife – Aquarium heater 100W (US) | $40 | $180 | 4.50 | 6 | $6.67 | 8.33% |
| AquaLife – Brand Store (US) | $10 | $40 | 4.00 | 1 | $10.00 | 5.56% |
| ClearTank – Brand Store (US) | $10 | $12 | 1.20 | 1 | $10.00 | 5.56% |
| ClearTank – Glass cleaner 500ml (US) | $20 | $72 | 3.60 | 6 | $3.33 | 16.67% |
| Not matched to an ad | $0 | $40 | — | 1 | — | 11.11% |

A Brand Store campaign holds both the store ad and the short-link ad, so it costs $10 a day but only the store ad sells.

**Drilling into a product's campaign:** two ad groups, *exact* and *broad*, each half the campaign (filter: $40 spend, $120 sales, ROAS 3.00). Each ad group's ads are A and B from the per-ad table.

**Trend chart:** flat lines, since every day is the same. Whole account: sales $584, spend $160. After clicking AquaLife: sales $460, spend $125. After clicking the filter: sales $240, spend $80.

**Checks that hold for any range:** at the top of the drill-down, the rows add up to the tiles. After a click, the rows add up to the clicked row. The chart's days add up to the tiles (open *Daily numbers*).

## Small demo: Leafwell Garden

A small, made-up gardening shop for demos: easy to explain in a minute, and uneven like a real one. **Small demo: Leafwell Garden, 4 days** fills it, and the dashboard then shows exactly its 4 days.

- **The 4 days are 18 to 15 days ago**, not up to yesterday: Amazon's 14-day window has closed for them, so nothing is marked as still being counted. Fill it again on the demo day, and the days move along.
- **One brand, Leafwell, two products** on the region's first marketplace (US in North America), created if missing:

| Product | ASIN | Price |
| --- | --- | --- |
| Bypass pruning shears | B0LFW00001 | $25 |
| Gardening gloves | B0LFW00002 | $18 |

- **Google:** three Search campaigns.

| Campaign | Ad group | Ad | Links to |
| --- | --- | --- | --- |
| Leafwell – Pruning shears (US) | pruning shears | **ad A** (the lower id) | the shears |
| | | **ad B** | the shears |
| Leafwell – Gardening gloves (US) | gardening gloves | gloves ad | the gloves |
| Leafwell – Brand (US) | leafwell | short-link ad (`amzn.to/…`) | nothing: **Not linked** |

  Plus Attribution lines for an ad Amazfy doesn't know: **Not matched**. Amazfy shows ads by their Google id, not their text, so ad A is the one with the lower id.

- **Fixed rules:** $1 a click (clicks = spend in dollars); 20 impressions per click; 9 of 10 clicks reach Amazon (click-throughs, rounded to whole visits); 2 add-to-carts per purchase; one unit per purchase.

### Per ad and day

Purchases in brackets; sales = purchases × price.

| | Day 1 | Day 2 | **Day 3** | Day 4 | 4 days | ROAS |
| --- | --- | --- | --- | --- | --- | --- |
| **Ad A** spend | $18 | $22 | **$30** | $25 | $95 | |
| sales | $50 (2) | $75 (3) | **$150 (6)** | $50 (2) | $325 (13) | **3.42** |
| **Ad B** spend | $20 | $19 | $24 | $22 | $85 | |
| sales | $25 (1) | $0 | $0 | $0 | $25 (1) | **0.29** |
| **Gloves ad** spend | $12 | $14 | **$18** | $13 | $57 | |
| sales | $36 (2) | $36 (2) | **$90 (5)** | $18 (1) | $180 (10) | **3.16** |
| **Short-link ad** spend | $5 | $4 | $6 | $5 | $20 | 0.00 |
| **Not matched** sales | $0 | $25 (1) | $25 (1) | $0 | $50 (2) | — |
| **Whole shop** spend | $55 | $59 | **$78** | $65 | **$257** | |
| sales | $111 | $136 | **$265** | $68 | **$580** | **2.26** |

Click-throughs over the 4 days: ad A 86, ad B 77, gloves 52, short link 19, not matched 28 (6, 8, 9 and 5 a day).

### Expected results for the 4 days

**Tiles:** spend **$257**, sales **$580**, ROAS **2.26**, purchases **26**, CPA **$9.88**, conversion rate **9.92%** (26 of 262 click-throughs); clicks 257.

**View by Brand:**

| Row | Spend | Sales | ROAS |
| --- | --- | --- | --- |
| Leafwell | $237 | $530 | 2.24 |
| Not linked to a product or brand | $20 | $0 | 0.00 |
| Not matched to an ad | $0 | $50 | — |

**View by Product** (or click Leafwell): Bypass pruning shears $180 → $350 (ROAS 1.94), Gardening gloves $57 → $180 (3.16).

**Click the shears, then its campaign and ad group:** ad A $95 → $325 (ROAS 3.42) and ad B $85 → $25 (0.29). Same product, about the same cost, but A sells 13 times and B once: B is the ad to pause.

**Trend chart:** day 3 is the peak ($78 → $265), day 4 the dip ($65 → $68).
