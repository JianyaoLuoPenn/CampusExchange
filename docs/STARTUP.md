# Startup and GitHub delivery checklist

## What was incomplete in the initial handover

The original local demonstration ran with Java 17 and a MySQL data directory under `/private/tmp`; the shipped script still relied on host Java/MySQL. The host default was Java 25, Docker Desktop was stopped, Compose only started MySQL, and the Dockerfile required an already-built JAR. VS Code still pointed at the unrelated `src/App.java` Hello World scaffold. Consequently, a passing GitHub Actions run was not enough to establish a reproducible local startup.

These were delivery defects, not evidence that every marketplace feature was absent. The previous private repository and commits were present, and its CI runs passed. The correction is to test the actual documented startup path from a clean source checkout.

## Current startup path

1. Open Docker Desktop and wait until the engine is running.
2. In the CampusExchange repository directory, run `sh scripts/start.sh`.
3. Wait for successful completion, then open http://localhost:5173.
4. Sign in with `alex@example.test` / `CampusDemo123!` (fictional data), or register your own account.
5. To stop without deleting data: `docker compose down`.

The script calls setup-env, validates Compose configuration, builds from source, and waits for database, API, and frontend/API-proxy health. Docker build contexts exclude `.env`, host `node_modules`, `dist`, and `target`, so successful startup cannot accidentally depend on those local files.

The frontend initially failed a clean-container test because Vite preview could not write its temporary config directory as the non-root `node` user. That directory now has appropriate ownership, and a frontend health check prevents a successful backend startup from masking a failed frontend.

## Troubleshooting

| Symptom | Check/action |
| --- | --- |
| Docker daemon unavailable | Open Docker Desktop; rerun `sh scripts/doctor.sh` before starting |
| Java 25 / Lombok compile errors | Use Docker startup, or set `JAVA_HOME` to an installed JDK 17 for native startup |
| `.env` missing | `sh scripts/setup-env.sh` creates random local credentials; keep it private |
| Missing/blank `MYSQL_ROOT_PASSWORD` or `DB_PASSWORD` | Existing `.env` was preserved; set nonempty values before running Compose |
| Existing placeholder secret | Replace it with `openssl rand -hex 32`; real secrets are never committed |
| MySQL access denied after changing `.env` | Existing volume keeps its original database credentials; restore them or change credentials inside MySQL. Recreating the volume deletes data and is not an automatic repair |
| Port already allocated | Stop the conflicting app, or change the relevant `*_PORT` and `FRONTEND_URL` |
| Browser works at localhost but not 127.0.0.1 | Remove an obsolete absolute `VITE_API_URL` from `frontend/.env`; default `/api/campus` uses the same-origin proxy |
| VS Code runs Hello World | Use the supplied CampusExchange task or the Spring Boot entrypoint under backend; the old scaffold is archived |
| Git commands show Xcode licence prompt on this Mac | The available alternative is `/Library/Developer/CommandLineTools/usr/bin/git`; do not accept software licences automatically |
| GitHub repository appears unavailable | It is private: sign in as JianyaoLuoPenn and open https://github.com/JianyaoLuoPenn/CampusExchange |

## Native environment-file migration

The previous local demo `.env` could contain `DB_USER=root`, an empty database password, and port 33307 for a temporary database. Keep a private backup before replacing that local-demo configuration. A fresh Docker configuration comes from `.env.example` and `scripts/setup-env.sh`; native DB_URL does not set the container's internal DB host/user.

Use a new Compose project name/data volume when verifying a clean install. Do not delete an existing working database merely to make a startup test pass.

## Scope still not claimed

Stripe test Checkout/refund calls require the account owner's test secrets; mocked SDK/signature tests do not constitute an external Stripe sandbox transaction. Dormant commercial/AI functionality is not enabled or promised. Editing/withdrawing listings, image hosting and a production deployment remain outside the current delivered flows.
