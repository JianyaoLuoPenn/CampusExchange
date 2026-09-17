# CampusExchange

A campus and nearby-apartment secondhand marketplace built by adapting the supplied **Ecommerce Multi Vendor Project-2**. Java 17 · Spring Boot 3.3.2 · MySQL · React · TypeScript · Vite. Single Spring Boot application, one database, one React client.

[Source attribution](ATTRIBUTION.md) · [Data model and state transitions](docs/ARCHITECTURE.md) · [Demo](docs/DEMO.md) · [Tests](docs/TESTING.md) · [Debugging notes](docs/MAINTENANCE.md) · [Original vs new contributions / interview preparation](docs/CONTRIBUTIONS.md)

![CampusExchange marketplace](docs/screenshots/marketplace-desktop.png)

## What you can do

- Browse across apartments without an account; filter by keyword, category, price, condition, campus and apartment.
- Use one account to buy and publish one-of-a-kind listings. No business registration, bank details or commercial seller onboarding.
- Choose a seller-provided pickup time. A database transaction locks the item so only one reservation can own it.
- Reserve immediately without a deposit, or hold the item while paying an optional refundable deposit.
- View buying/selling reservations, cancel before completion, and have the seller confirm the handoff.
- See precise pickup instructions only as a confirmed transaction participant.

English UI; fictional demo data. USD only. The offline balance is not collected by the platform. No real seller payouts, penalties, arbitration, AI chatbot, multi-vendor checkout or production payment claims.

## Run locally — recommended: Docker

The complete project now builds **from source** with Java 17, Node 22 and MySQL 8.4 inside Docker. No host JDK, Maven, Node installation, prebuilt JAR, or temporary database is required. Install/open Docker Desktop and wait for its engine to be ready, then from this repository:

```sh
sh scripts/start.sh
```

This creates an ignored `.env` with random local credentials if one does not exist, builds the backend/frontend, starts MySQL, and waits for **all three services** to pass health checks. The frontend health check also verifies its API proxy. First startup downloads images/dependencies and can take several minutes.

Open **http://localhost:5173** (or **http://127.0.0.1:5173**). Both work because the frontend uses a same-origin `/api` proxy. The API is also exposed at **http://localhost:8080/api/campus**. MySQL data persists in a named Docker volume across stops/rebuilds.

```sh
sh scripts/doctor.sh             # Environment and endpoint diagnostics; no secret values printed
docker compose ps               # mysql/backend/frontend should be healthy
docker compose logs -f backend  # Startup/API logs
docker compose down            # Stop; keep your database data
sh scripts/start.sh            # Rebuild/restart after pulling changes
```

Do not add `-v` to `docker compose down` unless you explicitly intend to delete the database. If ports are occupied, edit `DB_PORT`, `API_PORT`, `WEB_PORT` in `.env`; set `FRONTEND_URL` to match your chosen web port. The internal database connection is configured by Compose, independently of the native-development `DB_URL`.

If upgrading an existing checkout, your `.env` is preserved. It must contain nonempty `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` and a random `JWT_SECRET` of at least 32 bytes. Old environment files pointing to the previous temporary demo database are not a reproducible Docker setup; see [STARTUP.md](docs/STARTUP.md). Never commit `.env` or put Stripe secrets in `VITE_*` variables.

In VS Code, use **Terminal → Run Task → CampusExchange: start all services**. The active Java entrypoint is `backend/src/main/java/com/zosh/EcommerceMultiVendorApplication.java`. The unrelated original Hello World scaffold is archived under `legacy/workspace-scaffold` and excluded from Java project discovery.

### Native development (optional)

Use an installed **JDK 17**, Node **22.12+** and MySQL **8.4**, with a new database. Set `JAVA_HOME` to your JDK 17 directory. The native startup script rejects an incompatible Java version with a clear message (this machine originally defaulted to Java 25).

```sh
sh scripts/setup-env.sh
# Edit .env: DB_URL, DB_USER, DB_PASSWORD must describe your own MySQL database.
# If using only the Compose database: docker compose up -d mysql
sh scripts/run-backend.sh
```

In another terminal:

```sh
cd frontend
npm ci
npm run dev
```

The backend script loads root `.env`; Spring Boot itself does not read dotenv automatically. The frontend needs no `.env` for the default local API proxy. If your native API uses another port, run Vite with `API_PROXY_TARGET=http://localhost:YOUR_PORT`. `frontend/.env.example` documents optional overrides. A Docker image/preview server here is for local learning/demo, not a claim of production hosting.

With `DEMO_DATA=true`, these fictional accounts are created on the first run:

| Person | Email | Password |
| --- | --- | --- |
| Maya Chen, seller | maya@example.test | CampusDemo123! |
| Alex Rivera, buyer | alex@example.test | CampusDemo123! |
| Jordan Lee, seller/buyer | jordan@example.test | CampusDemo123! |

Set `DEMO_DATA=false` for an empty installation. Public registration creates an ordinary user who may also sell. These demo accounts are not production credentials. Seeding does not reset existing accounts or bookings.

## Payments

**Default: clearly labelled simulation.** No Stripe key is needed; the buyer can simulate success/failure for their own reservation. Simulated refunds are marked as such throughout the UI.

To use real Stripe **test-mode** API calls, set `PAYMENT_MODE=stripe`, `STRIPE_SECRET_KEY=sk_test_...`, and `STRIPE_WEBHOOK_SECRET=whsec_...`. Live keys or incomplete Stripe configuration fail startup. There is no silent fallback from an explicitly requested Stripe configuration.

Using the Stripe CLI on your machine:

```sh
stripe listen --forward-to localhost:8080/api/campus/webhooks/stripe
```

Use the signing secret printed by that listener, restart the backend, then open Checkout from **My pickups**. Use a Stripe test card, such as `4242 4242 4242 4242`, a future expiry and a test CVC. A redirect to the frontend never marks a payment successful. The signed webhook triggers server-side retrieval and checks the session ID, amount, USD currency and test/live flag. Subscribe to `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed` and `checkout.session.expired` if configuring a test dashboard endpoint instead of the CLI.

Refunds are processed by the scheduled job, with provider idempotency keys and persisted states. The UI says **refund pending** until Stripe reports success; definite failure can be retried by a participant. Keep the webhook listener and backend running. USD deposits in Stripe mode are validated against its $0.50 minimum (mock mode still permits one cent). This implementation was tested with SDK mocks and signed webhook fixtures; no claim of an actual Stripe sandbox transaction is made without your test keys.

Provider references: [Stripe webhook verification and retries](https://docs.stripe.com/webhooks), [Checkout Session](https://docs.stripe.com/api/checkout/sessions), [Refunds](https://docs.stripe.com/api/refunds), [Idempotent requests](https://docs.stripe.com/api/idempotent_requests).

## Build and test

```sh
# From repository root, after configuring .env
set -a
. ./.env
set +a
cd backend
sh ./mvnw verify
cd ../frontend
npm run lint
npm run build
```

Default backend tests use test-only H2. See [TESTING.md](docs/TESTING.md) to run the same transactional suite on a dedicated MySQL database and run the real-browser workflow. Never point the tests at a database you want to keep: the test profile uses `create-drop`.

The GitHub Actions workflow runs Java 17, MySQL 8.4, the active frontend build/lint, and browser tests. The production JAR is `backend/target/Ecommerce-multi-vendor-0.0.1-SNAPSHOT.jar`; the original artifact coordinates are retained. `frontend/dist` is the production frontend output.

## Scope and limitations

- This is a personal learning/interview project, not a production marketplace. Authentication has password hashing and signed JWTs, but no email verification, password recovery, rate limiting or moderation.
- The original framework/SDK baseline is retained; a production release requires dependency/security review and upgrades. Old commercial pages are preserved but excluded from active routes/build roots. They are not represented as repaired or tested.
- `ddl-auto=update` is a local-demo convenience; no migration of an existing original ecommerce database is claimed. Currency amounts on campus reservations are integer cents. Old commercial order totals are not authoritative for campus transactions.
- Provider calls use bounded timeouts inside a per-item transaction, keeping the implementation small but extending the lock duration. A production design could separate these calls with a durable work queue after measuring the need.
- Expiry/refund maintenance polls every 5 seconds by default. Unpaid holds also expire when another reservation attempt accesses the item. Failed definite refunds require a participant's retry; unknown network results remain pending and retry safely.
- Payment success depends on signed webhook delivery. Stripe retries temporary failures; this version has no separate payment-success reconciliation dashboard or background polling of all Checkout Sessions. A delayed success after expiry/cancellation triggers a full refund, never a renewed reservation.
- Sellers provide discrete pickup instants in their browser's local timezone; the backend stores UTC. There is no duration/calendar-conflict planner, messaging, map, image upload/storage service, seller settlement or offline balance verification.
- The UI's initial bundle emits a Vite size warning. This is not a failed build, and no performance or user-growth claim is made.
- The archive had no project-level LICENSE file. Original author references and wrapper license headers are retained. The GitHub repository is private; verify original distribution rights before publishing it.
