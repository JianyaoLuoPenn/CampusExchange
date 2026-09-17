# Verification results

Re-verified on 2026-09-17 after the startup/delivery report. All results below are observed, not estimated. The initial 2026-09-16 baseline and debugging history are preserved in MAINTENANCE.md.

| Check | Result |
| --- | --- |
| Java 17 backend build / runnable JAR | Passed |
| Backend tests, H2 in MySQL compatibility mode | 31 passed, 0 failed/errors/skipped |
| Current suite, clean local MySQL 8.4 Docker instance | 31 passed, 0 failed/errors/skipped |
| Initial suite, temporary native MySQL 5.7.24 | Historical baseline: 24 passed; no longer the recommended local runtime |
| GitHub Actions, Java 17 + MySQL 8.4 | Native verification plus the new clean-startup/restart job; see the latest workflow run below |
| Active React/TypeScript frontend build | Passed; Vite warns about the ~550 kB uncompressed initial JS chunk |
| ESLint on active campus dependency roots | Passed |
| Real Chrome + complete Docker stack at 127.0.0.1 | 2 scenarios passed (11.3 seconds total), including expired-session recovery |
| Full-source Docker startup | All 3 services healthy; frontend proxy reaches the backend |
| Environment setup | Generated credentials are private; rerunning preserves existing configuration |
| Docker restart and native-data migration | User/product/reservation counts preserved; API smoke transaction passed |
| Secret scan of tracked/imported source | No embedded Stripe/AI/GitHub token patterns found after cleanup; `.env` ignored |
| Actual Stripe sandbox charge/refund | Not run: no user-provided Stripe test credentials |

[Latest verification runs](https://github.com/JianyaoLuoPenn/CampusExchange/actions/workflows/verify.yml) include the re-audit changes.

Initial handover CI evidence: [run 35172392936](https://github.com/JianyaoLuoPenn/CampusExchange/actions/runs/35172392936), implementation commit `eeb9759`. The workflow runs again for subsequent commits. Screenshots and test reports are available as CI artifacts. [Desktop](screenshots/marketplace-desktop.png) and [mobile](screenshots/marketplace-mobile.png) screenshots were captured from the running app with fictional demo data.

## What the 31 tests cover

`MarketplaceIntegrationTest` contains 26 tests exercising Spring transactions, JPA repositories, security, HTTP DTOs and real databases. Clock and payment gateway are controlled test doubles; DB access and state transitions are real.

- Two independent buyer transactions race for one item; exactly one succeeds.
- No-deposit reservation, seller-only completion, completed-sale cancellation rejection.
- Expiry at the exact deadline and a new buyer acquiring the released item.
- Duplicate success event IDs and separate duplicate-object events.
- Cancellation → pending refund → definite failure → retry → confirmed refund.
- Ambiguous refund timeout remains pending with the same attempt.
- Late payment after expiry cannot steal a new reservation.
- Payment arriving after cancellation is refunded.
- Payment failure can recover; later failures cannot regress a paid booking.
- Wrong amount and live-mode payments are rejected without saving a receipt.
- Public address/password isolation, anonymous rejection, unrelated-user isolation, old-route denial, malformed bearer header.
- Combined search filters and literal SQL wildcard escaping.
- Self-booking, unsupported time slots and over-price deposits rejected.
- Mock endpoint cannot settle Stripe reservations.
- Forged webhook signature rejected before provider lookup.
- Valid HMAC-signed success webhook can be redelivered safely (provider lookup mocked).
- Concurrent cancellation and payment finish cancelled with refund pending.
- Replay after refund cannot change the payment back to paid.
- Signup produces a usable signed JWT; role-injection fields are rejected.
- Unpaid cancellation does not refund; unauthorized buyer/seller payment actions are denied.

`DepositGatewayTest` contains 5 SDK contract tests: stable Checkout idempotency key + amount + metadata; recovery of a lost refund response using provider metadata without creating another refund; full-deposit refund with stable attempt key; live/missing Stripe keys refused and explicit mock configuration accepted. Stripe static SDK entry points are mocked; these tests do not claim external Stripe connectivity.

Additional regression tests cover both directions of a payment-mode switch, Stripe minimum deposits, a photo URL longer than 255 characters, rejection of null images/fractional cents, accepted loopback origins with unrelated-origin rejection, and definite refund-request rejection.

The primary browser scenario uses the real API to browse/filter, authenticate seller and buyer in separate sessions, publish a new listing, reserve it, simulate payment, verify address visibility, cancel, wait for the scheduled refund, and check mobile overflow. It does not mock the HTTP responses. It validates the user flow separately from the more detailed backend failure tests. A second browser scenario preloads an invalid old token, verifies public browsing recovers, and signs in again.

## Reproduce

H2 (Java 17 required):

```sh
export JWT_SECRET=local-test-only-secret-with-at-least-32-bytes
cd backend
sh ./mvnw test
```

MySQL (use a dedicated disposable schema, never your working demo data):

```sh
# Create campus_test and grant access beforehand.
export JWT_SECRET=local-test-only-secret-with-at-least-32-bytes
export TEST_DB_URL='jdbc:mysql://localhost:3306/campus_test?useSSL=false&allowPublicKeyRetrieval=true'
export TEST_DB_USER=your_test_user
export TEST_DB_PASSWORD=your_test_password
cd backend
sh ./mvnw verify
```

Tests use **create-drop** and remove their schema contents. They do not use `DB_URL` for the test datasource; only `TEST_DB_URL` selects MySQL, otherwise H2 is used.

Frontend:

```sh
cd frontend
npm ci
npm run lint
npm run build
# Requires a separate running backend with DEMO_DATA=true and PAYMENT_MODE=mock.
npx playwright install chromium
npm run test:e2e
```

On macOS with an existing Chrome installation:

```sh
PLAYWRIGHT_CHROME_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' npm run test:e2e
```

Mockito's inline mock maker needs JVM attach support. One restricted-sandbox H2 attempt failed during mock initialization; the rerun with normal process permissions passed. This was an environment failure, distinct from the actual MySQL application race described in MAINTENANCE.md.

## Limits of these results

The current local test and demo environment uses MySQL 8.4; the earlier MySQL 5.7 result is historical only. There is no load test, production traffic, availability benchmark, real user count, real refund/charge or seller-settlement evidence. Original dormant commercial pages/services and AI behavior were not tested. Original frontend build failures remain documented rather than silently represented as fixed.

## Verify the delivered Docker app itself

```sh
sh scripts/start.sh
python3 scripts/smoke.py http://localhost:5173
cd frontend
npm ci
npx playwright install chromium
PLAYWRIGHT_EXTERNAL_URL=http://127.0.0.1:5173 npm run test:e2e
```

`PLAYWRIGHT_EXTERNAL_URL` skips starting a separate Vite dev server, so the browser really exercises the Docker-built frontend and API. The smoke command creates only a fictional account and mock transaction; it refuses Stripe mode. CI now has a separate `clean-startup` job in addition to the native build/test job.
