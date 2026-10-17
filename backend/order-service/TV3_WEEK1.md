# TV3 W1 — Checkout COD / CHANGES_REQUESTED followup PR #28

**[Cross-owner sign-off ledger](TV3_CONTRACT_SIGNOFF_LEDGER.md):** Six contract gates S01, C01, E01, M01, Inventory, Security; TV1 OD-09 decided as baseline, but pair-specific integrations PENDING. PR #39 introduces a competing SellerOrder state-name proposal requiring TV3/TV4 reconciliation. This W1 report does not constitute approval.


Status: PARTIAL / NOT PRODUCTION READY. No shared contract or other owners' module changed.

## 18/10/2026 — TV2 C01/M01/E01 consumer review at 9c28338

TV3 [COMMENT review of TV2 PR #37](https://github.com/duyxl56785/Project_HK5/pull/37#pullrequestreview-5559408327) gave **APPROVE target SKU uniqueness and batch direction only**, **REVISE wire/adapters**, **PENDING source/auth/money**. Current CartItem + V1 DB store unit_price_snapshot NUMERIC(19,2) and no currency; CartPort.Item has no currency, while TV2's Product proposal requires expectedCurrency and tests high-precision price. TV3 must not fake/default currency, silently round, or alter applied V1. Need TV5 approved precision/currency, TV2 production Product/Store JPA+SKU uniqueness, TV1 scoped internal auth; then implement reviewed Cart V2+/DTO and secure Catalog batch port. TV2 historical Product188 unit+25PG, Seller103 unit+24PG = 340PASS,0FAIL/skip at source 1f2ebf5; 11 PG use a candidate schema only, not the production provider. **Live Catalog/Checkout and new TV3 Gradle/PG E2E still NOT_RUN.**

## 18/10/2026 — TV3 independently verified Inventory payload preparation, provider contract updates

**Source commit:** `792b49fdbaa9d3955d3ad5c8b88360f63928d4d6` on TV3 branch, only two new Java files: `domain/InventoryOperationIntent.java` and `domain/InventoryOperationIntentTest.java`. The new class canonicalizes immutable SKU/long quantity lines, carries a caller-assigned stable per-step operationId and validates a stored SHA-256 fingerprint, matching TV4 provider hash format. It is **not connected to the active Checkout flow** and cannot survive a crash without a future Order DB journal.

**Local evidence on those committed bytes:** Java 21.0.12.1 javac PASS; 21 dependency-free standalone assertions PASS / 0 FAIL, golden provider hash independently matched. Five JUnit-style test methods were also exercised with **local JUnit stubs**, **not actual JUnit/Gradle**. **Gradle/JUnit, PostgreSQL16, Testcontainers, live Catalog, Inventory, JWT/Gateway, COD and E2E all NOT_RUN** in this environment due to absent Gradle distribution/cache, Docker and network. Existing 56 unit + 11 PostgreSQL16 PASS is **historical for code snapshot 89c20bc**, never add these 21 standalone assertions as part of that count.

**TV2 batch consumer reply:** [PR #37 TV3 response](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6238558286). C01 batch validator / global SKU unique are **owner design proposals**, no production enforcement/API. **TV4 PR #38:** [TV3 response](https://github.com/duyxl56785/Project_HK5/pull/38#issuecomment-6238569348), provider contract observed but no service-pair op-scoped identity and new PG regression NOT_RUN. **NEVER pass Inventory's broad X-Inventory-Internal-Token to Checkout.** No fake Product values, Finance override, stock mutations, unknown-outcome releases, or automatic retries without durable intent.

**Next on TV3 only after written owner decisions:** integrate C01/C02 server-authenticated product source, Order-owned V2+ journal persisted before any RESERVE HTTP, safe same-ID replay worker with manual hold, SellerOrder/outbox committed dispatch transitions, PG/concurrency tests and real two-Store E2E. See [decision matrix](TV3_PROVIDER_CONSUMER_DECISIONS.md#tv3-integration-readiness-checkpoint--c01-batch-and-pr-38-18102026). PR #28 remains PARTIAL/BLOCKED.

## 18/10/2026 — TV1 P0 owner gate and evidence

The source fixes were verified at 89c20bc: JWT YAML real-env, replay/uncertain-commit fetch graph, and scanner/failure-handler conditional DB transitions. Historical Cart+Order 56 unit +11 PostgreSQL16 PASS (0 fail/error/skip) uses mocked external ports and is NOT live Checkout E2E; no fresh Gradle run on docs-only HEAD.

See [P0 owner decision and UAT matrix](TV3_PROVIDER_CONSUMER_DECISIONS.md#tv1-p0-owner-and-acceptance-handoff--18102026-docs-only). TV2 must confirm authoritative Catalog/Store/Product/SKU source (PR #37 still OPEN); TV4 confirms Inventory provider errors and proposes DISPATCHED as CONSUME boundary, but C06 SellerOrder transition/event remains unapproved; TV5 must confirm Finance/COD/settlement; TV1 must approve service credentials, Gateway and actor authorization. TV3 owns only Order-side proposed journal/status/outbox persistence and Cart/Order consumer adapters after approved contracts. No outbox publisher/relay or live two-Store E2E is confirmed.

Both adapters remain fail-closed and HK5_FINANCE_POLICY_APPROVED=false. PR #28 PARTIAL/BLOCKED, Issue #3 OPEN. Ask reviewers only after integration code and test logs, never based solely on docs.

## 18/10/2026 — latest PR #28 review / cross-team contract gate

TV1 confirmed the former HS256 environment, replay-lazy and scanner-race P1 fixes at HEAD `89c20bc`; TV2 has resolved its replay/scanner review threads. TV5's HS256 comment remains awaiting its review. Follow-up reviews from TV2/TV4 now require an approved Product/SKU and Inventory reservation workflow before any live adapter is enabled. TV4 PR #31 has merged an Inventory API but **not** the Checkout consumer contract. The new document [TV3_PROVIDER_CONSUMER_DECISIONS.md](TV3_PROVIDER_CONSUMER_DECISIONS.md) records TV3's **DRAFT proposal** and owner questions. Keep `UnconfiguredCatalogAdapter` / `UnconfiguredInventoryAdapter` fail-closed, Finance gate false; no shared contract or migration V1 changes. Unit/PG GREEN evidence remains historical from the same source snapshot, **not** a new test run. Live integration NOT_RUN.

## TV5 supplemental regression fixes — 17/10/2026

See TEST_RESULTS_TV5_FIXES.md for RED/GREEN evidence on the separate fix branch. Order JWT environment now binds the actual HS256 decoder; successful/uncertain-commit reads fetch SellerOrders scoped to customer; stale scanner and failure-handler transitions condition their UPDATE on PROCESSING. Final two-service build:56unit +11PostgreSQL PASS,0fail/error/skip. Historical NOT_RUN statements below describe TV3's original followup head63f63c1, not this tested fix branch. Parent integration and owner reviews remain pending. Finance policy and shared contracts are unchanged.

## Idempotency / crash safety
- Unique (customer_id,idempotency_key) in Order DB; same key with different payload returns 409.
- Persistent attempt UUID is deterministic Order ID and Inventory correlation ID only; do not reuse it as an operationId across RESERVE, RELEASE and CONSUME. Never generate a fresh Order ID on replay.
- Current fail-closed implementation does not perform a second new RESERVE on existing attempts. Proposed future crash recovery may replay only the originally journaled operationId + type + exact SKU/qty line set within an approved retry window; this worker is NOT IMPLEMENTED.
- Before reserve, confirmed Cart claims can be released on deterministic business failures.
- After Inventory reserve was attempted, or after uncertain DB commit, never release Inventory/Cart automatically.
- After write timeout, Order DB read may prove success; otherwise mark NEEDS_RECONCILIATION or leave PROCESSING for scanner if database is unavailable.
- Scheduled scanner marks old PROCESSING as NEEDS_RECONCILIATION without remote compensation. Manual hold/reconciliation after exhausted same-ID retries remains mandatory; TV4 does not require a new status lookup API for the first-stage idempotent replay. Budget/backoff/SLA/alert are PENDING. The scanner is NOT a complete cross-service recovery worker.
- See TV3_RECONCILIATION_RUNBOOK.md. Production gate remains blocked until SKU source, service auth, journal/retry, SellerOrder lifecycle and Finance are approved and live E2E proves the workflow.

## Money / TV5
- No hardcoded commission rate or currency scale in TV3 MoneyRules. Default HK5_FINANCE_POLICY_APPROVED=false refuses Checkout before Inventory reserve.
- Only after written TV5/TV4 approval: set HK5_FINANCE_POLICY_APPROVED=true, HK5_FINANCE_PLATFORM_RATE, HK5_FINANCE_CURRENCY_SCALE, HK5_FINANCE_HOUSE_EXEMPT from controlled runtime configuration.
- TV5 must explicitly approve gross/platform_fee/seller_net, House Store exemption and rounding. Do not infer policy approval from mock tests or FinanceRules sample in main.

## JWT (TV1 Contract v1.0 merged #24)
- HS256 only: JWT_ISSUER_URI, JWT_AUDIENCE, JWT_HS256_CURRENT_KID, JWT_HS256_CURRENT_KEY_B64 and optional previous kid/key pair; no JWKS or fallback.
- Roles and ownership verified on server; /internal/** never open via public Gateway.

## Test commands / evidence — provenance and new test criteria

For a future integration SHA, use Java 21, PostgreSQL 16/Testcontainers and Gradle from the backend directory:

- ./gradlew :cart-service:build :order-service:build --rerun-tasks --no-daemon --console=plain
- ./gradlew :cart-service:postgresIntegrationTest :order-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain
- git rev-parse HEAD

Capture exit code, task logs, JUnit XML, SHA, exact PASS/FAIL/SKIP count and process-only UTC/api.version environment when required; never count a failed Docker setup as passed business cases.

**PASS historical source 89c20bc:** 56 unit + 11 PostgreSQL16/Flyway, zero fail/error/skip, Cart+Order only, with mocked Cart/Catalog/Inventory ports as applicable. See verification/tv5-fixes/final-green/ and TEST_RESULTS_TV5_FIXES.md; RED attempts kept separately.

**NOT_RUN on the current new-source+docs HEAD:** full Gradle/JUnit, PostgreSQL16/Flyway and E2E. Only isolated Java21 standalone checks ran for the new utility at its specific source SHA. **NOT_RUN live:** Auth JWT/Gateway, Product/Seller/Store, Inventory actual stock changes/timeout recovery, SellerOrder transition/event publishing, Finance/COD, FE two-Store UAT. No production contract sign-off or owner approval is inferred from historical PASS results.

Do not change existing Flyway V1. Create reviewed V2+ for new Order-owned persistence only after all providers approve.
