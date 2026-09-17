# Debugging and maintenance log

These entries describe observed behavior during this implementation. They are not fabricated historical activity.

## 2026-09-16 — Original frontend build

`npm run build` on the imported frontend failed on unused imports, React type imports and MUI Grid API incompatibilities in original commercial pages. Campus routes now use adapted product/navbar pages and the existing MUI theme; TypeScript checks their complete reachable dependency graph from `main.tsx`. Dormant original admin, coupon, cart, AI and commercial seller pages are retained for reference, not presented as repaired or tested.

## 2026-09-16 — MySQL repeatable-read race

The first 15 integration tests passed on H2. On the isolated MySQL 5.7.24 instance, the two-buyer race failed with `NoSuchElementException`: the second transaction acquired the Product lock and saw the first transaction's active reservation ID, but a subsequent ordinary reservation read still used its older consistent snapshot and could not see that row.

Fix: read only the immutable product ID when locating a reservation, lock Product first, then use a locking/current read for Reservation. Refresh both under `PESSIMISTIC_WRITE` so Hibernate's persistence context cannot retain an older entity state. The next MySQL run exposed the same snapshot problem in an eager Order association. Mutation transactions now explicitly use READ_COMMITTED as well as Product row locks; this makes associated rows committed by the preceding lock holder visible. The original concurrency test remains as a regression. This is why an H2-only pass was insufficient.

## Build environment

The system `git` launcher required Xcode license acceptance; the already-installed Command Line Tools Git worked. Java 17 was downloaded into `/private/tmp` for verification because the shell default was Java 25. No global Java installation was changed. A separate MySQL data directory and port were used, without touching existing databases. GitHub CLI was installed temporarily and authenticated by the account owner.

## 2026-09-16 — Payment dependency and refund recovery

Moved original commercial payment/AI sources into `legacy/` and removed the Razorpay SDK from the build. The active adapter uses Stripe only. Added refund metadata lookup before recreating a refund whose local ID is missing, so a lost response/commit can be reconciled even beyond the provider's idempotency-key retention window. Contract tests verify that an existing successful refund is reused.

## 2026-09-16 — Browser and validation checks

The first browser run used exact label matching that did not account for MUI required-field labels; the second clicked the publish link before sign-in navigation completed. Corrected the test selectors and added a visible signed-in-state wait. The application flow then passed through real API publication, reservation, mock deposit, cancellation, scheduled refund, and mobile layout. The active lint run also identified a Fast Refresh export issue, fixed by separating the auth hook/context from the provider component. No tests were removed or assertions disabled to obtain these passes.

Final verification: 24 backend tests passed on local H2 and MySQL; GitHub Actions also passed on MySQL 8.4 with the browser workflow. No actual Stripe sandbox charge was performed without test keys.

## 2026-09-16 — Local demo process during rebuild

A screenshot rerun after rebuilding the same JAR that the demo JVM had open failed with `NoClassDefFoundError`. The earlier clean browser/CI runs had passed. Restarted the demo from a separate stable JAR copy rather than overwriting an in-use artifact; documented this deployment lesson rather than treating the failure as a payment/auth regression. The mobile screenshot test now waits for product cards, not just the static hero, before checking overflow and capturing evidence.
