# pgAdmin: looking inside the local database

**pgAdmin** is a web app for PostgreSQL: browse tables, see the rows in them and run SQL. `docker-compose.yml` runs it next to the local database, so you can check what the backend stored, e.g. whether a sign-up created the user, the account and the membership.

Local development only: it runs without a pgAdmin login and is never part of production.

## Open it

1. Start the local services (repository root): `docker compose up -d`. It starts PostgreSQL, Mailpit and pgAdmin.
2. Open **http://localhost:5050**. No pgAdmin login is needed ("desktop mode").
3. On the left: **Servers → amazfy (local)**. The first time, pgAdmin asks for the database password: enter `amazfy` and tick **Save password**.

The server is registered automatically (from the `configs` part of `docker-compose.yml`). Inside Docker, pgAdmin reaches the database by its service name `postgres`, not `localhost`.

## Look at a table

**Servers → amazfy (local) → Databases → amazfy → Schemas → public → Tables**, then right-click a table → **View/Edit Data → All Rows**.

| Table | What's in it |
| --- | --- |
| `users` | People who log in (email, when the email was verified) |
| `user_identities` | How each user logs in: `password` (with the bcrypt hash) and/or `google` (Google's user id) |
| `accounts` | Seller accounts, one per Amazon seller account, each with its Amazon region (`NA` / `EU`) |
| `memberships` | Which user can access which account, and their role |
| `refresh_tokens`, `one_time_tokens` | Sessions and emailed links; only hashes, never the real tokens |
| `auth_attempts` | Recent failed logins and email requests for the brute-force limits (hashed keys) |
| `flyway_schema_history` | Which migrations Flyway has applied. **Never edit it.** |

## Run SQL

Select the **amazfy** database, then **Tools → Query Tool** (or the database icon with ▶). Type a query and press **F5** (or ▶ Execute).

```sql
-- The newest users, and whether their email is verified
SELECT email, email_verified_at IS NOT NULL AS verified, created_at
FROM users ORDER BY created_at DESC LIMIT 20;

-- Who can access which Amazon seller account, with which role
SELECT u.email, a.name AS account, m.role
FROM memberships m
JOIN users u ON u.id = m.user_id
JOIN accounts a ON a.id = m.account_id
ORDER BY a.name;

-- How each user logs in
SELECT u.email, i.provider
FROM user_identities i JOIN users u ON u.id = i.user_id
ORDER BY u.email;
```

Reading is always safe. **Changing data by hand** (`UPDATE`, `DELETE`, editing cells) can leave the database in a state the backend never creates itself; do it only when you know why, and never change the tables' structure here: structure changes go through Flyway migrations (`backend/src/main/resources/db/migration`), or the next backend start fails.

## Start over

| Goal | Command |
| --- | --- |
| Empty database (all users, accounts, ...) | `docker compose down -v`, then `docker compose up -d`; Flyway recreates the tables at the next `bootRun`. Also resets pgAdmin (saved password, open tabs). |
| Reset only pgAdmin | `docker compose down`, `docker volume rm amazfy_pgadmin-data`, `docker compose up -d` |

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| http://localhost:5050 doesn't load | `docker compose ps`: is `amazfy-pgadmin` running? It starts after the database is healthy; give it half a minute after `up -d`. |
| "password authentication failed" | Wrong saved password: right-click **amazfy (local)** → **Properties → Connection**, password `amazfy`. |
| **amazfy (local)** is missing | pgAdmin imports the server only when its data is new. Reset only pgAdmin (above). |
| Port 5050 already in use | Another program uses it: change the left side of `"5050:80"` in `docker-compose.yml` (e.g. `"5051:80"`) and open that port instead. |
| A table you expect isn't there | The backend hasn't run since the migration was added: start `bootRun` once (Flyway creates it), then right-click **Tables → Refresh**. |

## Other tools

Any PostgreSQL client works the same way from your machine: host `localhost`, port `5432`, database, user and password `amazfy`. For a quick look without a browser: `docker exec -it amazfy-postgres psql -U amazfy -d amazfy` (see the [Docker guide](docker-guide.md)).
