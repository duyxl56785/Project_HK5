# TV3 — Uncertain Checkout outcome reconciliation runbook

**Safety rule:** never automatically release inventory or unlock Cart after timeout unless committed outcome is verified.

1. Lookup checkout_attempt by (customer_id,idempotency_key), record attempt.id and state.
2. Use attempt.id as the deterministic Order ID and **correlation ID only**. TV4 Inventory uses distinct, stable step-level `operationId` values for RESERVE, RELEASE and CONSUME; never reuse one operationId across types. Current TV3 InventoryPort still accepts orderId/productId and is **not** a compatible live adapter.
3. If a placed Order exists, do not release stock: verify SellerOrders/outbox, repair attempt state transactionally with TV4/DB owner review, and finalize the Cart idempotently.
4. If no Order exists, inventory.reserve may nevertheless have succeeded. The **proposed** TV3 design must persist the exact SKU/quantity reservation lines and each step operationId before network calls; no approved journal/status API exists yet. Obtain authoritative reservation status from TV4 after its contract is approved, before any compensating release or checkout retry. Never invent a `release(orderId)` operation from a bare orderId.
5. Stale PROCESSING is marked NEEDS_RECONCILIATION by a scheduled scanner. It does NOT modify remote services.
6. DB unavailable or uncertain commit means keep attempt blocked, no automatic compensation.
7. Document operator, external evidence, outcome, timestamps, and manual state transitions. No unreviewed direct SQL correction.

**Production remains blocked** pending TV4 reserve/query/release semantics, audited reconciliation implementation and live crash/retry tests.

TV5 regression fix: stale scanner and uncertain-failure marking use conditional DB UPDATEs restricted to PROCESSING. A concurrently committed SUCCEEDED row is retained. Successful replay and committed-outcome reads load the response collection with the verified customer's ID scope before returning from the repository. These guards do not perform remote compensation or replace the operator-led steps above; see TEST_RESULTS_TV5_FIXES.md for real PostgreSQL race/replay tests.

For the pending provider/consumer decisions and tests see [TV3_PROVIDER_CONSUMER_DECISIONS.md](TV3_PROVIDER_CONSUMER_DECISIONS.md). This is a proposal only and does not change deployed InventoryPort or Flyway V1.
