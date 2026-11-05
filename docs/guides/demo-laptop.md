# Running Amazfy on another computer (demo laptop)

How to run Amazfy on a second computer, such as a partner's MacBook, for a demo. Nothing is deployed: everything runs on that laptop, and its data stays there. The general setup is in the [README](../../README.md); this page adds what a fresh machine, and a Mac in particular, needs. Do it the day before: the first start downloads a lot.

## 1. Install the tools once

| Tool | On a Mac | Why |
| --- | --- | --- |
| Docker Desktop | [docker.com](https://www.docker.com/products/docker-desktop/): choose **Apple chip** or **Intel chip** (Apple menu → About This Mac) | Runs the database, the email catcher and pgAdmin |
| A JDK, 17 or newer | [Temurin 21](https://adoptium.net/) installer (`.pkg`) | Starts Gradle; Gradle then downloads the JDK 21 it compiles with by itself |
| Node.js 22 LTS | [nodejs.org](https://nodejs.org/) installer (`.pkg`) | Runs the frontend |
| Git | `xcode-select --install` in Terminal, or GitHub Desktop | Gets the code |

Check in a new Terminal window: `java -version` (17 or newer), `node -v` (v22), `docker -v`.

All images in `docker-compose.yml` (PostgreSQL, Mailpit, pgAdmin) run on Apple-chip Macs.

## 2. Get the code and the secrets

```bash
git clone https://github.com/w2nstudio/amazfy.git
cd amazfy
```

The repository may be private: sign in with a GitHub account that has access, or download it as a ZIP from GitHub.

**The secrets file is not in git, on purpose.** Copy `backend/secrets-local.properties` from the development computer to the same place on the laptop (`amazfy/backend/secrets-local.properties`), by USB stick or AirDrop. Never by git, email or chat: it holds Google's client secret. Without it Amazfy still runs, with sample data, but **Continue with Google** and **Connect Google Ads** don't work.

The other local settings (database, encryption key for local data, email) are in `application-local.properties` and need nothing.

## 3. Start everything

```bash
docker compose up -d        # in the amazfy folder; Docker Desktop must be running
```

```bash
cd backend
./gradlew bootRun           # first time: several minutes of downloads
```

Wait for `Started AmazfyBackendApplication`. In a second Terminal window:

```bash
cd frontend
npm install                 # first time only
npm run dev
```

Open http://localhost:5173. The Google sign-in and Google Ads addresses are `localhost`, the same on every computer, so Google needs no change for the laptop.

## 4. Prepare the demo

The laptop starts with an empty database: nothing from the development computer comes along.

1. **Sign up.** The verification email arrives in Mailpit, http://localhost:8025.
2. For the sample data demo: **Add Amazon seller account** *Leafwell Garden – US* (North America) and fill **Small demo: Leafwell Garden, 4 days** on **Home**. Tick **Google side only** first to show the CSV import too ([how](sample-data.md#showing-the-csv-import)).
3. For the real demo: the seller's Gmail must be a **test user** in Google Cloud, with a passkey; see the [demo checklist](demo-checklist.md).

## Problems

| Problem | Fix |
| --- | --- |
| `port is already allocated` on `docker compose up` | Something on the laptop already uses that port, often a PostgreSQL of its own on 5432. Stop it, or stop the other program |
| `./gradlew: Permission denied` | `chmod +x gradlew` in `backend` (a ZIP download can lose the flag) |
| `Unable to locate a Java Runtime` | Install the JDK (step 1) and open a new Terminal window |
| Google says "Access blocked" | The Google account isn't a test user (demo checklist) |
| **Continue with Google** answers "not available" | `secrets-local.properties` is missing or in the wrong folder (step 2) |

## Afterwards

`docker compose down` stops the services and keeps the data for next time. `docker compose down -v` also deletes the database, including any real seller's Google Ads connection and numbers: do that when the laptop's owner wants their data gone.
