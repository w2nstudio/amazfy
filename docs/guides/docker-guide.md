# Docker guide

What Docker is, how Amazfy uses it, the commands you need day to day, and how to fix the usual problems. No prior Docker knowledge is assumed.

## The four ideas

| Term | Meaning | Amazfy example |
| --- | --- | --- |
| **Image** | A read-only package: an app plus everything it needs to run (OS files, runtime, libraries). Built once, run anywhere. | `postgres:18-alpine`, our `amazfy-backend` image |
| **Container** | A running (or stopped) instance of an image, isolated from your machine | `amazfy-postgres`, the local database |
| **Volume** | Storage that outlives a container, so data survives restarts | `postgres-data`, where the local database keeps its tables |
| **Docker Compose** | Starts one or more containers from a YAML file (`docker-compose.yml`) with one command | Starts the local PostgreSQL |

The **Docker Engine** runs containers. The `docker` command is only a client that sends it instructions. On Windows, **Docker Desktop** provides the engine by running a small Linux system (`docker-desktop`) on WSL 2.

## How Amazfy uses Docker

| Use | What happens | You need |
| --- | --- | --- |
| **Local database** for `./gradlew bootRun` | `docker-compose.yml` runs PostgreSQL 18 on `localhost:5432` (database, user and password: `amazfy`) | Docker running **and** the `amazfy-postgres` container started |
| **Local email** for `./gradlew bootRun` | `docker-compose.yml` also runs Mailpit: it accepts emails on `localhost:1025` (SMTP) and shows them at http://localhost:8025 instead of delivering them | The `amazfy-mailpit` container started (same `docker compose up -d`) |
| **Database browser** | `docker-compose.yml` also runs pgAdmin at http://localhost:5050 to browse tables and run SQL; see the [pgAdmin guide](pgadmin-guide.md) | The `amazfy-pgadmin` container started (same `docker compose up -d`) |
| **Backend tests** (`./gradlew build` / `test`) | Testcontainers starts a throwaway PostgreSQL per test run and removes it afterwards | Docker running only |
| **Backend image** for deployment | `backend/Dockerfile` packages the backend; CI builds it on every pull request | Docker running, to build it locally |

## Everyday commands

Run Compose commands in the repository root (where `docker-compose.yml` is).

| Command | What it does |
| --- | --- |
| `docker compose up -d` | Start the local database, Mailpit and pgAdmin in the background (`-d` = detached). Same as ▶ on the `amazfy` group in Docker Desktop. |
| `docker compose down` | Stop and remove the containers. **Database data is kept** in the volume (Mailpit's emails are not). |
| `docker compose down -v` | Stop and **delete the data** too (`-v` = volumes). Next start begins empty; Flyway recreates the tables. |
| `docker compose ps` | Are the database and Mailpit running (and the database healthy)? |
| `docker compose logs -f postgres` | Follow the database log (Ctrl+C to stop following) |
| `docker exec -it amazfy-postgres psql -U amazfy -d amazfy` | Open a SQL prompt inside the database (`\dt` lists tables, `\q` quits) |
| `docker ps` | All running containers |
| `docker images` | Images on this machine |
| `docker system df` | How much disk Docker uses |
| `docker system prune` | Remove stopped containers, unused networks and dangling images (asks first; volumes are kept) |

The containers do not start automatically when Docker starts. After a restart of Docker or Windows, start it again with `docker compose up -d` (or ▶ in Docker Desktop).

## Our backend image

`backend/Dockerfile` is a recipe in two stages:

1. **Build stage** (`eclipse-temurin:21-jdk`): copies `api/` and `backend/`, compiles the jar with Gradle, and splits it into layers.
2. **Run stage** (`eclipse-temurin:21-jre`): a runtime-only image that receives just the layers. No compiler or source code ships.

Production details:

- **Layers:** libraries change rarely, our code often. Docker stores them as separate layers, so a code change uploads only the small application layer.
- **Non-root user:** the app runs as user `amazfy`, limiting the damage if it were ever compromised.
- **Profile:** `prod` by default; settings come from environment variables.
- **Size:** about 600 MB, mostly the Ubuntu base and the Java runtime; our own code is under 1 MB.

Build and run it locally (from the repository root):

```bash
docker build -f backend/Dockerfile -t amazfy-backend .
docker run --rm -p 8081:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/amazfy \
  -e DB_USERNAME=amazfy -e DB_PASSWORD=amazfy \
  -e CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  -e JWT_SECRET=local-development-secret-not-for-production-0123456789 \
  -e SECRET_ENCRYPTION_KEY=pQd05CRP9xYS6vbn2OtbAxkKs6025o8BG5YCLASdPgM= \
  -e MAIL_HOST=host.docker.internal -e MAIL_PORT=1025 \
  -e "MAIL_FROM=Amazfy <no-reply@amazfy.local>" -e FRONTEND_BASE_URL=http://localhost:5173 \
  amazfy-backend
```

Inside a container, `localhost` means the container itself. `host.docker.internal` is Docker Desktop's name for your computer, which is where the ports of the local database and Mailpit are published. `--rm` removes the container when it stops.

## Docker Desktop tips

- **Start with Windows:** Settings → General → *Start Docker Desktop when you sign in*. Saves "Could not find a valid Docker environment" test failures.
- **Engine status:** bottom-left of the Docker Desktop window shows *Engine running* when ready.
- **Signing in** to a Docker account is optional; nothing in Amazfy needs it.
- **Licence:** Docker Desktop is free for personal use, education and small businesses (fewer than 250 employees and under $10M annual revenue).

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Tests fail with *Could not find a valid Docker environment* | Docker Desktop isn't running. Start it and wait for *Engine running*. |
| `bootRun` fails to connect to `localhost:5432` | The database container isn't started: `docker compose up -d`. |
| `docker` commands hang; Docker Desktop shows stale information (e.g. "running for 15 hours") | The engine is frozen, often after sleep or a long uptime. 1) Docker Desktop tray icon → *Quit Docker Desktop*. 2) In PowerShell: `wsl --shutdown`. 3) If `Docker Desktop` or `com.docker.backend` processes are still in Task Manager, end them. 4) Start Docker Desktop again. A Windows restart also fixes it. |
| Email links never arrive | Open http://localhost:8025 (Mailpit), not your real inbox. If it doesn't load: `docker compose up -d`. |
| Port 5432 already in use | Another PostgreSQL runs on your machine. Stop it, or change the left side of `"5432:5432"` in `docker-compose.yml` and the port in `DB_URL`. |
| Disk filling up | `docker system df` to see usage, `docker system prune` to clean up. |

`wsl --shutdown` stops all WSL Linux systems. Docker uses its own (`docker-desktop`); other distributions such as Ubuntu are stopped too, so save work there first. `wsl --list --verbose` shows which are running.
