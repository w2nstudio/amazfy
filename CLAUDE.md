# Amazfy

SaaS for Amazon sellers: run ads outside Amazon and measure the Amazon sales they produce (external ROAS). Phase 1 = Google Ads + Amazon Attribution.

## Docs

- `docs/requirements/phase-1-requirements.md`: scope, user stories, data model, architecture. Source of truth.
- `docs/plan/phase-1-plan.md`: milestones. Work milestone by milestone; mark progress there.
- `docs/guides/google-ads-primer.md`: how Google Ads maps to Amazon products.
- `docs/guides/business-glossary.md`: Amazon and Google Ads terms. **Append-only**: add new terms at the end (`**Term** — explanation.`) without reading or rewriting the file.

## Working style

- Make small changes, one focused step at a time, and explain each: what changed and why.
- When an Amazon or Google Ads business concept comes up, explain it in plain words with an example, and append it to the glossary.
- Update the docs when behaviour, setup or decisions change.

## Domain naming (use everywhere: code, schema, UI, docs)

- **User** = a person who logs in. Never call it "account".
- **Account** = one seller business = exactly one Amazon seller account. Users ↔ accounts are many-to-many via **Membership** (role: owner in Phase 1).
- Hierarchy: Account → Brand → Product (unique by ASIN + marketplace).
- Google structure: Campaign → AdGroup → Ad. The **Ad** is linked to a product (ASIN parsed from its final URL) or, for Brand Store links, only to a brand.
- Always write "Google Ads account" and "Amazon seller account" in full.

## Architecture

- Modular monolith, Spring Boot 4 + Kotlin (JDK 21), PostgreSQL. No microservices.
- Hexagonal modules: `domain/` (pure Kotlin) · `application/` (use cases) + `application/port/` (interfaces) · `adapter/in/web`, `adapter/out/persistence`, `adapter/out/<service>`.
- **Strict** (domain has no Spring, JPA or API-client imports): channel (Google), attribution, reporting, ad matching.
- **Light** (simple CRUD may skip layers): auth, account, catalog, audit.
- Modules talk to each other only through their public application API, never another module's internals. Enforced by architecture tests.
- Every table has `account_id`; every query is scoped to the current account.
- Daily metrics are stored per ad per day; higher levels are sums.

## API

- Contract-first: write/update `api/openapi.yaml` for a milestone's endpoints **before** implementing them.
- Kotlin controller interfaces and the TypeScript client are generated from the spec; never hand-edit generated code.
- Paths are versioned (`/api/v1/...`); errors use RFC 7807 problem details.

## Conventions

- Database changes only through Flyway migrations.
- Secrets never in code, config files or logs; OAuth tokens encrypted at rest.
- Tests: unit tests for domain and use cases; integration tests with Testcontainers PostgreSQL.

## Commands

Backend (run in `backend/`; Windows: `gradlew.bat`):

- `./gradlew build` — compile, ktlint check, run tests, build the jar (same as CI)
- `./gradlew ktlintFormat` — auto-fix formatting (style: `.editorconfig`, ktlint_official)
- `./gradlew test` — tests only
- `./gradlew bootRun` — start on http://localhost:8080 (health: `/actuator/health`)

Local database (run in repo root; needs Docker Desktop running):

- `docker compose up -d` — start PostgreSQL 18 on localhost:5432 (db/user/password: `amazfy`)
- `docker compose down` — stop it (data is kept in a Docker volume; `down -v` deletes it)

Backend tests start their own throwaway PostgreSQL with Testcontainers; only Docker must be running.
Migrations live in `backend/src/main/resources/db/migration` (`V<n>__<description>.sql`); never edit a merged one.

Frontend: to be added in M0.
