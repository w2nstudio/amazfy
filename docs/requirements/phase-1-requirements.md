# Amazfy Phase 1 Requirements

Status: agreed · Last updated: 2026-10-10

Phase 1 proves one thing: an Amazon seller can run Google Ads through Amazfy and see the Amazon sales and ROAS that each Google ad, ad group, campaign and product produced.

New to Google Ads? Read [Google Ads primer](../guides/google-ads-primer.md) first.

## 1. Overview

Amazfy is a SaaS for Amazon sellers who drive traffic to their Amazon listings from outside Amazon. The long-term product covers many channels (Meta, TikTok, Pinterest, Reddit, creators, publishers) plus an AI agent that recommends budget changes. Phase 1 covers one channel, **Google Ads**, so the core loop is proven before anything else is built.

The seller always pays Google directly from their own Google Ads account. Amazfy manages campaigns through the API and never handles ad money.

**Phase 1a: connect and measure**

- The seller connects a Google Ads account and an Amazon Ads (Attribution) account.
- Amazfy reads the seller's Google campaigns, ad groups and ads, and matches each ad to a product by the ASIN in its link.
- Amazfy adds the Amazon Attribution tag to those ads' links (with the seller's approval).
- Amazfy syncs Google spend and Amazon Attribution sales daily and shows ROAS per product, brand, campaign, ad group and ad.

**Phase 1b: create campaigns**

- A guided wizard creates a Google Search campaign for one product, or adds a product to an existing campaign as a new ad group.
- The seller can pause, resume and change the budget of Amazfy-created campaigns.

**Out of scope for Phase 1:** other ad channels, the AI agent, creators and publishers, inviting team members, billing for Amazfy itself, and Performance Max, Display or Shopping campaigns.

The company-supplied prototype (`amazfy_mvp`, FastAPI with Meta and CSV import) is reference material only. Phase 1 is a new codebase.

## 2. Glossary and naming rules

| Term | Meaning |
| --- | --- |
| **User** | A person who logs in to Amazfy. Never called "account". |
| **Account** | One seller business in Amazfy. Maps 1:1 to one **Amazon seller account**. |
| **Membership** | Links a user to an account with a role. Users and accounts are many-to-many. |
| **Brand** | A seller's brand (registered in Amazon Brand Registry). An account has one or more brands. |
| **Product** | One Amazon listing, unique by **ASIN + marketplace** within an account. Belongs to a brand. |
| **ASIN** | Amazon's 10-character product ID, starting with "B0". Each variation (size, colour) has its own ASIN. |
| **Marketplace** | An Amazon country site: US, CA, UK, DE, … |
| **Amazon seller account** | The seller's account in Amazon Seller Central. Always written in full. |
| **Google Ads account** | The seller's account in Google Ads (customer ID like 123-456-7890). Always written in full. |
| **Campaign / Ad group / Ad** | Google Ads structure; see the primer. |
| **Attribution tag** | Tracking parameters Amazon generates; added to an ad's link so Amazon can report the sales it caused. |
| **Brand-level spend** | Spend on ads that link to a Brand Store page rather than one product. |

Amazon gives a seller one seller account **per region** (North America, Europe, …). A seller in the US and the UK therefore has two Amazfy accounts. Phase 1 launches in the US only.

## 3. Users, accounts and onboarding

```
User (person who logs in)
 └─ Membership (role: owner) ─ Account  ↔ one Amazon seller account
                                 ├─ Google Ads connection
                                 ├─ Amazon Ads (Attribution) connection
                                 └─ Brands
                                     └─ Products (ASIN + marketplace)
```

- A user signs up with email and password, or with "Sign in with Google".
- The sign-up form asks for email, password and **account name** (the business name). Amazfy creates the user, the account and an owner membership in one step.
- A user can create more accounts later ("Add account") and switch between them from the top bar.
- Products, brands, connections, campaigns and metrics all belong to an account, never to a user.
- Phase 1 has only the **owner** role. Inviting other users (editor, viewer) is a later phase; the Membership table exists from day one so no migration is needed.
- "Sign in with Google" (login) and "Connect Google Ads" (ad access) are separate consents, even when the same Google account is used.

### Onboarding checklist

After sign-up the dashboard shows a checklist until every step is done.

1. **Check eligibility.** The seller confirms they are a professional seller in Brand Registry, in a supported marketplace. Amazfy links to Amazon's Brand Registry page if not.
2. **Enable Amazon Attribution.** A short guide shows how to open Attribution in the Amazon Ads console. Most sellers have never used it.
3. **Connect Amazon.** Login with Amazon. Amazfy stores the token and lists the seller's Attribution advertisers; the seller picks one.
4. **Connect Google Ads.** Google OAuth. Amazfy lists accessible Google Ads accounts; the seller picks one.
5. **Add brands and products.** The seller enters ASINs, each with marketplace and brand. Amazfy also suggests products from ASINs found in existing ad links.
6. **Review ad matching (1a)** or **create a campaign (1b).**

If Amazon API access is not yet approved for Amazfy, step 3 is replaced by a CSV upload of the Attribution report.

## 4. How spend and sales are linked to products

The **ad** is the unit of linking, not the campaign.

- Every Google ad has a final URL. If it points to a product page (`amazon.com/dp/B0AAA11111`), Amazfy extracts the ASIN and links the ad to that product **automatically**.
- If it points to a Brand Store page (`amazon.com/stores/...`) or a short link, there is no single product. The seller links it to a brand, and its spend is shown as **brand-level spend**.
- Spend and clicks are stored **per ad per day**. Ad group, campaign, product, brand and account figures are sums of ad rows.
- One campaign may contain ads for several products. One ad group may too (imported campaigns only; Amazfy shows a tip suggesting one product per ad group).
- The Amazfy wizard always creates **one product per ad group**.

| Seller's setup | Cost per product | Accuracy |
| --- | --- | --- |
| One product per campaign | Campaign's ads | Exact |
| One ad group per product | Ad group's ads | Exact |
| Mixed ad group, each ad links to one product | That product's ads | Exact |
| Ads link to a Brand Store page | Brand-level only | Brand-level |

**To verify with a real seller (open risk):** that Amazon Attribution reports sales per ad ("creative") for Google macro tags. If it reports only per ad group, sales are stored per ad group, which is still exact when an ad group advertises one product.

## 5. Functional requirements

### Phase 1a: connect and measure

| ID | As a seller, I can… | Done when |
| --- | --- | --- |
| A1 | Sign up and log in with email/password or Google | User, account and owner membership created; password reset by email works |
| A2 | Create another account and switch between my accounts | Account switcher in the top bar; all data shown is scoped to the selected account |
| A3 | Connect and disconnect my Amazon Ads account | Token stored encrypted; Attribution advertisers listed; disconnect deletes the token |
| A4 | Connect and disconnect my Google Ads account | Token stored encrypted; accessible Google Ads accounts listed; I pick one |
| A5 | Add, edit and remove brands and products | ASIN format validated; ASIN + marketplace unique in the account; each product has a brand |
| A6 | See my Google campaigns, ad groups and ads | Synced with name, status, budget, final URL |
| A7 | Have ads matched to products automatically | ASIN parsed from final URL; unmatched ads listed for me to link to a product or brand |
| A8 | Have the Attribution tag applied to my ads | Amazfy fetches the Google macro tag and sets it on the tracking template; I approve before anything is written |
| A9 | Get data synced without doing anything | Google spend per ad daily; Attribution report daily, re-pulling the last 14 days |
| A10 | Upload an Attribution CSV when the API is unavailable | Real Amazon export format parsed; re-upload updates rows, never duplicates |
| A11 | See a dashboard summary for a date range | Spend, Amazon sales, orders, ROAS, CPA, conversion rate; default last 30 days |
| A12 | Drill down by brand → product → campaign → ad group → ad | Sortable tables; brand-level spend shown as its own row per brand |
| A13 | See a daily trend chart | Spend and sales per day for the selected level |
| A14 | See when data was last synced and any sync errors | Per-connection status; expired token shows a reconnect button |

### Phase 1b: create campaigns

| ID | As a seller, I can… | Done when |
| --- | --- | --- |
| B1 | Start the wizard from a product | Product name and Amazon listing URL pre-filled |
| B2 | Choose a new campaign or add to an existing one | Existing campaign gets a new ad group for this product |
| B3 | Get keyword suggestions and edit them | Suggestions from Google Keyword Planner; match type per keyword |
| B4 | Write ad text with Google's limits shown | Responsive search ad: up to 15 headlines (30 chars) and 4 descriptions (90 chars) |
| B5 | Set a daily budget and target locations | Budget in the Google Ads account's currency; default country = product marketplace |
| B6 | Review and launch | Campaign, ad group, keywords and ad created through the API with the Attribution tag set; launch paused or live (see open questions) |
| B7 | Pause, resume and change the budget | Change applied in Google and recorded in the audit log |
| B8 | See policy rejections and errors | Google disapproval reasons shown on the ad in plain words |

### Metric definitions

- **ROAS** = attributed Amazon sales ÷ Google spend
- **CPA** = Google spend ÷ attributed Amazon purchases
- **Conversion rate** = attributed purchases ÷ Attribution click-throughs × 100
- Sales use Amazon's 14-day attribution window. Totals are in the marketplace currency; different currencies are never summed.

## 6. Data model

```
User ──< Membership >── Account
                          ├── GoogleAdsConnection (customer ID, encrypted token)
                          ├── AmazonConnection (profile ID, advertiser ID, encrypted token)
                          ├── Brand ──< Product (ASIN, marketplace, name)
                          ├── Campaign ──< AdGroup ──< Ad (final URL, product_id?, brand_id?)
                          │                               ├── AdSpendDaily (date, spend, impressions, clicks)
                          │                               └── AttributionDaily (date, clicks, page views, add to carts, purchases, units, sales)
                          ├── AuditLog (who, what, when, approved by)
                          └── SyncRun (connection, status, error)
```

- Every table carries `account_id`, and every query filters on it.
- `Ad` links to a product, or (for Brand Store links) only to a brand.
- `AdSpendDaily` and `AttributionDaily` are unique on (ad, date). Syncs upsert, so re-running never duplicates.
- If Attribution returns data only per ad group, `AttributionDaily` rows are keyed on the ad group instead (decided after the real-seller test).
- Attribution rows that match no known Google ad are kept as unmatched and shown to the seller.
- Campaign, AdGroup and Ad carry a `channel` column (`google` in Phase 1) and the external ID, leaving room for Meta and TikTok later.

## 7. Architecture and tech stack

Phase 1 is one deployable Spring Boot application split into clear modules (a modular monolith). One developer can run it, and modules can become separate services later.

```
React SPA ──HTTPS──▶ Spring Boot backend (Kotlin)
                      ├─ auth & accounts      ├─ google connector ──▶ Google Ads API
                      ├─ catalog (brands,     ├─ amazon connector ──▶ Amazon Ads API (Attribution)
                      │  products)            └─ sync jobs
                      └─ reporting
                              │
                      PostgreSQL · secrets manager
```

The two connectors are the only code that talks to outside APIs. Each implements a common channel interface, so adding Meta or TikTok later means adding a connector, not changing reporting.

### Module architecture: hexagonal (ports and adapters)

Business logic in the centre knows nothing about Google, Amazon, the database or HTTP. It talks to the outside only through interfaces (**ports**); the real implementations (**adapters**) plug into them.

```
<module>/
├─ domain/              plain Kotlin models and rules (no Spring, no JPA, no API clients)
├─ application/         use cases, e.g. SyncAttribution, ImportAttributionCsv
│  └─ port/             interfaces, e.g. AttributionSourcePort, AttributionRepositoryPort
└─ adapter/
   ├─ in/web/           REST controllers
   ├─ out/persistence/  JPA entities and repositories
   └─ out/<service>/    external clients, e.g. out/amazon, out/csv
```

| Strictness | Modules | Rule |
| --- | --- | --- |
| **Strict** | channel (Google), attribution (Amazon API + CSV), reporting, ad matching | Full layering; domain is pure Kotlin; persistence entities are mapped to domain models |
| **Light** | auth, account, catalog (brands, products), audit | Same folders, fewer layers; simple CRUD may use persistence entities directly in use cases |

Examples of ports: `AdChannelPort` (Google now; Meta, TikTok later), `AttributionSourcePort` (Amazon API or CSV upload behind one interface).

The rules are enforced by architecture tests (Spring Modulith or ArchUnit): the build fails if a domain package imports Spring, JPA or an API client, or if one module reaches into another module's internals.

| Layer | Choice | Why |
| --- | --- | --- |
| Backend | Spring Boot 4, Kotlin 2, JDK 21 (downloaded by the Gradle toolchain) | Long-lived, typed, scales well |
| Build | Gradle (Kotlin DSL) with wrapper | No global Gradle install needed |
| Frontend | React, TypeScript, Vite (single-page app); React Router, TanStack Query, Tailwind CSS + shadcn/ui, Recharts, React Hook Form + Zod, OpenAPI-generated client | Dashboard sits behind login; no server rendering needed; widely used, maintained libraries |
| Database | PostgreSQL 18, Flyway migrations | Relational data with strict per-account isolation |
| Background jobs | JobRunr (or Spring Scheduler + ShedLock) | Retries; one run per job across instances |
| Google client | Official Google Ads API Java client library | Maintained by Google; works from Kotlin |
| Amazon client | Spring WebClient against the REST API | No official JVM client for Attribution |
| Auth | Spring Security, JWT access + refresh tokens | Stateless backend |
| Local dev | Docker Compose for PostgreSQL | Same database version for every developer |
| Hosting | Docker containers on AWS or GCP, managed PostgreSQL | Decide before beta |

## 8. External APIs (verified 2026-10-04)

### Google Ads API

Access is tied to the Google Cloud project ([access levels](https://developers.google.com/google-ads/api/docs/api-policy/access-levels)).

| Level | Accounts | Daily operations | How to get it |
| --- | --- | --- | --- |
| Test | Test accounts only | 15,000 | Automatic when the API is enabled |
| Explorer | Production and test | 2,880 on production | Apply in Google Cloud Console |
| Basic | Production and test | 15,000 | Brand verification, then apply |
| Standard | Production and test | Unlimited | Manual audit, about 10 business days |

Development uses Test access. Explorer blocks keyword planning (needed by the 1b wizard), so **Basic** is the Phase 1 target.

### Amazon Ads API: Amazon Attribution

Offered through tool providers integrated with the Amazon Ads API ([Amazon guide](https://advertising.amazon.com/library/guides/basics-of-amazon-attribution)). Amazfy registers once; sellers grant access with Login with Amazon.

| Endpoint | Purpose |
| --- | --- |
| GET /attribution/advertisers | List the seller's Attribution advertisers |
| GET /attribution/publishers | List supported publishers (Google Ads is one) |
| GET /attribution/tags/macroTag | One macro tag per publisher; Google fills in campaign, ad group and ad IDs |
| GET /attribution/tags/nonMacroTemplateTag | Template tags for publishers without macros |
| POST /attribution/report | Grouped by CAMPAIGN, ADGROUP or CREATIVE, or a PRODUCTS report |

Metrics: click-throughs, detail page views, add to carts, purchases, units sold, sales; 14-day window. Eligible: Brand Registry professional sellers and vendors in US, CA, UK, DE, FR, IT, ES. Endpoint list taken from client libraries ([python-amazon-ad-api](https://python-amazon-ad-api.readthedocs.io/en/latest/api/attribution.html), [Go SDK](https://pkg.go.dev/github.com/oh0123/amazon-advertise-sdk/codegen/attribution)); confirm against Amazon's reference once developer access is granted.

**CSV fallback:** Amazon confirms Attribution reports are downloadable from the Attribution console. Collect a real export before building the importer; its columns differ from the MVP sample.

### Open risks

- Whether Attribution needs extra access beyond normal Ads API approval, and Amazon's review time.
- Whether Google macro tags produce Attribution rows per ad (creative) or only per ad group.
- Attribution data arrives late and changes during the 14-day window; recent days are re-synced daily.

## 9. Non-functional requirements

- **Security:** OAuth tokens encrypted at rest with a key from a secrets manager; never in code or logs. Passwords hashed with Argon2 or bcrypt. HTTPS only.
- **Tenant isolation:** every query scoped by account; an automated test proves one account cannot read another's data.
- **Least privilege:** request only the OAuth scopes needed. Amazfy never touches Google billing.
- **Audit log:** every change written to Google (tag set, campaign created, budget changed) records who approved it and when.
- **Sync reliability:** retries with backoff, respects rate limits, safe to re-run.
- **Freshness:** last successful sync time shown per connection.
- **Scalability:** stateless backend; several instances can run; each sync job runs once.
- **Privacy:** privacy policy and account deletion that removes data and revokes tokens (needed for Google brand verification and Amazon approval).
- **Observability:** structured logs, error tracking, alerts on repeated sync failures.

## 10. Approvals checklist (start in week 1)

- [ ] Company details ready: legal name, address, support email
- [ ] Public website on an Amazfy domain, with privacy policy and terms of service
- [ ] Google Cloud project created; Google Ads API enabled (Test access)
- [ ] Google Cloud brand verification completed
- [ ] Google Ads API Explorer, then Basic access requested
- [ ] Google OAuth consent screen verified for the Google Ads scope
- [ ] Google Ads manager account and test accounts created
- [ ] Login with Amazon security profile created
- [ ] Amazon Ads API access application submitted, naming Amazon Attribution as the use case
- [ ] One friendly seller recruited to test Attribution end to end
- [ ] A real Amazon Attribution report export collected

## 11. Open questions

- [ ] Do campaigns created by the wizard always start paused, or does the seller choose at launch?
- [ ] Does the company already have an Amazon Ads API developer account?
- [ ] Confirm US as the first marketplace.
- [ ] Confirm no payment collection for Amazfy is needed during the beta.
- [ ] Who owns the Amazfy domain and brand assets needed for Google brand verification?
