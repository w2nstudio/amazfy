# Amazfy

SaaS for Amazon sellers: run ads outside Amazon and measure the Amazon sales they produce (external ROAS). Phase 1 = Google Ads + Amazon Attribution.

**Start of a session:** read the Progress section of `docs/plan/phase-1-plan.md` to see what is done and what is next.

## Docs

- `docs/requirements/phase-1-requirements.md`: scope, user stories, data model, architecture. Source of truth.
- `docs/plan/phase-1-plan.md`: milestones. Work milestone by milestone; mark progress there.
- `docs/guides/google-ads-primer.md`: how Google Ads maps to Amazon products.
- `docs/guides/google-login-setup.md`: Google Cloud project, consent screen, OAuth client for "Sign in with Google"; local secrets go in `backend/secrets-local.properties` (git-ignored, loaded by the local profile).
- `docs/guides/google-login-implementation.md`: how Google login works in the code (flow, state/PKCE/nonce, code exchange, ID token checks, tests). Update it when the Google login code changes.
- `docs/guides/openapi-contract-first.md`: how the API contract and code generation work; steps to add an endpoint.
- `docs/guides/docker-guide.md`: Docker basics, how Amazfy uses Docker, commands, troubleshooting. Update it whenever Docker usage changes (the developer is learning Docker).
- `docs/guides/api-testing-guide.md`: testing endpoints by hand with Postman (import `api/openapi.yaml`, token scripts, walkthroughs per feature) or VS Code REST Client (`api/amazfy.http`). When an endpoint becomes usable, add a walkthrough and a request in `api/amazfy.http`.
- `README.md`: developer setup (install, run, check). Keep it working when setup steps change.
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
- In the UI the person signs up / logs in (never "create an account" for the login); what the account switcher lists and adds is an "Amazon seller account" ("Add Amazon seller account"); brands are a separate level inside it. Example names: seller account "AquaLife Inc. – North America", brands "AquaLife", "ClearTank".

## Architecture

- Modular monolith, Spring Boot 4 + Kotlin (JDK 21), PostgreSQL. No microservices.
- Hexagonal modules: `domain/` (pure Kotlin) · `application/` (use cases) + `application/port/` (interfaces) · `adapter/inbound/web`, `adapter/outbound/persistence`, `adapter/outbound/<service>` (not `in`/`out`: `in` is a Kotlin keyword).
- **Strict** (domain has no Spring, JPA or API-client imports): channel (Google), attribution, reporting, ad matching.
- **Light**: auth, account, catalog, audit, system. Same folders and ArchUnit rules as strict modules, but one application service per module (e.g. `AccountService`) instead of a class per use case, and minimal mapping. Example to copy: `account/`.
- JPA entities: plain classes (never `data class`), only in `adapter/outbound/persistence`, IDs via `@UuidGenerator(style = VERSION_7)`; adapters map them to domain classes. One entity per file named after it (`BrandEntity.kt`; an embedded id stays with its entity, e.g. `MembershipId` in `MembershipEntity.kt`); the Spring Data repository and the adapter live in `<Thing>PersistenceAdapter.kt`.
- `common/` holds shared infrastructure only (error handling, security config), no business logic.
- Modules talk to each other only through their public application API, never another module's internals. A module's API (its application service) returns its own API types defined in `application/` (e.g. `AccountSummary`, `Role`), never domain classes.
- These rules are enforced by ArchUnit tests in `backend/src/test/kotlin/com/amazfy/ArchitectureTest.kt` (domain pure Kotlin; application never uses adapters; generated API types only in inbound web adapters; no access to another module's adapter or domain). If a rule blocks a design you need, discuss it rather than weakening the rule.
- Every account-owned table has `account_id`; its JPA entity marks it `@TenantId` (`var accountId: UUID? = null`, never set by hand). Hibernate then filters every query and fills every insert with `CurrentAccount` (`common/tenant`), which `AccountScopeInterceptor` sets for each `/api/v1/accounts/{accountId}/...` request; background jobs use `CurrentAccount.runAs(id) { }`. Unset means `CurrentAccount.NONE`, which matches no rows. Ports of account-owned data take no account parameter. Plain SQL (reports) must filter `account_id` explicitly. Every new account-owned table gets isolation tests like `catalog/TenantIsolationTest`.
- Tests: never assert whole-table row counts; other tests leave committed rows behind.
- Daily metrics are stored per ad per day; higher levels are sums.

## API

- Contract-first: write/update `api/openapi.yaml` for a milestone's endpoints **before** implementing them.
- Kotlin controller interfaces and the TypeScript client are generated from the spec; never hand-edit generated code.
- Paths are versioned (`/api/v1/...`); errors use RFC 7807 problem details (`common/web/GlobalExceptionHandler`; never expose internal messages).
- Expected business errors extend `common.error.AppException` with an `ErrorKind` (e.g. CONFLICT → 409); their message is shown to clients, so keep it safe and user-friendly.
- Security: every endpoint requires a valid access token (JWT, checked by `common/security/JwtConfig`) unless listed in `SecurityConfig.PUBLIC_ENDPOINTS`; keep that list in sync with `security: []` in `api/openapi.yaml`. In authenticated endpoints get the user with `currentUserId()`.
- Tokens: access token 15 min (JWT, HS256, `JWT_SECRET`); refresh token 30 days, only in the `amazfy_refresh` HttpOnly cookie, stored as a SHA-256 hash, rotated on every use (reuse revokes the family). Use the injected `Clock`, never `Instant.now()`, in application code.
- Emails: the application calls the `AuthEmailSender` port and sends only after the transaction commits; the SMTP adapter sends on a background thread (`applicationTaskExecutor`) and logs failures without the address; Spring tests get `RecordingAuthEmailSender` (in `TestcontainersConfiguration`) instead of SMTP and read tokens from it.
- Brute-force limits: `AttemptLimiter` (auth) with `AttemptLimit`s defined in `AuthService`'s companion; over a limit it throws `TooManyAttemptsException` → 429 + `Retry-After`. The client IP comes from `request.remoteAddr` (proxy-aware in prod). Tests give each test its own fake IP (`remoteAddr`) so per-IP limits don't add up across tests.
- Contract endpoints not built yet throw `NotImplementedYetException` (501) until their step.
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

- `docker compose up -d` — start PostgreSQL 18 on localhost:5432 (db/user/password: `amazfy`) Mailpit (SMTP localhost:1025; sent emails readable at http://localhost:8025) and pgAdmin (http://localhost:5050, database browser; `docs/guides/pgadmin-guide.md`)
- `docker compose down` — stop it (data is kept in a Docker volume; `down -v` deletes it)

Backend container image (run in repo root): `docker build -f backend/Dockerfile -t amazfy-backend .` (multi-stage, JRE-only runtime, non-root user, `prod` profile by default; pass `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `JWT_SECRET`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, `FRONTEND_BASE_URL` with `-e`; inside a container the local database is `host.docker.internal:5432`).

Backend tests start their own throwaway PostgreSQL with Testcontainers; only Docker must be running.
Migrations live in `backend/src/main/resources/db/migration` (`V<n>__<description>.sql`); never edit a merged one.

Frontend (run in `frontend/`; Node 22):

- `npm install` — install dependencies (after cloning or when `package.json` changes)
- `npm run dev` — dev server on http://localhost:5173 with hot reload; `/api` is proxied to the backend on :8080 (start it with `bootRun`)
- `npm run generate:api` — regenerate the TypeScript client and TanStack Query hooks from `api/openapi.yaml` with Orval (also runs automatically before `dev` and `build`; output in `src/api/generated/`, never committed)
- `npm run build` — TypeScript check + production build into `dist/`
- `npm run lint` — oxlint (warnings fail too); config `.oxlintrc.json`
- `npm run format` / `npm run format:check` — Prettier (with Tailwind class sorting); config `.prettierrc.json`
- CI runs `npm ci`, `lint`, `format:check` and `build`; run the same before pushing
- `npx shadcn@latest add <component>` — add a shadcn/ui component (e.g. `input`, `table`, `dialog`); its source is copied into `src/components/ui/` and is ours to edit

Frontend conventions: call the backend only through the generated hooks (e.g. `useGetSystemInfo()`), never hand-written `fetch`. Every generated call goes through `src/api/client.ts` (`apiFetch`, set as Orval mutator): it adds the access token (kept in memory only, never localStorage), renews it once on a 401 via the refresh cookie, and throws `ApiError` (status, problem details, `Retry-After`) for 4xx/5xx; show `error.message` (the backend's safe `detail`). Pages needing a login go under the `RequireAuth` route in `src/router.tsx` (it also sends users without an account to `/onboarding`), and app pages go inside its `AppLayout` route (`src/components/AppLayout.tsx`: header, account switcher, page links; add a `PageLink` for a new page); account-scoped pages hide change controls from viewers (`role !== Role.VIEWER`), mirroring the backend; account-scoped pages get the account from `useCurrentAccount()` (`src/account/currentAccount.ts`, switched in the header's `AccountSwitcher`); `main.tsx` restores the session with one refresh before rendering; pages live in `src/pages/` and are registered in `src/router.tsx`; TypeScript `strict`; style with Tailwind classes and shadcn/ui components (theme colours in `src/index.css`); import from `src` with the `@/` alias (`@/components/ui/button`).
