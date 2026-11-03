# Demo with a real seller

How to show Amazfy working on a real seller's own numbers: their real Google Ads spend next to their real Amazon sales, per ad. Run on the developer's laptop with the local setup, before any approval beyond Explorer access. The big approvals are applied for after the demo.

## What's ready and what isn't

| For the demo | Status |
| --- | --- |
| Connect Google, pick the Google Ads account, bring in campaigns, ad groups and ads | Works; tested on a test account |
| Daily sync, Ads page, Campaigns page, dashboard, drill-down, trend chart | Works |
| Upload an Attribution CSV and match its lines to ads, also after a later sync | Works, with files shaped like Amazon's documented columns |
| **Reading a real Google Ads account** | **Needs Explorer access**: with Test access, Google refuses to read real accounts |
| Real spend numbers | Not tested yet: test accounts have no spend |
| A real Attribution CSV | Not tested yet: no real export seen so far |
| Entering the seller's products | Works: **Add this product** under **Ads to link** fills in the ASIN and marketplace from each ad's link |

## Approvals, and when each is needed

An approval is Google or Amazon agreeing that Amazfy may read or change a seller's account through their systems.

| Approval | What it allows | When |
| --- | --- | --- |
| **Google Explorer access** | Reading real Google Ads accounts (2,880 operations a day) | **Before the demo.** Steps in [Google Ads setup](google-ads-setup.md#explorer-access-to-read-real-accounts) |
| Google brand verification | Google confirms Amazfy is a real company, from its website and domain | After the demo; before Basic |
| Google Basic access | 15,000 operations a day, and keyword ideas for the campaign wizard (M7) | Before the beta |
| Google OAuth verification of the Ads permission | No "unverified app" warning, and connections stop expiring after 7 days | Before real sellers use it; can take weeks |
| Amazon Ads API access | Reading Attribution data and setting tracking tags (M5) | M5 can't be built or tested without it; takes weeks, so apply right after the demo |

All but Explorer need company details: legal name, address, a website on Amazfy's own domain with a privacy policy and terms, and a support email ([requirements, section 10](../requirements/phase-1-requirements.md#10-approvals-checklist-start-in-week-1)).

## Before the demo

**Developer (Amazfy's Google Cloud project owner):**

- [ ] Explorer access applied for, and the Overview page shows **Explorer**
- [ ] The seller's Google account added under **Google Auth Platform → Audience → Test users**
- [ ] Latest `main` running locally (backend restarted after pulling)

**Seller, a few days ahead:**

- [ ] An **Amazon Attribution report downloaded as CSV**, broken down by creative (ad), for the last 30 to 90 days, sent to the developer. It's checked against the importer before the demo, since a column Amazfy doesn't recognise is the likeliest thing to fail on the day
- [ ] Confirmed that their Google ads carry the **Amazon Attribution tag** in the tracking template. Without it, Amazon reports no sales per ad, and the demo shows spend only
- [ ] A passkey on the Google account that manages their Google Ads (Google asks for one when connecting; a new passkey can take up to 7 days to work)

**Code still to do:**

- [x] "Add this product" on the Ads page: an ad's ASIN and marketplace filled in from its final URL, the seller picks a brand and name
- [ ] Fixes from the seller's real CSV, if any

## Rehearsal

Once Explorer is approved, run the whole demo once privately with the seller's account and CSV, so the first real spend read and the first real file can't surprise anyone in front of them. Keep the backend log open. Amazfy can't delete a seller account yet (M6), so the demo continues from the rehearsal's data, or starts from a different sign-up.

## The demo

1. The seller signs up in Amazfy (or continues with Google) and creates their seller account for their region.
2. **Ads → Connect Google Ads**: their Google login, past the "unverified app" warning (**Advanced → Go to Amazfy**), passkey, allow. Connect on the day: in Testing, connections expire after about 7 days.
3. Choose their Google Ads account → **Bring in campaigns and spend**: campaigns, ads and 90 days of spend.
4. **Ads → Ads to link → Add this product** on each ad pointing to one of their products: a name and a brand (the first time, **New brand…**), and every ad pointing to that product links at once.
5. **Import**: their Attribution CSV for the right marketplace → preview → import.
6. **Home**: spend, sales, ROAS and CPA; drill down from brand to product to campaign to the single ad; the trend chart.

## Fallback: demo on test accounts

If the seller's Gmail or Attribution export isn't ready in time, the same story can be shown without their data. Two parts, each honest about what it is: the reports on made-up numbers, and the real Google connection on a test account.

**Two seller accounts in Amazfy are needed**, because **Fill with sample data** deletes all of a seller account's campaigns and Attribution numbers, including those brought in from Google:

| Seller account | Holds | Shows |
| --- | --- | --- |
| Your existing one (e.g. *Aqualife - US*) | Connected to the Google test account | The real connection: Google's consent page, picking an account, bringing in campaigns and ads, linking, the Campaigns page, an Attribution upload matched to real ad ids |
| A second one, e.g. *Demo shop* (**Add Amazon seller account**) | Sample data only, never connected | The reports: 90 days of spend, sales and ROAS by brand, product, campaign and ad |

### Before the fallback demo

- [ ] **Demo shop** created; **Fill with sample data** pressed **on the demo day** (its numbers end the day before it's pressed)
- [ ] The test seller account connected within the last 7 days (**Connect again** on the demo day morning is safest)
- [ ] An Attribution CSV for the test ads: the ids of your test campaign, ad group and ads (pgAdmin: `external_id` in `campaigns`, `ad_groups`, `ads`) with made-up sales. Claude can generate it from the database; keep it out of the repository
- [ ] Optional: a change ready to make in Google Ads during the demo, such as renaming the test campaign

### The fallback demo (about 15 minutes)

1. **The problem.** Google Ads shows clicks and cost, Amazon shows sales, and neither says which ad brought which sale.
2. **The answer, on sample data** (*Demo shop*, say the numbers are made up): the tiles (spend, sales, ROAS, CPA); **View by Brand → Product → Campaign → ad**. Then the key moment: filter **ad A and ad B** cost the same $20 a day, but A sells twice as much (ROAS 4.00 vs 2.00), something Google alone can't show. Then the **Not linked** and **Not matched** rows (totals stay complete), and the trend chart with the last 14 days shaded while Amazon is still counting.
3. **It's real, on the Google test account** (*Aqualife - US*): **Ads → Bring in campaigns and spend** live; the **Campaigns** page shows the campaign built in Google Ads; rename it in Google Ads, bring in again, and the page follows. **Ads to link**: link the short-link ad to a product. **Import**: upload the test CSV, and its lines match the real ads.
4. **What their own account adds:** real spend next to their real sales, per ad; then the Amazon connector (M5) fetches Attribution by itself, with no CSV.

What it can't show: test accounts have no spend, so part 3 shows no cost or ROAS. Sample numbers are the same every day, so part 2's trend lines are flat.

## After the demo

- [ ] Apply for Amazon Ads API access and the Google approvals straight away; they take weeks
- [ ] Meanwhile, build what needs no approval: M6 (account deletion, privacy, deployment, audit log, alerts) and most of M7
