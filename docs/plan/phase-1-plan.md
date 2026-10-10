# Phase 1 development plan

Last updated: 2026-10-10 · Team: 1 developer · Scope: [Phase 1 requirements](../requirements/phase-1-requirements.md)

Phase 1a ships first: real sellers can connect Google Ads and Amazon and see ROAS. Phase 1b (campaign wizard) follows. API approvals run in parallel from week 1, because they take longer than the early milestones.

## Repository layout

```
amazfy/
├─ backend/        Spring Boot 3 + Kotlin, Gradle (Kotlin DSL)
│  └─ src/main/kotlin/com/amazfy/
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
├─ docs/           requirements, guides, plan
└─ docker-compose.yml   PostgreSQL for local development
```

## Milestones

| # | Milestone | Delivers | Depends on |
| --- | --- | --- | --- |
| M0 | Project setup | Repo, backend and frontend skeletons, Postgres via Docker Compose, Flyway, CI build, README | Docker Desktop and JDK 21 installed |
| M1 | Auth and accounts | Sign-up (email, password, account name), login, JWT refresh, accounts + memberships, account switcher, tenant-isolation test (A1, A2) | M0 |
| M2 | Catalog | Brands and products CRUD with ASIN + marketplace validation; UI pages (A5) | M1 |
| M3 | Reporting core + CSV import | Campaign/AdGroup/Ad and daily metric tables; Attribution CSV import; dashboard summary, drill-down and trend chart on imported data (A10–A13) | M2, a real Attribution export (sample data until then) |
| M4 | Google Ads connector | Google OAuth, pick Google Ads account, sync campaigns/ad groups/ads and daily spend per ad, ASIN auto-matching, unmatched-ads screen (A4, A6, A7, A9 spend) | M3, Google Test access |
| M5 | Amazon connector | Login with Amazon, pick advertiser, macro tag fetch and tracking-template update with approval, daily Attribution sync re-pulling 14 days (A3, A8, A9 sales) | M4, Amazon Ads API approval |
| M6 | Hardening and beta | Sync status and errors (A14), audit log, encryption with secrets manager, deployment, onboarding checklist, privacy/deletion | M5 |
| — | **Phase 1a beta with 1–3 real sellers** | | Google Basic access, Amazon approval |
| M7 | Campaign wizard | Wizard: new or existing campaign, keyword suggestions, responsive search ad editor, budget, launch (B1–B6) | M6, Google Basic access |
| M8 | Campaign management | Pause/resume, budget change, policy errors (B7, B8) | M7 |

M3 comes before the connectors on purpose: the dashboard can be built and tested on CSV and sample data while API approvals are pending.

## Parallel track: approvals (owner: company + developer)

Start in week 1; see the checklist in the requirements, section 10. The critical items are Google Cloud brand verification, Google Ads API Basic access, the Amazon Ads API application, and one friendly seller for an end-to-end Attribution test.

## Definition of done (every milestone)

- Feature works end to end in the local environment.
- Automated tests for business logic and API endpoints pass in CI.
- Every query is scoped by account; tenant-isolation test still passes.
- Database changes are Flyway migrations, never manual edits.
- Docs updated if behaviour or setup changed.

## Local prerequisites

| Tool | Version | Status on the lead developer's machine (2026-10-10) |
| --- | --- | --- |
| JDK | 21 LTS (17 works) | 17 installed |
| Node.js | 22 LTS (20.19+ minimum for current Vite) | 18.16 installed: upgrade needed |
| Docker Desktop | current | not installed |
| Git | any recent | installed |
| Gradle | not needed globally | project uses the Gradle wrapper |
