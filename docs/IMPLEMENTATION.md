# Implementation plan and baseline audit

1. Import the supplied archive, retain provenance, remove embedded secrets, verify Java 17 and frontend builds.
2. Extend Product and reuse User, Order and OrderItem; add a single-item Reservation with transactional locking, public DTOs and participant-only pickup addresses.
3. Reuse the Stripe Checkout SDK approach; add signed events, server retrieval, expiry, durable refund states and explicitly labelled simulation.
4. Adapt existing customer/product/account pages and MUI theme for cross-apartment discovery and pickup.
5. Verify races and failure paths, document startup, demo and actual test results. Commit actual changes and fixes in logical parts.

Findings: original Spring Boot 3.3.2, Java target 17, React 19, Vite 7. Original payment implementation mixes Razorpay verification with Stripe Checkout creation; it is unsuitable for deposits as-is. Original authorization permits all non-/api paths and does not reliably enforce ownership. Default admin initialization and embedded JWT/AI/config secrets require removal from the active application. The provided workspace initially contained only a VS Code Java Hello World scaffold. No project-level source license was found in the supplied archive.

Legacy commercial controllers/services will remain as learning/reference code but will not be component-scanned or reachable by the CampusExchange application. Active routes use a deny-by-default allowlist. No old payment return endpoint is trusted.
