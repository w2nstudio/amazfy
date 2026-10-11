# Amazfy

SaaS for Amazon sellers: run ads outside Amazon and measure the Amazon sales they produce (external ROAS). Phase 1 = Google Ads + Amazon Attribution.

**Start of a session:** read the Progress section of `docs/plan/phase-1-plan.md` to see what is done and what is next.

## Docs

- `docs/requirements/phase-1-requirements.md`: scope, user stories, data model, architecture. Source of truth.
- `docs/plan/phase-1-plan.md`: milestones. Work milestone by milestone; mark progress there.
- `docs/guides/google-ads-primer.md`: how Google Ads maps to Amazon products.
- `docs/guides/openapi-contract-first.md`: how the API contract and code generation work; steps to add an endpoint.
- `docs/guides/business-glossary.md`: Amazon and Google Ads terms. **Append-only**: add new terms at the end (`**Term** — explanation.`) without reading or rewriting the file.

## Working style

- Make small changes, one focused step at a time, and explain each: what changed and why.
- When an Amazon or Google Ads business concept comes up, explain it in plain words with an example, and append it to the glossary.
- Keep docs current as part of every change: this file (rules, commands), the plan's Progress section (mark merged steps), and the requirements (decisions). A new session must be able to continue from the docs alone.
- The developer is new to the Amazon/ads domain and to some tools (e.g. OpenAPI); explain new tools and concepts briefly when introducing them.

## Git workflow

- Never commit to `main` directly. One branch per step (`m0/...`, `m1/...`, `docs/...`), pushed to GitHub; the developer opens and merges the pull request (no `gh` CLI installed). Give the PR link and a short suggested description.
- Remote: `git@github-w2n:w2nstudio/amazfy.git` (SSH alias `github-w2n` for the w2nstudio GitHub account). Commit email for this repo: `w2nstudio16@gmail.com`.
- After the developer says a PR is merged: `git fetch` and **confirm `origin/main` contains the change** before switching to `main` and deleting the branch locally and remotely.
- CI (`.github/workflows/ci.yml`) must be green before merging.

## Domain naming (use everywhere: code, schema, UI, docs)

- **User** = a person who logs in. Never call it "account".
- **Account** = one seller business = exactly one Amazon seller account. Users ↔ accounts are many-to-many via **Membership** (role: owner in Phase 1).
- Hierarchy: Account → Brand → Product (unique by ASIN + marketplace).
- Google structure: Campaign → AdGroup → Ad. The **Ad** is linked to a product (ASIN parsed from its final URL) or, for Brand Store links, only to a brand.
- Always write "Google Ads account" and "Amazon seller account" in full.

## Architecture

- Modular monolith, Spring Boot 4 + Kotlin (JDK 21), PostgreSQL. No microservices.
- Hexagonal modules: `domain/` (pure Kotlin) · `application/` (use cases) + `application/port/` (interfaces) · `adapter/inbound/web`, `adapter/outbound/persistence`, `adapter/outbound/<service>` (not `in`/`out`: `in` is a Kotlin keyword).
- **Strict** (domain has no Spring, JPA or API-client imports): channel (Google), attribution, reporting, ad matching.
- **Light** (simple CRUD may skip layers): auth, account, catalog, audit, system.
- `common/` holds shared infrastructure only (error handling, security config), no business logic.
- Modules talk to each other only through their public application API, never another module's internals. Enforced by architecture tests.
- Every table has `account_id`; every query is scoped to the current account.
- Daily metrics are stored per ad per day; higher levels are sums.

## API

- Contract-first: write/update `api/openapi.yaml` for a milestone's endpoints **before** implementing them.
- Kotlin controller interfaces and the TypeScript client are generated from the spec; never hand-edit generated code.
- Paths are versioned (`/api/v1/...`); errors use RFC 7807 problem details (`common/web/GlobalExceptionHandler`; never expose internal messages).
- Security: every endpoint requires authentication unless added to the public list in `common/security/SecurityConfig`.
- Authentication is our own (M1), built to be swappable for a managed provider: other modules only verify JWTs and use the user ID. Authorization goes through `accountAccess` + `@PreAuthorize`; account-scoped paths are `/api/v1/accounts/{accountId}/...`. See requirements section 3.

## Conventions

- Database changes only through Flyway migrations.
- Secrets never in code, config files or logs; OAuth tokens encrypted at rest.
- Config: `application.properties` (shared, values only from environment variables) · `application-local.properties` (local defaults, the default profile) · `application-prod.properties`. A new required setting goes into `requiredSettings` in `AmazfyBackendApplication.kt` and gets a local default.
- Tests: unit tests for domain and use cases; integration tests with Testcontainers PostgreSQL.

## Commands

Backend (run in `backend/`; Windows: `gradlew.bat`):

- `./gradlew build` — compile, ktlint check, run tests, build the jar (same as CI)
- `./gradlew openApiGenerate` — regenerate API interfaces from `api/openapi.yaml` (also runs automatically before compiling; output in `build/generated/openapi`, never committed)
- `./gradlew ktlintFormat` — auto-fix formatting (style: `.editorconfig`, ktlint_official)
- `./gradlew test` — tests only
- `./gradlew bootRun` — start on http://localhost:8080 (health: `/actuator/health`)

Local database (run in repo root; needs Docker Desktop running):

- `docker compose up -d` — start PostgreSQL 18 on localhost:5432 (db/user/password: `amazfy`)
- `docker compose down` — stop it (data is kept in a Docker volume; `down -v` deletes it)

Backend tests start their own throwaway PostgreSQL with Testcontainers; only Docker must be running.
Migrations live in `backend/src/main/resources/db/migration` (`V<n>__<description>.sql`); never edit a merged one.

Frontend (run in `frontend/`; Node 22):

- `npm install` — install dependencies (after cloning or when `package.json` changes)
- `npm run dev` — dev server on http://localhost:5173 with hot reload; `/api` is proxied to the backend on :8080 (start it with `bootRun`)
- `npm run generate:api` — regenerate the TypeScript client and TanStack Query hooks from `api/openapi.yaml` with Orval (also runs automatically before `dev` and `build`; output in `src/api/generated/`, never committed)
- `npm run build` — TypeScript check + production build into `dist/`
- `npm run lint` — oxlint
- `npx shadcn@latest add <component>` — add a shadcn/ui component (e.g. `input`, `table`, `dialog`); its source is copied into `src/components/ui/` and is ours to edit

Frontend conventions: call the backend only through the generated hooks (e.g. `useGetSystemInfo()`), never hand-written `fetch`; pages live in `src/pages/` and are registered in `src/router.tsx`; TypeScript `strict`; style with Tailwind classes and shadcn/ui components (theme colours in `src/index.css`); import from `src` with the `@/` alias (`@/components/ui/button`).
