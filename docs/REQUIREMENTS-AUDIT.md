# Re-audit after the startup/delivery report — 2026-09-17

The first handover did not adequately validate the user's local startup path. This review separates an implemented feature from a reproducible delivered app, and records actual defects rather than relying on the previous passing tests.

| Original requirement | Current evidence / boundary |
| --- | --- |
| Keep Java 17, Spring Boot, MySQL, React/TS/Vite and single-app architecture | Preserved. Full-source Docker builds now supply Java 17, Node 22 and MySQL 8.4 without host prerequisites |
| Secondhand fields, categories, keyword/price/condition/campus/apartment filtering | Active API/UI; combination/wildcard tests and real-browser filtering |
| Ordinary users can buy and list one unique item | Unified existing User identity; one Product/OrderItem per reservation; browser publishes and buys |
| Seller-provided pickup time and participant-only precise address | Active DTO/API/GUI; HTTP privacy/ownership tests |
| Concurrent reservation exclusivity | Product row lock, current reads, explicit READ_COMMITTED; real MySQL integration race tests |
| Optional positive deposit no greater than price; cancel before completion for full refund | Mock and Stripe test adapters; Stripe mode additionally validates provider USD minimum. Cancellation/refund state tests |
| Configurable hold, expiry release, late success handling | Scheduled expiry and transactional release; expiry/new-buyer/late-payment regression tests |
| Server-trusted payments, duplicate notifications, failed payments/refunds | Signed webhook + server retrieval, durable states and provider idempotency. Newly fixed mode-switch mismatch and definite refund-request rejection |
| Missing payment keys have an honest mock mode | Explicit mode banner/buttons; missing keys in requested Stripe mode fail startup |
| Runnable code and startup instructions | Previously incomplete; now tested via a clean three-service Docker stack, health-gated startup and frontend-origin smoke/browser tests |
| GitHub delivery and contributor requirement | Existing private repo and previous commits confirmed. New commits use the same verified user identity, real timestamps and no co-author |
| Learning/interview attribution, documentation and limitations | Original sources retained; README, architecture/demo/test/debug/contribution docs updated |

## Concrete defects found and fixed

- Temporary native JDK/MySQL state was relied on during the first demonstration; default host Java was 25.
- The old VS Code Hello World scaffold still looked like the project's entrypoint.
- Original Compose only provisioned MySQL; original Dockerfile required a prebuilt JAR.
- Absolute browser API URLs made the localhost/127.0.0.1 entrypoint sensitive to CORS. The same-origin proxy now works in native dev and Docker preview.
- A clean frontend container exited because non-root Vite could not write its temporary config directory. Directory ownership and a frontend/API-proxy health check were added.
- A saved invalid/expired JWT could block public browsing and make the server appear offline; public GETs now recover anonymously and the sign-in state resets.
- The localhost/127.0.0.1 browser origin mismatch was reproduced and fixed, with unrelated origins still denied.
- A mock reservation could reach the real Stripe Checkout adapter after a server-mode change; the reverse mismatch was also possible. Both are rejected before external calls.
- Image URLs accepted up to 1,000 characters but the DB collection column defaulted to 255; the schema now matches validation.
- Null image entries and fractional integer-cent amounts needed explicit rejection.
- Small positive deposits could be unpayable with Stripe's USD minimum. The active UI and server now reflect the mode-specific minimum.
- A definitively rejected new refund request could be left pending forever; known 400 rejection now remains failed, while ambiguous network results remain pending.

## Still not externally verified / intentionally limited

Actual Stripe sandbox charges/refunds are not claimed: the account owner has not supplied test credentials. Payment-success recovery currently depends on provider webhook delivery/retry; there is no independent reconciliation dashboard. The project has no production seller payouts, arbitration, penalties, maps or new AI features. Listing editing/withdrawal and hosted image uploads are not part of the delivered reservation-focused scope. Dormant commercial modules are not certified as working.

Provider rules consulted for the deposit/session review: [Stripe minimum charge](https://docs.stripe.com/api/payment_intents/create) and [Checkout Session expiration](https://docs.stripe.com/api/checkout/sessions/expire). A future reconciliation improvement should actively expire old Checkout links; current late successful payments are handled by the tested refund path.
