# Google Ads setup for development

What has to exist on Google's side so Amazfy can read a Google Ads account, using test accounts that cost nothing. About 20 minutes, free. Do this once, before the "Connect Google Ads" work (milestone M4).

It builds on the Google Cloud project from [Google login setup](google-login-setup.md). Menu names change over time; if one differs, look for the closest match.

## What you need, and don't

Google retired **developer tokens** on 9 September 2026. Older guides, including older Amazfy notes, are out of date on this:

| | Before | Now |
| --- | --- | --- |
| Where API access belongs | A developer token from a manager account | The **Google Cloud project** that owns the OAuth client |
| Developer token | Required | Not needed. Don't request one from the API Center: Google no longer processes those requests |
| Production manager account (MCC) | Required | Not needed to start. Only for managing several real accounts |
| Test accounts | Under a test manager account | Still the same |

So nothing new goes in `secrets-local.properties`: the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` you already have are the same project's credentials.

## 1. Enable the Google Ads API

1. Sign in to Google Cloud with the account that owns the `Amazfy` project, and select the project.
2. Open https://console.cloud.google.com/flows/enableapi?apiid=googleads.googleapis.com and enable the **Google Ads API**.
3. Open the [Google Ads API Overview page](https://console.cloud.google.com/google/ads-apis/overview). It should show access level **Test**: test accounts only, 15,000 operations a day. That's all development needs, and you don't have to apply for it.

## 2. Add the Ads permission to the consent screen

This is what lets Amazfy ask a user "may Amazfy read your Google Ads?".

**APIs & Services → OAuth consent screen** (also called **Google Auth Platform**):

1. **Data access** (or *Scopes*) → **Add or remove scopes** → add `https://www.googleapis.com/auth/adwords`. If it isn't listed, paste it under *Manually add scopes*. Google marks it as *sensitive*, which is expected. **Save**.
2. **Audience → Test users**: make sure the Google account you'll use in step 4 is listed.

Leave the app in **Testing** status. That's fine for development, but it has one effect: **Google Ads connections expire after 7 days**, and you connect again. See "Later, for real sellers" below.

## 3. Add the redirect address to the OAuth client

**Clients** → open `Amazfy local` → **Authorized redirect URIs** → add:

```
http://localhost:8080/api/v1/google-ads/callback
```

Keep the existing login address too. **Save**. This is the address Google returns to after "Connect Google Ads". It must match character for character (no trailing slash).

## 4. Create a test manager account

A **test manager account** is the top of a practice hierarchy. Its accounts look real but can't run ads and never charge anything.

1. Decide which Google account to use. It must **not be linked to a real manager account**. If yours isn't, use it; otherwise create a new Google account (the sign-in page offers this).
2. Open https://ads.google.com/nav/selectaccount?sf=mt and sign in with it.
3. Fill in the form (name e.g. `Amazfy test manager`, your country and time zone) and create the account.
4. Check that the account shows a red **Test account** label. Without it, you're in a real account: stop and go back.

## 5. Create a test client account

The **client account** is the one that holds campaigns, like a seller's Google Ads account.

1. In the test manager account: **Accounts → + (add) → Create new account**.
2. Fill in the form (name e.g. `AquaLife test`, currency USD, your time zone). It automatically becomes a test account.

A budget-related error may appear here. Google says you can ignore it, because test accounts have no budgets.

Google Ads may show a test account as **Canceled**. That's how it displays them, so don't reactivate it. To see it in the Accounts table, include Canceled in the Account status filter.

## 6. Create a test campaign

Sign in to the test client account and create a small structure, so the sync has something to read. Nothing runs and nothing is charged. Choose **Create a campaign without a goal** if asked, type **Search**.

| What | Value |
| --- | --- |
| Campaign | `Aquarium filter (test)`, status can be paused |
| Ad group | `filter exact`, with a keyword such as `[aquarium filter 40l]` |
| Ad 1 | Final URL `https://www.amazon.com/dp/` + the ASIN of a **real** product: should link to the product automatically |
| Ad 2 | Final URL `https://amzn.to/123456`: should appear as an unlinked ad |

A responsive search ad needs at least 3 headlines and 2 descriptions; any text will do.

**Use a real product for Ad 1.** Any product on Amazon works, not only your own: test accounts never show ads, so you aren't advertising it. Choose one whose ASIN starts with `B0`, the only kind Amazfy accepts (the ASIN is the 10 characters after `/dp/` in the product's address).

**Note that ASIN.** Sample data uses made-up ASINs, so it won't match Ad 1. To see Ad 1 link automatically, add a product with the same ASIN on amazon.com in Amazfy.

Two things to expect:

- **The Publish button** can be at the top right of the page rather than at the bottom. If it's missing, look for a red line on the **Review** page: an error is blocking publishing.
- **The ad's status** can stay *Pending* or *Under review*, and may even become *Disapproved*. None of that matters here, because Amazfy only needs the ad to exist. Editing an ad sends it for review again, so leave it once its final URL is right.

## 7. Note the two account numbers

Copy them from the top of each account's page. They look like `123-456-7890`.

| Account | Used as |
| --- | --- |
| Test **client** account | The account Amazfy reads (you'll pick it in the app) |
| Test **manager** account | Sent to Google when reading through the manager (`login-customer-id`) |

These aren't secrets like a password, but keep them out of the repository anyway. Nothing needs to be configured yet.

## Done when

- [ ] The Google Ads API is enabled, and the Overview page shows **Test**
- [ ] The consent screen has the `adwords` permission, and your account is a test user
- [ ] The OAuth client has the new redirect address
- [ ] A test manager account and a test client account exist, both with the **Test account** label
- [ ] The test client account has a campaign, an ad group and two ads
- [ ] You've noted both account numbers and Ad 1's ASIN

## What test accounts can't do

**Test accounts can't run ads, so their cost, clicks and impressions are always empty.** Campaigns, ad groups, ads and final URLs come through normally, so Amazfy's connection, account picker and structure sync can be tested for real. The **daily spend sync** can't: its tests use fake Google answers until a real account is available.

Also: at most 50 test accounts per hierarchy, and accounts with no logins or API use for a year are deleted.

## Later, for real sellers

Not needed now. Requirements section 10 tracks these.

| Step | Why | How |
| --- | --- | --- |
| **Explorer access** | Reads real accounts (2,880 operations a day) | Apply on the Google Ads API Overview page; mostly reviewed automatically |
| **Brand verification** | Required before Basic | Google Cloud, on the `Amazfy` project |
| **Basic access** | 15,000 operations a day, and keyword planning for the campaign wizard | Apply on the Overview page |
| **OAuth verification of the Ads permission** | Sellers can connect without the "unverified app" warning, and connections stop expiring after 7 days | Publish the app and submit it for verification; can take weeks |

A project on the Google Cloud **free trial**, or with suspended billing, may be rejected when applying for Explorer or Basic. Google's advice is to upgrade to a paid account and retry.

## Troubleshooting

| What you see | Cause | Fix |
| --- | --- | --- |
| `redirect_uri_mismatch` | The address in step 3 differs | Copy it exactly |
| "Access blocked" | The Google account isn't a test user | Add it in step 2 |
| `invalid_grant` after about a week | Testing status: connections expire after 7 days | Connect Google Ads again |
| `AUTHORIZATION_ERROR` when reading an account | Test access reading a real account, or the Google account has no access to it | Use a test account signed in with the Google account from step 4 |
| The test manager page opens a real account | The Google account is already linked to a real manager account | Use another Google account |
| The campaign won't publish: a red **Ads** line on the Review page | Seen with a made-up ASIN in the final URL. It cleared after trying a real product's link, deleting the sitelinks Google added and turning off **AI Max**, but we didn't isolate which one fixed it | Try those three, starting with the real link. AI Max lets Google rewrite ads and change landing pages, so keep it off here anyway |

## Sources

- [Developer token (sunset)](https://developers.google.com/google-ads/api/docs/api-policy/developer-token)
- [Access levels](https://developers.google.com/google-ads/api/docs/api-policy/access-levels)
- [Test accounts](https://developers.google.com/google-ads/api/docs/best-practices/test-accounts)
- [Make your first call](https://developers.google.com/google-ads/api/docs/get-started/make-first-call)
- [Common errors](https://developers.google.com/google-ads/api/docs/get-started/common-errors)
