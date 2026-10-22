# inventory-service

**Owner:** TV4.

## Week 1 implementation (draft contract pending TV3 confirmation)

This service provides a PostgreSQL-backed inventory ledger and HTTP API. It uses `sku` as the inventory key for this first draft; TV3 must confirm whether the shared contract should use `sku` or `productId` before the adapter is enabled.

### Endpoints

Existing internal Inventory workflows may use `X-Inventory-Internal-Token`, configured by `INVENTORY_INTERNAL_TOKEN`. This is a broad compatibility credential for `/api/inventory/**`; it is **not** approved for Checkout/Order and must never be shared with that caller. If the legacy secret is absent, legacy requests fail closed with HTTP 503.

The separate Order-pair gate for `POST /api/inventory/operations` uses `X-Internal-Token` and `INVENTORY_ORDER_PAIR_TOKEN`, but is **default OFF**. `INVENTORY_ORDER_PAIR_ENABLED` must be explicitly set to `true` for the filter to consider the pair credential; configuring a secret alone does not enable mutation. Keep the flag false until TV1/TV3 approve the caller/callee authorization contract and delegated actor/system provenance is implemented and tested. When the gate is enabled, only `RESERVE`, `CONSUME`, and `RELEASE` are allowlisted; initialize, balance reads, wrong method/path, `INCREASE`, and `DECREASE` are denied. Missing verifier/disabled gate returns 503, an invalid pair token returns 401, and an authenticated out-of-scope request returns 403. Operation JSON body is capped at 64 KiB in the filter and over-limit requests return 413. See [Order → Inventory pair gate](ORDER_INVENTORY_PAIR_GATE.md).

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
- `INVENTORY_ORDER_PAIR_ENABLED` (default `false`; leave false until explicit owner approval and authorization implementation)

Flyway owns schema creation; Hibernate is configured with `ddl-auto: validate`.

### Integration blockers

- TV2/TV3 must provide the authoritative Product→SKU and Store/Seller facts and agree how Order persists a `reservationId` before the first RESERVE. The Java V3 candidate is not on `main` and is not yet an approved wire or migration.
- TV3/TV4 must finalize operation IDs, status/error responses, retry/reconciliation, and the SellerOrder dispatch/cancel boundary. The Java-only candidate lookup is not an HTTP endpoint and is not an authoritative cross-service contract until approved.
- Pair authentication alone does not verify original customer Bearer/current Cart/Store authority or durable Order intent. CONSUME/RELEASE also require approved async-system provenance and state transitions. These checks are not implemented by this filter; the default-off flag must remain false until they are implemented and tested.
- This scoped pair gate is not full JWT/delegated-user authorization or current Store/Seller authority. Pair rotation, exact deployment configuration, and Gateway boundary must be reviewed before enabling positive traffic.
- A request with uncertain outcome must retry only the exact persisted operation ID/type/payload; UNKNOWN outcomes require reconciliation, never blind RELEASE.
- CI/unit evidence on PR #49 is limited to filter unit tests. PostgreSQL pair-auth integration, deployed HTTP security-chain/path-normalization, positive service-pair integration, and checkout E2E remain NOT_RUN.

### TV5 verification checkpoint — 17/10/2026

The preceding NOT_RUN line describes the original draft handoff. On owner fixes `adc705e` plus the TV5 regression/matcher changes (source `6c6cf8c`), module build + unit + PostgreSQL tasks executed: **16 unit and 13 PostgreSQL PASS**, 0 failures/errors/skips, exit0/31s. See [commands, RED/GREEN logs, source scope and limits](TEST_RESULTS_TV5_DB_FIXES.md) and the [QA matrix](../../reports/TV5_PR31_DB_REGRESSION_2026-10-17.md).

From the repository root in Windows PowerShell, run:
```powershell
.\backend\gradlew.bat -p backend :inventory-service:test :inventory-service:postgresIntegrationTest --rerun-tasks --continue --no-daemon --console=plain
```

`:inventory-service:test` is unit-only. `:inventory-service:postgresIntegrationTest` runs tagged PostgreSQL integration tests against a fresh PostgreSQL 16 Testcontainers database and requires Docker. The PostgreSQL integration task uses Docker API version `1.44` by default; override it with `-PdockerApiVersion=...` only when the local Docker engine requires another supported API version.

For local service runtime, configure the `INVENTORY_DB_*` variables above. Supply non-default passwords and internal tokens through local environment settings or a secret manager; do not commit real credential values in source files. Keep `INVENTORY_ORDER_PAIR_ENABLED=false` until explicit owner approval and the authorization implementation are complete.

V1 stays unchanged; owner V2 migrates the hash to VARCHAR(64). Do not infer shared service-auth/role/Store authorization or live checkout E2E PASS from these regressions: those remain NOT_RUN/owner-gated.

### Reservation ownership candidate — not an approved shared contract

`candidate/InventoryReservationLedgerCandidateService` and Flyway V3 are an isolated provider-side candidate based on TV3's per-Store/future-SellerOrder ownership principle. It binds an opaque `reservationId` to Store, Seller, SellerOrder, canonical initial SKU lines, and distinct RESERVE/CONSUME/RELEASE operation IDs; partial operations are limited by that reservation's held quantities. The candidate also provides a Java-only status lookup whose missing result is `UNKNOWN`.

No HTTP controller calls this candidate, no public/internal route was added, and it does not alter the existing operation request. Keep the pair gate OFF. V3 is not approved for deployment until TV1/TV2/TV3/TV5 sign the owner fields, Product/SKU and Store authority, status/error wire, route authorization, and cancellation/partial/COD rules. The PostgreSQL candidate tests are provider-module evidence only; they are not live Order→Inventory HTTP, JWT, or Checkout E2E evidence. See [reservation ledger candidate](RESERVATION_LEDGER_CANDIDATE.md).
