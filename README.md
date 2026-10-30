# Amazfy

Amazfy helps Amazon sellers run ads outside Amazon and measure the Amazon sales those ads produce (external ROAS). Phase 1 covers Google Ads with Amazon Attribution.

Stack: Spring Boot 4 + Kotlin backend, React + TypeScript (Vite) frontend, PostgreSQL 18, OpenAPI contract-first API.

## Set up a development environment

### 1. Install the tools

| Tool | Version | Why |
| --- | --- | --- |
| [Git](https://git-scm.com/) | recent | Source code |
| JDK | 17 or newer | Runs Gradle. Gradle downloads JDK 21 for compiling by itself. |
| [Node.js](https://nodejs.org/) | 22 LTS | Frontend |
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | current | Local database and backend tests. New to Docker? Read the [Docker guide](docs/guides/docker-guide.md). |

Gradle itself is **not** needed: the project includes the Gradle wrapper (`gradlew`).

Recommended editors: IntelliJ IDEA for the backend (Community is enough; Ultimate also covers the frontend) and VS Code for the frontend.

### 2. Get the code

```bash
git clone git@github.com:w2nstudio/amazfy.git
cd amazfy
```

### 3. Start the local database and mail server

Start Docker Desktop and wait for *Engine running*, then in the repository root:

```bash
docker compose up -d
```

This runs PostgreSQL 18 on `localhost:5432` (database, user and password: `amazfy`) and Mailpit, a fake email server. Tables are created automatically by Flyway when the backend starts. Emails the backend sends (e.g. verification links) never leave your machine: read them at http://localhost:8025. To look inside the database, open pgAdmin at http://localhost:5050 (see the [pgAdmin guide](docs/guides/pgadmin-guide.md)).

### 4. Run the backend

```bash
cd backend
./gradlew bootRun          # Windows PowerShell: .\gradlew.bat bootRun
```

The first run downloads Gradle, JDK 21 and libraries (a few minutes). The backend starts on http://localhost:8080 with the `local` profile, which already points at the Docker database. Check it: http://localhost:8080/api/v1/system/info.

### 5. Run the frontend

In a second terminal:

```bash
cd frontend
npm install                # first time, and after package.json changes
npm run dev
```

Open http://localhost:5173. The home page should show *Backend: amazfy-backend …*. In development, Vite forwards `/api` calls to the backend on port 8080.

### 6. Run the checks (same as CI)

```bash
cd backend && ./gradlew build                                   # compile, ktlint, all tests (Docker must be running)
cd frontend && npm run lint && npm run format:check && npm run build
```

### IDE setup

- **IntelliJ IDEA (Community or Ultimate):** open the repository root (`amazfy/`). The backend's Gradle build is one folder down, so link it once: right-click `backend/build.gradle.kts` → *Link Gradle Project*. Set *Settings → Build Tools → Gradle → Gradle JVM* to JDK 17 or 21. Generated API code appears after the first build; if types show red, build once and click *Reload All Gradle Projects*.
- **Frontend editor:** VS Code (or IntelliJ Ultimate). Community edition has no React/TypeScript support.
- **VS Code:** install the EditorConfig extension so files follow `.editorconfig`.

### Common problems

| Problem | Fix |
| --- | --- |
| Backend tests fail with *Could not find a valid Docker environment* | Start Docker Desktop. |
| `bootRun` can't connect to the database | `docker compose up -d` in the repository root. |
| Frontend shows *Backend not reachable* | Start the backend (step 4). |
| Docker commands hang | See *Troubleshooting* in the [Docker guide](docs/guides/docker-guide.md). |

## How we work

- Every change goes through a branch and a pull request; `main` is protected and needs green CI (backend, frontend, Docker image).
- API changes start in [`api/openapi.yaml`](api/openapi.yaml); code is generated from it.
- Database changes are Flyway migrations in `backend/src/main/resources/db/migration`.
- Project rules and commands: [CLAUDE.md](CLAUDE.md).

## Documentation

- [Phase 1 requirements](docs/requirements/phase-1-requirements.md): scope, user stories, data model, architecture, external APIs
- [Phase 1 development plan](docs/plan/phase-1-plan.md): milestones and current progress
- [Google Ads primer](docs/guides/google-ads-primer.md): how Google Ads works and how Amazfy maps ads to Amazon products
- [Google login setup](docs/guides/google-login-setup.md): Google Cloud steps for "Sign in with Google" and where local secrets go
- [Google login implementation](docs/guides/google-login-implementation.md): the sign-in flow, its security values and how Google's ID token is checked
- [OpenAPI and contract-first development](docs/guides/openapi-contract-first.md): how the API contract and code generation work
- [Docker guide](docs/guides/docker-guide.md): Docker basics, our usage, commands and troubleshooting
- [pgAdmin guide](docs/guides/pgadmin-guide.md): browse the local database's tables and run SQL in the browser
- [API testing guide](docs/guides/api-testing-guide.md): try the endpoints by hand with Postman or with `api/amazfy.http` in VS Code (REST Client)
- [Business glossary](docs/guides/business-glossary.md): Amazon and Google Ads terms in plain language
- ["Not linked" and "Not matched" report rows](docs/guides/report-rows-not-linked-not-matched.md): the two special dashboard rows, side by side
