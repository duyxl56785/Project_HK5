# inventory-service

**Owner:** TV4.

## Week 1 implementation (draft contract pending TV3 confirmation)

This service provides a PostgreSQL-backed inventory ledger and HTTP API. It uses `sku` as the inventory key for this first draft; TV3 must confirm whether the shared contract should use `sku` or `productId` before the adapter is enabled.

### Endpoints

Existing internal Inventory workflows may use `X-Inventory-Internal-Token`, configured by `INVENTORY_INTERNAL_TOKEN`. This is a broad compatibility credential for `/api/inventory/**`; it is **not** approved for Checkout/Order and must never be shared with that caller. If the legacy secret is absent, legacy requests fail closed with HTTP 503.

A separate Order-pair gate is being introduced for `POST /api/inventory/operations` using `X-Internal-Token` and `INVENTORY_ORDER_PAIR_TOKEN`. It allows only `RESERVE`, `CONSUME`, and `RELEASE`; initialize, balance reads, wrong method/path, `INCREASE`, and `DECREASE` are denied. Missing pair-token configuration returns 503, an invalid pair token returns 401, and an authenticated out-of-scope request returns 403. This gate is not a production integration approval and does not replace TV3's durable operation journal, approved actor/system provenance, or TV1's final credential provisioning/rotation review. See [Order → Inventory pair gate](ORDER_INVENTORY_PAIR_GATE.md).

- `POST /api/inventory/items` — initialize an SKU and available quantity (duplicate SKU returns conflict).
- `GET /api/inventory/{sku}` — read available and reserved quantities.
- `POST /api/inventory/operations` — apply an operation atomically for all SKUs in the request.

Operation types:
- `INCREASE`: add available stock.
- `DECREASE`: subtract available stock, rejecting insufficient stock.
- `RESERVE`: move quantity from available to reserved.
- `CONSUME`: subtract from reserved stock after a successful checkout/fulfillment decision.
- `RELEASE`: move reserved stock back to available stock after cancellation/rejection.

Each operation requires a stable `operationId`. The database ledger rejects reuse of an ID with a different canonical request and prevents a committed operation from applying again. All requested stock rows are locked in SKU order inside one transaction; a failed item rolls back the whole multi-SKU operation. The database also enforces non-negative available/reserved quantities.

### Configuration and verification

- `INVENTORY_DB_URL` (default `jdbc:postgresql://localhost:5432/inventory_db`)
- `INVENTORY_DB_USERNAME` (default `inventory`)
- `INVENTORY_DB_PASSWORD` (default empty; set it outside source control)
- `INVENTORY_INTERNAL_TOKEN` (legacy broad token for existing internal workflows only; never provide to Checkout/Order)
- `INVENTORY_ORDER_PAIR_TOKEN` (dedicated Order pair token; keep unset until owner review and secret provisioning)

Flyway owns schema creation; Hibernate is configured with `ddl-auto: validate`.

### Integration blockers

- TV3 must confirm `sku` vs `productId`, operation-ID derivation from checkout/order, and when reserve/consume/release is called. Until that is confirmed, do not wire this endpoint into checkout.
- This scoped pair gate is not full JWT/delegated-user authorization or current Store/Seller authority. The shared service-auth contract, pair rotation, exact deployment configuration, and Gateway boundary must be reviewed before enabling positive traffic.
- A request with uncertain outcome must retry only the exact persisted operation ID/type/payload; UNKNOWN outcomes require reconciliation, never blind RELEASE.
- Unit tests for the new pair gate are added on a separate branch but are NOT_RUN until CI/test execution is confirmed. PostgreSQL/runtime auth, positive service-pair integration, and checkout E2E remain NOT_RUN.

### TV5 verification checkpoint — 17/10/2026

The preceding NOT_RUN line describes the original draft handoff. On owner fixes `adc705e` plus the TV5 regression/matcher changes (source `6c6cf8c`), module build + unit + PostgreSQL tasks executed: **16 unit and 13 PostgreSQL PASS**, 0 failures/errors/skips, exit0/31s. See [commands, RED/GREEN logs, source scope and limits](TEST_RESULTS_TV5_DB_FIXES.md) and the [QA matrix](../../reports/TV5_PR31_DB_REGRESSION_2026-10-17.md).

Run `gradle -p backend :inventory-service:build :inventory-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain` from the repository root (or use the equivalent Gradle Wrapper). The explicit PostgreSQL task requires Docker and a server-supported JVM timezone; unit test excludes the postgres-integration tag. Test databases are newly created by Testcontainers. V1 stays unchanged; owner V2 migrates the hash to VARCHAR(64). Do not infer shared service-auth/role/Store authorization or live checkout E2E PASS from these regressions: those remain NOT_RUN/owner-gated.
