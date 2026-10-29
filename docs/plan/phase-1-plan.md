# Phase 1 development plan

Last updated: 2026-10-11 · Team: 1 developer · Scope: [Phase 1 requirements](../requirements/phase-1-requirements.md)

Phase 1a ships first: real sellers can connect Google Ads and Amazon and see ROAS. Phase 1b (campaign wizard) follows. API approvals run in parallel from week 1, because they take longer than the early milestones.

## Progress

Current milestone: **M3 reporting core + CSV import** (M0, M1 and M2 complete). Update this section whenever a step is merged.

| Step | Status |
| --- | --- |
| Docs, CLAUDE.md, GitHub repo, `.gitattributes` | Done |
| Backend skeleton: Spring Boot 4.1 + Kotlin 2.3, JDK 21 toolchain, Gradle wrapper | Done |
| PostgreSQL 18 via Docker Compose, Spring Data JPA, Flyway (V1 baseline), Testcontainers | Done |
| ktlint + `.editorconfig`, GitHub Actions CI (backend), Dependabot | Done |
| OpenAPI contract `api/openapi.yaml` + Kotlin interface generation; `GET /api/v1/system/info` | Done |
| RFC 7807 errors, Spring Security baseline (deny by default, stateless, CORS), local/prod config | Done |
| Frontend scaffold: Vite 8 + React 19 + TypeScript (Vite template), placeholder page | Done |
| Frontend styling: Tailwind CSS 4 + shadcn/ui (Radix, Nova preset), `@/` alias, TypeScript strict | Done |
| Frontend data: Orval-generated client + TanStack Query hooks, React Router, Vite `/api` proxy; home page shows backend version | Done |
| Frontend quality: oxlint (warnings as errors) + Prettier with Tailwind plugin; frontend job in CI; Dependabot for npm | Done |
| Backend Dockerfile: multi-stage, JRE runtime, non-root, layered jar; image build in CI | Done |
| Architecture tests | Moved to M1 (done there) |
| Branch protection on `main`: ruleset requiring a PR and the three CI checks, no force pushes | Done |

**M1: auth and accounts**

| Step | Status |
| --- | --- |
| API contract: Auth, Me, Accounts endpoints in `api/openapi.yaml` (password min 10; login allowed before email verification; 409 on duplicate sign-up) | Done |
| Architecture tests (ArchUnit, 4 rules) | Done |
| Database migrations: V2 users, identities, refresh tokens; V3 accounts, memberships (uuidv7 ids, constraints tested) | Done |
| Account module core: domain, AccountService, JPA adapter (create with owner, list, role, rename) | Done |
| Auth core: users, bcrypt passwords, register and login rules (AuthService), business error mapping | Done |
| Email + password endpoints: JWT access token, register/login/refresh/logout/logout-all over HTTP, refresh rotation with reuse detection, security wiring, field errors on 400 | Done |
| Mailpit in Docker Compose, Spring mail starter, `MAIL_*` / `FRONTEND_BASE_URL` settings (mail health check off) | Done |
| Database migration: V4 one-time tokens (email verification, password reset; hashed, expiring, single use) | Done |
| Email-sending port `AuthEmailSender` + SMTP adapter (plain-text verification and reset emails, links to frontend pages), tested against a Mailpit container | Done |
| Email verification: sign-up sends a 24 h link (after commit, so a mail outage never undoes a sign-up), `verify-email` (single use), `resend-verification` (old links stop working); resend rate limit comes with brute-force limits | Done |
| Password reset: `forgot-password` (always 202), `reset-password` (1 h single-use link, logs out all sessions, counts as email verification); emails sent in the background so timing reveals nothing | Done |
| Brute-force limiter: V5 `auth_attempts` (hashed keys, hourly clean-up), `AttemptLimiter` (sliding window), 429 with `Retry-After` | Done |
| Brute-force limits applied: failed logins 5/15 min per email (cleared on success) and 20/15 min per IP; forgot-password 3/h per email and 10/h per IP; resend-verification 3/h per user; client IP via `server.forward-headers-strategy=native` in prod | Done |
| Account access: `accountAccess` (`AccountAccessGuard`: canView/canEdit/canManage) + `@PreAuthorize`, 403 for non-members; `GET /me`, `POST /accounts`, `GET`/`PATCH /accounts/{id}` | Done |
| `@TenantId` filtering + tenant-isolation tests: moved to M2, where brands are the first account-owned table to filter | Moved to M2 |
| Google login, part 1: settings (optional; 501 without a client id), `secrets-local.properties` (git-ignored), setup guide, `GET /auth/google` redirect with state + PKCE + nonce cookie | Done |
| Google login, part 2: callback checks state (cookie), exchanges the code (PKCE + secret), verifies the ID token (signature via Google's keys, issuer, audience, expiry, nonce); cancel → `/login`, failure → `/login?error=google`; tested against a fake Google | Done |
| Google login, part 3: user by Google `sub`, else link by verified email (never-verified password users lose their password and sessions), else new user → `/onboarding`; refresh cookie set on the callback | Done |
| Frontend F1: `apiFetch` (access token in memory, auto-refresh on 401, `ApiError`), session restore on page load, `RequireAuth`, Login page, home with accounts and logout | Done |
| Frontend F2: sign-up, verify-email (+ reminder banner with resend on home), forgot/reset-password pages, shared `AuthCard`/`Field` form pieces | Done |
| Frontend F3: "Continue with Google" button, onboarding (`RequireAuth` sends users without an account to `/onboarding`), current account (`useCurrentAccount`, remembered in localStorage) with switcher and "Add account" | Done |

**M2: catalog**

| Step | Status |
| --- | --- |
| API contract: Brands and Products under `/api/v1/accounts/{accountId}/...` (marketplaces US, CA, UK, DE, FR, IT, ES; ASIN + marketplace unique; ASIN/marketplace fixed after creation; a brand with products can't be removed); placeholder `CatalogController` with access rules (501) | Done |
| `@TenantId` account filtering (`CurrentAccount`, resolver, interceptor for `/accounts/{accountId}/...`), V6 brands table, brand persistence, tenant-isolation tests | Done |
| Brand endpoints: list (by name), create, rename, delete; 409 for a duplicate name (any case), 404 for unknown or other accounts' brands; `CatalogService` | Done |
| V7 products table (ASIN format, 7 marketplaces, unique ASIN + marketplace per account, brand must be in the same account via composite foreign key, brands with products can't be deleted); product domain (`Asin`, `Marketplace` with Amazon domain and currency), persistence, isolation tests | Done |
| Product endpoints: list (by brand, then name; optional brand filter), create (400 for a brand outside the account, 409 for a duplicate ASIN + marketplace), rename/move, delete; real brand product counts; 409 when removing a brand with products | Done |
| Frontend: `AppLayout` (header, account switcher, page links) around logged-in pages; Brands page (list with product counts, add, rename, remove with confirmation; read-only for viewers) | Done |
| Frontend: Products page (filter by brand via `?brand=`, add with ASIN + marketplace, edit name/brand, remove with confirmation, link to the Amazon listing); brand product counts link to it | Done |
| Account region (follow-up): V8 `accounts.region` (NA/EU, existing accounts NA, fixed after creation), chosen at sign-up and "Add Amazon seller account"; products only on the region's marketplaces (400); shared `common/amazon` Region + Marketplace; product form shows only the region's marketplaces | Done |
| Error page (follow-up): `ErrorPage` for crashed pages (full page, or inside the header for app pages; details only in the console), unknown regions don't crash; Dependabot skips Kotlin 2.4 until ktlint supports it | Done |

**M3: reporting core + CSV import**

No Google connection yet (M4) and no real Attribution export yet: the dashboard is built on sample data, and the
Attribution table follows the report's documented fields (Amazon's reporting API and connector docs agree on them).
The CSV importer reads columns through a name mapping and is checked against a real export before A10 counts as done.

| Step | Status |
| --- | --- |
| V9 channel tables: campaigns → ad groups → ads (channel + external id; ad links to a product, a brand or nothing; removing a product makes its ads unmatched), daily spend per ad (micros, currency; one row per ad and day) | Done |
| V10 Attribution table: daily numbers per Attribution campaign / ad group / creative and marketplace (click-throughs, detail page views, add to carts, purchases, units, sales), linked to the Google ad when matched, kept when not; one row per reported line and day | Done |
| Sample data for local development: `POST /accounts/{id}/dev/sample-data` (local profile only, owner only) replaces the account's ads and numbers with 90 days of realistic data, incl. Brand Store, unmatched ad and unmatched Attribution line; adds sample products if none; "Fill with sample data" on the home page in the dev build | Done |
| Reporting API: `GET /accounts/{id}/reports/summary` for a date range (spend, sales, orders, ROAS, CPA, conversion rate; per currency; default last 30 days up to yesterday; at most 366 days), strict `reporting` module with SQL totals | Done |
| Reporting API: drill-down `GET /accounts/{id}/reports/breakdown?by=…` (brand → product → campaign → ad group → ad; filters for drilling; BRAND_LEVEL, NOT_LINKED and UNMATCHED rows so every level adds up to the summary) | Done |
| Reporting API: daily trend `GET /accounts/{id}/reports/trend` (all totals per day, one series per currency, every day of the range with zeros on empty days; same filters as the drill-down, plus `adId` in both) | Done |
| Frontend: dashboard summary on the home page (spend, sales, ROAS, purchases, CPA, conversion rate per currency; last 7/30/90 days or custom days, kept in the URL; note while the 14-day attribution window is open) | Done |
| Frontend: drill-down table under the summary (view by brand, product or campaign; click a row to go one level down to the ads; path above the table to go back) | Done |
| Frontend: trend chart (sales and spend per day for the whole account or the drill-down selection; last 14 days shaded as still being counted; hover or arrow keys for a day's numbers; daily table) | Done |
| Frontend: sort the drill-down table by any column (numbers biggest first; missing values and the unmatched row last; currencies never ranked together; kept while drilling) | Done |
| Report performance, part 1: `./gradlew performanceTest` times every report for a large seller (500 products, 5,000 ads, 366 days = 1.83M rows each of spend and Attribution; target under 500 ms). Drill-down ads passed as one array parameter (an IN list broke past 65,535 ads). Baseline: whole-account reports 0.5–1.8 s; the database alone needs ~0.8 s to add up a year per ad, and covering indexes don't help (one account's year is read in full) | Done |
| Report performance, part 2: rollup tables (V11): totals per account and day (summary, whole-account trend) and per ad and month (drill-down, loose days at the ends from the daily rows); refreshed by `RefreshReportTotals` in the writer's transaction. Large seller, 366 days: summary 668 → 18 ms, trend 661 → 28 ms, breakdowns 1.0–1.8 s → 0.3–0.43 s; performanceTest passes | Done |
| Attribution CSV import, backend (A10): `POST /accounts/{id}/attribution/imports` (owner/editor, one marketplace); strict `attribution` module; columns found by header names (aliases, header below report info lines); Google lines only; all or nothing with the bad lines named; lines matched to ads by Google's campaign / ad group / ad ids, else unmatched; re-upload updates (upsert), report totals refreshed | Done |
| Attribution CSV import, frontend: Import page (owner/editor) with marketplace choice; the file is first read as a preview (`dryRun=true`: lines, days, matched share, sales; nothing saved), then imported on confirmation; warning when under half the lines match the account's ads | Done |
| Attribution CSV import: manual column mapping when a header isn't recognised | To do (with the real export) |
| Verify the CSV import against a real Attribution export; add its header names | To do |

## Backlog

Agreed ideas without a milestone yet, or waiting for something. Moved into Progress when picked up.

| Idea | Why | Waiting for |
|---|---|---|
| Clearer names for the special drill-down rows, e.g. "Ads with an unreadable link" and "Amazon sales without a known ad" | The current names don't say which side (Google ad or Amazon sale) is missing | Low priority; check the wording with the seller Amazfy is built with |
| CSV import: check a marketplace or currency column against the chosen marketplace | A Canada report uploaded as US would count CAD as USD | A real export (does it have such a column?) |
| CSV import: remove header aliases the real export doesn't use (e.g. "clicks", "campaign") | Generic names could match the wrong column | A real export |
| Import history: who uploaded which file, when, for which days | A wrong upload can be traced | M6 audit log |
| Undo an import | Upserts overwrite earlier numbers; re-uploading the right file repairs updated lines but not added ones | Only if real use shows the need |

## Repository layout

```
amazfy/
├─ backend/        Spring Boot 4 + Kotlin, Gradle (Kotlin DSL)
│  └─ src/main/kotlin/com/amazfy/
│     ├─ common/        shared: web error handling, security config
│     ├─ system/        service info endpoint
│     ├─ auth/          users, login, JWT, memberships
│     ├─ account/       accounts, account switching
│     ├─ catalog/       brands, products
│     ├─ channel/       common channel interface
│     │  └─ google/     Google Ads connector
│     ├─ attribution/   Amazon Attribution connector + CSV import
│     ├─ reporting/     aggregation, ROAS/CPA
│     ├─ sync/          scheduled jobs, sync runs
│     └─ audit/         audit log
├─ frontend/       React + TypeScript + Vite
├─ api/            openapi.yaml: the API contract, source for generated code
├─ docs/           requirements, guides, plan
└─ docker-compose.yml   PostgreSQL for local development
```

## Milestones

| # | Milestone | Delivers | Depends on |
| --- | --- | --- | --- |
| M0 | Project setup | Backend and frontend skeletons, Postgres via Docker Compose, Flyway, OpenAPI code generation, production-grade baseline (see below) | Docker Desktop and Node 22 installed |
| M1 | Auth and accounts | Own authentication: email + password sign-up and login, email verification, password reset, rotating refresh tokens, brute-force limits, Google login; Mailpit for local email. Accounts + memberships, `accountAccess` authorization, `@TenantId`, account switcher, tenant-isolation tests (A1, A2) | M0 |
| M2 | Catalog | `@TenantId` account filtering and tenant-isolation tests (from M1); brands and products CRUD with ASIN + marketplace validation; UI pages (A5) | M1 |
| M3 | Reporting core + CSV import | Campaign/AdGroup/Ad and daily metric tables; Attribution CSV import; dashboard summary, drill-down and trend chart on imported data (A10–A13) | M2, a real Attribution export (sample data until then) |
| M4 | Google Ads connector | Google OAuth, pick Google Ads account, sync campaigns/ad groups/ads and daily spend per ad, ASIN auto-matching, unmatched-ads screen (A4, A6, A7, A9 spend) | M3, Google Test access |
| M5 | Amazon connector | Login with Amazon, pick advertiser, macro tag fetch and tracking-template update with approval, daily Attribution sync re-pulling 14 days (A3, A8, A9 sales) | M4, Amazon Ads API approval |
| M6 | Hardening and beta | Sync status and errors (A14), audit log, encryption with secrets manager, deployment, onboarding checklist, privacy/deletion | M5 |
| — | **Phase 1a beta with 1–3 real sellers** | | Google Basic access, Amazon approval |
| M7 | Campaign wizard | Wizard: new or existing campaign, keyword suggestions, responsive search ad editor, budget, launch (B1–B6) | M6, Google Basic access |
| M8 | Campaign management | Pause/resume, budget change, policy errors (B7, B8) | M7 |

M3 comes before the connectors on purpose: the dashboard can be built and tested on CSV and sample data while API approvals are pending.

## How each milestone is built

1. **Branch:** one branch per milestone or smaller step, merged to `main` through a pull request.
2. **API contract first:** add the milestone's endpoints to `api/openapi.yaml` and review them before any implementation.
3. **Generate code:** Kotlin controller interfaces (backend) and the TypeScript client (frontend) are generated from the spec.
4. **Implement:** database migration → domain → use cases → adapters → UI, in small explained steps.
5. **Test and merge:** CI must pass; the definition of done below must hold.

## M0 production-grade baseline

| Area | Included in M0 |
| --- | --- |
| Repository | Git on GitHub, `main` protected, pull requests for every change, LF line endings |
| Code quality | ktlint (Kotlin, in M0); detekt added once its Kotlin 2.3-compatible 2.0 release is stable; oxlint and Prettier (frontend; oxlint is Vite's default, replacing ESLint) |
| Tests | JUnit 5 unit tests; integration tests with Testcontainers PostgreSQL; architecture tests (ArchUnit) |
| CI | GitHub Actions: build, checks and tests for backend and frontend on every push and pull request |
| Database | PostgreSQL 18 in Docker Compose for local development; Flyway migrations only |
| API | `api/openapi.yaml`, code generation for Kotlin and TypeScript, `/api/v1` paths, RFC 7807 errors |
| Configuration | Spring profiles per environment (local, test, production); secrets from environment variables, never committed |
| Operations | Spring Actuator health checks, structured JSON logs in production, Dockerfile for the backend |
| Security | Spring Security baseline, CORS for the frontend origin, Dependabot dependency updates |

Not in Phase 1: microservices, Kubernetes, message queues. The modular, hexagonal structure keeps them possible later.

## Parallel track: approvals (owner: company + developer)

Start in week 1; see the checklist in the requirements, section 10. The critical items are Google Cloud brand verification, Google Ads API Basic access, the Amazon Ads API application, and one friendly seller for an end-to-end Attribution test.

## Definition of done (every milestone)

- Feature works end to end in the local environment.
- Automated tests for business logic and API endpoints pass in CI.
- Every query is scoped by account; tenant-isolation test still passes.
- Database changes are Flyway migrations, never manual edits.
- Docs updated if behaviour or setup changed.

## Local prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 17+ to run Gradle | Gradle downloads JDK 21 for compiling (toolchain) |
| Node.js | 22 LTS (20.19+ minimum for current Vite) | |
| Docker Desktop | current | Needed for the local database and for backend tests (Testcontainers) |
| Git | any recent | |
| Gradle | not needed globally | project uses the Gradle wrapper |

Lead developer's machine (2026-10-11): Windows 11, JDK 17, Node 22.23, Docker Desktop 29.8, IntelliJ IDEA Ultimate and VS Code.
