# TV2 Catalog handoff — 2026-10-18

Refs #2, [PR #37](https://github.com/duyxl56785/Project_HK5/pull/37), [Checkout PR #28](https://github.com/duyxl56785/Project_HK5/pull/28). **PARTIAL / runtime integration BLOCKED.** Branch: `feat/tv2-week1-completion`. Tested source: `1f2ebf5366e6046108825fd677f74aba253a3517`; subsequent report changes are documentation only.

## What can actually be called

**No production Catalog provider endpoint exists yet.** `/internal/catalog/checkout/validate` is a proposed POST path, not a controller or agreed HTTP contract. The existing product-service fallback denies it with 403 `PRODUCT_ROUTE_AUTH_NOT_CONFIGURED`; missing/bogus credentials, forged identity headers, test ADMIN and arbitrary internal tokens cannot open it. Existing public Category reads remain unchanged. No Store/Product write endpoint is opened.

`CatalogBatchValidator` and `CatalogSnapshotSource` are internal Java proposals with no Spring registration, production source adapter or persistence wiring. A test policy or JSON fixture cannot supply authoritative production facts. Positive Catalog HTTP/JWT integration, persisted Store/Product reads and live Checkout are **NOT_RUN / BLOCKED**, not demonstrated by the tests below.

## Decisions requiring owner confirmation

The [decision request on PR #37](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6238333126) remains pending. No owner approval of S01/C01/M01/E01 was found at this checkpoint.

| Decision | TV2 proposal / present fact | Confirmation needed |
|---|---|---|
| SKU ownership | Product owns immutable exact `productId -> inventorySku`; Product ID is not SKU. Global SKU uniqueness is the target invariant already required by Issue #2. Runtime enforcement is absent. | TV3/TV4 consumer mapping; TV5 Product schema review |
| S01 Store boundary | Store in seller-service/Seller DB with local Seller FK, one Store per Seller, canonical unique slug; Product in Product DB with logical Store ID. No cross-database FK/join. **Proposal only.** | TV1 + TV2 before production JPA/Flyway |
| C01 batch read/validate | Proposed POST path above, 1–100 distinct Product IDs, expected Store/Seller/price/currency used only for comparison. Limit/shape/error mapping are drafts. | TV3 DTO/semantics; TV1 service pair, delegated actor/current authorization |
| M01 money | Exact BigDecimal comparison, no rounding, conversion, default currency or invented precision/scale. Provider must supply currency explicitly. Test currency labels are not a money policy. | TV3 + TV5 currency/code, precision/scale, checkout price-change handling |
| E01 outcomes | Internal item codes below; malformed batch and unavailable/inconsistent source fail the batch. No approved HTTP status mapping. | TV3 + TV1 HTTP/error/security contract |

TV4's [PR #38 handoff draft](https://github.com/duyxl56785/Project_HK5/blob/9b8ae3557d1b1b50ee27d1579c7cd740576e0648/backend/inventory-service/INVENTORY_CHECKOUT_CONTRACT.md) retains a SKU-only Inventory key and correctly distinguishes the target invariant from enforcement. This delivery changes no Inventory key, operation, credential or retry/fulfillment behavior. It does not authorize exposing Inventory's broad temporary token to Checkout.

## Concrete internal DTO proposal for TV3

- Input item: `productId`, `expectedStoreId`, `expectedSellerId` (UUIDs), `expectedUnitPrice` (BigDecimal), `expectedCurrency` (exact string). Required, positive price, no duplicate Product IDs. Request values never create the trusted snapshot.
- Successful snapshot: `productId`, `inventorySku`, `storeId`, `sellerId`, `unitPrice`, `currency`, `available`, `sellerApproved`, `storeOpen`, `houseStore`. No ownerReference or moderation reason.
- Per-item result: `productId`, `code`, `snapshot`; snapshot exists only for `READY`. Other codes: `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_SELLABLE`, `OWNERSHIP_MISMATCH`, `PRICE_CHANGED`, `CURRENCY_MISMATCH`. Results preserve request order; snapshot lists are immutable. These Java records are not published HTTP DTOs.
- Eligibility: ACTIVE Product, currently APPROVED Seller, open Store and existing Category; then expected identity/currency/price comparison. `10.0` equals `10.0000`; no value rounding. Ownership mismatch compares Product/Store/Seller facts; it does not authenticate the caller.
- The source is called once per batch. Exceptions, null/duplicate/unrequested facts, Store mismatch or conflicting SKUs cause `DEPENDENCY_UNAVAILABLE`; invalid requests cause `INVALID_REQUEST`. No client/fixture fallback and no partial ready response from an inconsistent source.

TV3's current `CatalogPort.verify(String productId)` returns `productId/sellerId/storeId/available/sellerApproved/storeOpen/houseStore/unitPrice`, with no SKU or currency. The proposed batch records are **not a drop-in approved adapter**. TV3 must own its port change after agreement. No consumer contract test is claimed. Current reads also cannot guarantee freshness through Order commit: version/revalidation/price-change and orchestration semantics still need agreement; this validator creates no distributed transaction or price lock.

## Database evidence and limits

`backend/product-service/src/test/resources/proposals/catalog-product-candidate.sql` is outside the Flyway runtime migration directory. A new isolated PostgreSQL Testcontainer runs the unchanged Category V1 migration, then applies the proposal manually to `catalog_product_candidate`. Flyway history still contains only Category V1.

The candidate verifies exact globally unique SKU across Store references, positive finite NUMERIC price without rounding, status checks, restrictive Category FK and nonnegative version. Its Store reference is logical only; it does not enforce Store ownership/approved status, slug uniqueness or Store existence. There is no Product entity, repository, migration or JPA optimistic locking implementation. The SQL ASCII outer-space check is not yet a full Unicode normalization contract. These are review inputs, not production persistence acceptance.

Eleven actual PostgreSQL test invocations cover migration separation, global duplicate SKU, exact-case SKU and durable high-precision price, Category reference/delete rejection, five invalid prices, rollback and concurrent SKU registration. The concurrent case observes the competing transaction waiting on a PostgreSQL lock before the winner commits, then checks SQLSTATE 23505 for the loser. Test cleanup touches only its newly created isolated container database, not existing PostgreSQL data/volumes.

The earlier two-Store JSON fixture remains test-only/not persisted; no live Store/Product/Inventory seed or stock is created.

## Executed verification

Java 21.0.12, Gradle 8.10, Docker Linux Engine, PostgreSQL 16.6 via Testcontainers. From `backend`, process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`:

```powershell
.\gradlew.bat :product-service:build :seller-service:build --rerun-tasks --no-daemon --continue --console=plain
```

**BUILD SUCCESSFUL, exit 0, 46s, 17 tasks executed.**

| Suite | PASS | FAIL/errors | SKIPPED |
|---|---:|---:|---:|
| Product unit/security | 188 | 0 | 0 |
| Product PostgreSQL: Category production regression 14 + SQL prototype 11 | 25 | 0 | 0 |
| Seller/Store unit | 103 | 0 | 0 |
| Seller PostgreSQL production regression | 24 | 0 | 0 |
| Total | **340** | **0** | **0** |

New coverage: 23 batch unit cases, 5 negative HTTP security cases and 11 SQL prototype PostgreSQL invocations. All 49 PostgreSQL invocations above used actual PostgreSQL; 11 concern a test schema only. Module build PASS does not make a positive Catalog API, production Store/Product persistence or Checkout E2E PASS. No new full-backend run was performed; the earlier 366 PASS / 2 SKIPPED full-backend run remains separately recorded in `TV2_WEEK1_PROGRESS.md` and its original CSV.

Initial `:product-service:test` failed at `compileTestJava` because generic inference rejected a list containing empty/null request samples. Added an explicit generic type witness, preserving every case, then reran the builds successfully. No test was deleted or disabled. Initial log: `backend/seller-service/build/catalog-batch-unit.log`, SHA256 `3CB25049DF93BC7F510E2CDA8000C4C02FCEA94C873ED314FF7568B00867F6CE`.

Canonical final log: `backend/seller-service/build/catalog-handoff-final.log`, SHA256 `5F32BA9958AAD4B6E23F76C402B291A3B5F34F7AD79E11DB9D9668FDA5EBF222`. Logs/XML remain ignored build artifacts; machine-readable committed summary: `TV2_CATALOG_TEST_SUMMARY.csv`. `git diff --check` PASS. No shared build, existing migration, security implementation or other owner's source changed.

## Checkpoint / next implementation gate

Commits: `9d3b47e` internal batch proposal/security regression; `1f2ebf5` isolated PostgreSQL candidate constraints. Documentation follows without compiled-source changes. Read latest PR #37/#28 comments, Issue #2 and Git status before resuming; preserve subsequent owner decisions.

1. TV1 confirms S01 Store data boundary and actual service/actor authorization; TV3 confirms C01/E01; TV3/TV5 confirm M01; TV4 confirms SKU consumer mapping. Do not interpret silence as approval.
2. Only then add reviewed Store/Product V2+ migrations/JPA, authoritative status/ownership adapters and fail-closed authenticated Catalog route. Retest local transactions, unique slug/SKU/concurrency, Category references and cross-Seller access against production schema.
3. Persist idempotent two-Store seed through the agreed flow, coordinate Inventory through its approved boundary, implement consumer contract tests with TV3 and test live calls. Keep Finance/Checkout gates closed until their own prerequisites are met.

Issue #2 stays OPEN/PARTIAL: no additional composite business acceptance checkbox is complete. PR #37 was observed OPEN and non-draft during this checkpoint (previous delivery called it Draft); this task preserves that externally changed state, does not approve/merge and does not claim integration readiness.

Follow-up after TV3/TV5 feedback: [TV2 Catalog contract candidate v0.2](TV2_CATALOG_CONTRACT_RESPONSE.md) gives the technical response, still pending joint/human approvals. [PR review and new Cart/Order verification](TV2_PR_FOLLOWUP_2026-10-18.md) records the published PR40/PR28 reviews and 85 new tests at PR28 source55bf5ff. These do not change this handoff's historical Product/Seller test scope or create a production Catalog API.
