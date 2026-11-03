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
| Entering the seller's products | Works by typing each ASIN; "Add this product" (backlog) makes it a few clicks |

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

- [ ] "Add this product" on the Ads page: an ad's ASIN and marketplace filled in from its final URL, the seller picks a brand and name
- [ ] Fixes from the seller's real CSV, if any

## Rehearsal

Once Explorer is approved, run the whole demo once privately with the seller's account and CSV, so the first real spend read and the first real file can't surprise anyone in front of them. Keep the backend log open. Amazfy can't delete a seller account yet (M6), so the demo continues from the rehearsal's data, or starts from a different sign-up.

## The demo

1. The seller signs up in Amazfy (or continues with Google) and creates their seller account for their region.
2. **Ads → Connect Google Ads**: their Google login, past the "unverified app" warning (**Advanced → Go to Amazfy**), passkey, allow. Connect on the day: in Testing, connections expire after about 7 days.
3. Choose their Google Ads account → **Bring in campaigns and spend**: campaigns, ads and 90 days of spend.
4. Add their brand and products (or use **Add this product** under **Ads to link**); ads with Amazon product links link by themselves.
5. **Import**: their Attribution CSV for the right marketplace → preview → import.
6. **Home**: spend, sales, ROAS and CPA; drill down from brand to product to campaign to the single ad; the trend chart.

## After the demo

- [ ] Apply for Amazon Ads API access and the Google approvals straight away; they take weeks
- [ ] Meanwhile, build what needs no approval: M6 (account deletion, privacy, deployment, audit log, alerts) and most of M7
