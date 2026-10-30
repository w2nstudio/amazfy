# "Not linked" and "Not matched" report rows

The dashboard's table can show two special rows. They are easy to confuse, but they are opposite problems: one on the Google side, one on the Amazon side.

| | **Not linked to a product or brand** | **Not matched to an ad** |
| --- | --- | --- |
| What Amazfy **knows** | The **ad** (from Google), so its **spend** | The **sale** (from Amazon Attribution) |
| What Amazfy **doesn't know** | Which **product** the ad advertises | Which **ad** brought the sale |
| Why | The ad's link can't be read, e.g. `amzn.to/123456` (a short link) instead of `amazon.com/dp/B0…` | Amazon reports ids that match none of the account's ads |
| Has spend? | Yes | No (spend unknown, so ROAS and CPA show "—") |
| Has sales? | Yes, if its sales are matched to it | Yes |

Both rows exist so the **totals stay complete**: the rows of a level add up to the dashboard's tiles. Without them, money spent or earned would silently vanish from the table.

## In the sample data

- **Not linked:** each brand has a short-link ad (`amzn.to/…`). Amazfy knows it costs $5 a day, but can't tell which product it sells, so it can't sit under AquaLife or ClearTank.
- **Not matched:** one Attribution line per day with a creative id (`999999999999`) that isn't any known ad: 1 purchase, $40 a day. Amazon says "this sale came from a Google ad", but Amazfy has no such ad.

Expected numbers: [Sample data and expected results](sample-data.md).

## Where each row shows up

| | **Not linked** | **Not matched** |
| --- | --- | --- |
| View by Brand or Product | Yes | Yes, always last |
| View by Campaign | No row of its own: its ads sit inside their campaign, because the campaign is known | Yes, always last |
| After drilling into a brand, product or campaign | Only its own ads, in their rows | No: these numbers belong to no brand, product or ad |
| Can be clicked | Yes: opens its campaigns and ads | No: there is no ad to open |

## How each gets fixed in real use

| | Typical cause | Fix |
| --- | --- | --- |
| **Not linked** | Short links, links to a non-Amazon page, a product not added to Amazfy yet | The seller adds the product, or links the ad by hand on the **Ads** page. The ad then moves under its product or brand. |
| **Not matched** | The ad was removed on Google before Amazfy's first sync; Google isn't synced yet; the report only goes down to ad groups; a report from another seller account | The next Google sync brings the ad in and the line gets matched on the next upload or sync. A large share of unmatched sales points to a wrong file, which the import page warns about. |
