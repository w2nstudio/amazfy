# Phase 1 development plan

Last updated: 2026-10-11 · Team: 1 developer · Scope: [Phase 1 requirements](../requirements/phase-1-requirements.md)

Phase 1a ships first: real sellers can connect Google Ads and Amazon and see ROAS. Phase 1b (campaign wizard) follows. API approvals run in parallel from week 1, because they take longer than the early milestones.

## Progress

Current milestone: **M1 auth and accounts** (M0 complete). Update this section whenever a step is merged.

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
| Database migrations: users, identities, refresh tokens, accounts, memberships | Next |
| Email + password: register, login, refresh, logout | To do |
| Email verification, password reset, Mailpit | To do |
| Brute-force limits | To do |
| Accounts, memberships, `accountAccess`, `@TenantId`, tenant-isolation tests | To do |
| Google login | To do |
| Frontend: login, sign-up, onboarding, account switcher | To do |

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
| M2 | Catalog | Brands and products CRUD with ASIN + marketplace validation; UI pages (A5) | M1 |
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
