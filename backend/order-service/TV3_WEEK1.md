# TV3 W1 — Checkout COD / CHANGES_REQUESTED followup PR #28

Status: PARTIAL / NOT PRODUCTION READY. No shared contract or other owners' module changed.

## 18/10/2026 — latest PR #28 review / cross-team contract gate

TV1 confirmed the former HS256 environment, replay-lazy and scanner-race P1 fixes at HEAD `89c20bc`; TV2 has resolved its replay/scanner review threads. TV5's HS256 comment remains awaiting its review. Follow-up reviews from TV2/TV4 now require an approved Product/SKU and Inventory reservation workflow before any live adapter is enabled. TV4 PR #31 has merged an Inventory API but **not** the Checkout consumer contract. The new document [TV3_PROVIDER_CONSUMER_DECISIONS.md](TV3_PROVIDER_CONSUMER_DECISIONS.md) records TV3's **DRAFT proposal** and owner questions. Keep `UnconfiguredCatalogAdapter` / `UnconfiguredInventoryAdapter` fail-closed, Finance gate false; no shared contract or migration V1 changes. Unit/PG GREEN evidence remains historical from the same source snapshot, **not** a new test run. Live integration NOT_RUN.

## TV5 supplemental regression fixes — 17/10/2026

See TEST_RESULTS_TV5_FIXES.md for RED/GREEN evidence on the separate fix branch. Order JWT environment now binds the actual HS256 decoder; successful/uncertain-commit reads fetch SellerOrders scoped to customer; stale scanner and failure-handler transitions condition their UPDATE on PROCESSING. Final two-service build:56unit +11PostgreSQL PASS,0fail/error/skip. Historical NOT_RUN statements below describe TV3's original followup head63f63c1, not this tested fix branch. Parent integration and owner reviews remain pending. Finance policy and shared contracts are unchanged.

## Idempotency / crash safety
- Unique (customer_id,idempotency_key) in Order DB; same key with different payload returns 409.
- Persistent attempt UUID is deterministic Order and Inventory reservation correlation ID; never generate a fresh Order ID on replay.
- Only newly created PROCESSING requests may call Inventory. Existing PROCESSING/FAILED/NEEDS_RECONCILIATION requests never reserve again.
- Before reserve, confirmed Cart claims can be released on deterministic business failures.
- After Inventory reserve was attempted, or after uncertain DB commit, never release Inventory/Cart automatically.
- After write timeout, Order DB read may prove success; otherwise mark NEEDS_RECONCILIATION or leave PROCESSING for scanner if database is unavailable.
- Scheduled scanner marks old PROCESSING as NEEDS_RECONCILIATION without remote compensation. Operator-led reconciliation with TV4 is mandatory; this is NOT a complete automatic recovery worker.
- See TV3_RECONCILIATION_RUNBOOK.md. Production gate remains blocked until TV4 Inventory status/reconcile contract is verified.

## Money / TV5
- No hardcoded commission rate or currency scale in TV3 MoneyRules. Default HK5_FINANCE_POLICY_APPROVED=false refuses Checkout before Inventory reserve.
- Only after written TV5/TV4 approval: set HK5_FINANCE_POLICY_APPROVED=true, HK5_FINANCE_PLATFORM_RATE, HK5_FINANCE_CURRENCY_SCALE, HK5_FINANCE_HOUSE_EXEMPT from controlled runtime configuration.
- TV5 must explicitly approve gross/platform_fee/seller_net, House Store exemption and rounding. Do not infer policy approval from mock tests or FinanceRules sample in main.

## JWT (TV1 Contract v1.0 merged #24)
- HS256 only: JWT_ISSUER_URI, JWT_AUDIENCE, JWT_HS256_CURRENT_KID, JWT_HS256_CURRENT_KEY_B64 and optional previous kid/key pair; no JWKS or fallback.
- Roles and ownership verified on server; /internal/** never open via public Gateway.

## Test commands / evidence
cd backend
./gradlew :cart-service:test :order-service:test --rerun-tasks --console=plain
./gradlew :cart-service:postgresIntegrationTest :order-service:postgresIntegrationTest --rerun-tasks --console=plain
git rev-parse HEAD

PASS (earlier c3150c6 only): author's Windows Gradle build and unit test log.
NOT_RUN (revised head): security tests, recovery tests, PostgreSQL16/Flyway/Hibernate, live Auth/Product/Inventory, Kafka, E2E.
FAIL: No new test run executed in current environment; this does NOT imply no defects.
PostgreSQL tests use Testcontainers and Docker, need real logs and SHA before TV1 re-review.
Migration V1 remains unchanged; create V2+ if deployed schema needs changes.
