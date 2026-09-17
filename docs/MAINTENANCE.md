# Debugging and maintenance log

These entries describe observed behavior during this implementation. They are not fabricated historical activity.

## 2026-09-16 — Original frontend build

`npm run build` on the imported frontend failed on unused imports, React type imports and MUI Grid API incompatibilities in original commercial pages. Campus routes now use adapted product/navbar pages and the existing MUI theme; TypeScript checks their complete reachable dependency graph from `main.tsx`. Dormant original admin, coupon, cart, AI and commercial seller pages are retained for reference, not presented as repaired or tested.

## 2026-09-16 — MySQL repeatable-read race

The first 15 integration tests passed on H2. On the isolated MySQL 5.7.24 instance, the two-buyer race failed with `NoSuchElementException`: the second transaction acquired the Product lock and saw the first transaction's active reservation ID, but a subsequent ordinary reservation read still used its older consistent snapshot and could not see that row.

Fix: read only the immutable product ID when locating a reservation, lock Product first, then use a locking/current read for Reservation. Refresh both under `PESSIMISTIC_WRITE` so Hibernate's persistence context cannot retain an older entity state. The next MySQL run exposed the same snapshot problem in an eager Order association. Mutation transactions now explicitly use READ_COMMITTED as well as Product row locks; this makes associated rows committed by the preceding lock holder visible. The original concurrency test remains as a regression. This is why an H2-only pass was insufficient.

## Build environment

The system `git` launcher required Xcode license acceptance; the already-installed Command Line Tools Git worked. Java 17 was downloaded into `/private/tmp` for verification because the shell default was Java 25. No global Java installation was changed. A separate MySQL data directory and port were used, without touching existing databases. GitHub CLI was installed temporarily and authenticated by the account owner.
