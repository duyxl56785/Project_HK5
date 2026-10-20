# TV2 Catalog handoff — 2026-10-18

Refs #2, [PR #37](https://github.com/duyxl56785/Project_HK5/pull/37), [Checkout PR #28](https://github.com/duyxl56785/Project_HK5/pull/28). **PARTIAL / runtime integration BLOCKED.** Branch: `feat/tv2-week1-completion`. Tested source: `1f2ebf5366e6046108825fd677f74aba253a3517`; subsequent report changes are documentation only.

## What can actually be called

### Current provider inventory — 2026-10-20

Audited main `03fb1b2e8a1c1261568446c1f9ed68d264784e4e`. This section supersedes the historical
Store/S01/pending-merge wording below; previous test counts remain separate historical executions.
Category/Seller/domain/Store DB/Image Validation PR7/29/37/52/55 are merged; PR53 merged a **proposal only**.

| Source/interface | Production availability | Consumer boundary |
|---|---|---|
| `StoreCurrentSource.findById/findBySellerId/findBySlug` | Real local Seller DB projection: Store/Seller IDs, OPEN/CLOSED and Seller status, both versions; eligible = OPEN AND APPROVED | Same module only, READ_COMMITTED observation; no HTTP, Account/recipient, House or commit-time lease |
| Seller/Store persistence services | Real REQUIRED transactions, uniqueness and optimistic locking; explicit verified-policy wiring still absent | Internal records are not agreed HTTP DTOs or production JWT proof |
| `CatalogSnapshotSource.load(Set<UUID>)` | Internal interface only: exactly one FOUND/ABSENT/UNAVAILABLE result per ID; no authoritative Product adapter | Enum FOUND is not verification; missing/stale/unverified dependency fails the entire batch |
| `CatalogBatchValidator.validate(List<ExpectedItem>)` | Existing internal proposal,50 unit cases; no production bean/provider | No usable READY subset on rejected batch; no consumer HTTP contract test claimed |
| Product→inventorySku and current price/currency/House | Product production table/JPA/API absent; SQL candidates and JSON fixture remain test-only | No runtime global UNIQUE SKU proof, durable Product seed or live two-Store Checkout |

S01 **Store DB boundary is decided and implemented**, not waiting for another architecture vote:
[TV1 scope decision](https://github.com/duyxl56785/Project_HK5/pull/48#issuecomment-6251503735),
[merged Store PR52](https://github.com/duyxl56785/Project_HK5/pull/52). Product keeps logical Store ID only.
TV3 [accepted C01/E01 v0.2 consumer design](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248646211);
TV4 [confirmed SKU-only/exact immutable global target and Inventory requirements](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6282124816).
Neither requires a repeat technical confirmation; shared wire/security/freshness approval is still missing.
`CatalogPort.verify(String)` on current Order still lacks SKU/currency/batch; its adapter remains TV3-owned.

M01 latest Product price candidate is **M01-PRICE-SCOPE-v1-DRAFT** (explicit VND, integral, unit cap1,000,000,000),
not the earlier17-digit unit-cap proposal and **not business approved**. See the existing
[decision packet §14](TV5_M01_HUMAN_DECISION_PACKET.md#14-m01-price-scope-v1-draft--đề-xuất-đơn-giá-product-19102026).
No unit cap may be applied to Order aggregates by implication. Price persistence remains blocked on accountable
authority and applicable same-version price/schema approvals; House/current authority and freshness are separate gates.

The candidate endpoint remains closed. Missing/duplicate/unrequested/partial/stale/unverifiable results abort the
whole batch; only confirmed authoritative ABSENT permits PRODUCT_NOT_FOUND. Inventory consumes trusted SKU and
positive long quantities, never Product ID. Stable ID/type/canonical line sets and UNKNOWN recovery remain mandatory;
Order journal/fencing PR54/58 now exists, but disabled transport does not prove live Inventory or delegated authorization.
No fixture may substitute for production facts or stock. [Current W1 matrix and blockers](TV2_WEEK1_PROGRESS.md#current-w1-boundaries--2026-10-20).

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

Latest code checkpoint: [Catalog source consistency fix](TV2_CATALOG_SOURCE_FIX.md), tested/pushed source `63668b870c6e2e65e86c28b8c79715f2d0a9e7c8`. Source now requires explicit FOUND/ABSENT/UNAVAILABLE and full ID coverage; omission is never proof of absence. Rejected batches expose no snapshots. Catalog50 unit cases PASS; fresh two-module build **367PASS/0FAIL/0SKIP**, including49 actual PG invocations (11 SQL prototype). This supersedes the old internal omission ambiguity; shared C01/E01/S01/M01/Security and production integration remain gated. Earlier counts above remain historical.

## Independent schema preparation and pending API acceptance — 2026-10-18

The new explicit user scope permits useful test-only SQL preparation while S01/M01 remain pending. Commits `8abc1ce` and `3ecf9c3` extend that preparation; they do not convert a technical candidate into an approved shared contract. Current full-run evidence and feature classification are in [the existing progress checkpoint](TV2_WEEK1_PROGRESS.md#independent-database-verification--2026-10-18). Do not add focused-run totals to the full-run count.

### Candidate schema / production migration handoff

| Area | Verified candidate and existing domain | Production decision/work still required |
|---|---|---|
| Store location | `seller-service/src/test/resources/proposals/store-candidate.sql` is manually applied **after** unchanged Seller Flyway V1 in a disposable PostgreSQL database | TV1 S01 must select service/database and current ownership authority before any runtime migration/JPA |
| Store identity/ownership | UUID PK; local restrictive Seller FK; one Store/Seller and unique lowercase canonical slug; required name/open. Owner is derived by exact local Seller join, not copied from request | Local join is one candidate only, not a cross-service FK decision. Domain Store still carries resolved owner; production adapter must supply verified current facts |
| Eligibility | Store domain rechecks APPROVED/ownership for mutations. DB test demonstrates that a referenced Seller can later become SUSPENDED while the Store row stays open | FK cannot enforce current APPROVED/open/authorization. Runtime policy and public reads must re-resolve these facts; House source remains pending |
| Product persistence | Existing candidate retains logical Store reference, restrictive local Category FK, exact NUMERIC price, explicit currency, case-sensitive global SKU, lifecycle and nonnegative version | Store reference type/authority, schema/money bounds and status/reason mapping need approval. Candidate logical Store text is not the proposed wire UUID contract or a Store existence proof |
| Product metadata/concurrency | Candidate adds non-null TIMESTAMPTZ created/updated, updated >= created. Explicit SQL command increments version and sets updated_at; competing expected-version updates affect 1/0 rows | Not JPA @Version, no automatic timestamp trigger or cross-source quote/lease. Future REQUIRED orchestration/JPA must be exercised against reviewed runtime migration |
| Immutable SKU / Category deletion | Domain edit keeps SKU immutable; SQL proves uniqueness and restrictive Category deletion, including concurrent duplicate inserts | Candidate allows direct SQL SKU updates. Production mapping/command/DB permissions must preserve immutability; Category API needs reviewed in-use error mapping and concurrent reference integration |
| Representation | No price scale/default currency selected. Exact owner/SKU case is preserved | VARCHAR uses PostgreSQL character counts while current domain name/SKU limits use Java UTF-16 units; Unicode normalization/length parity and whitespace rules need explicit request/persistence validation before production |

Both SQL candidates live outside `src/main/resources/db/migration`; tests assert only V1 is recorded in Flyway history. No V1, shared build or runtime route changes. Future approved implementation must use a **new** migration in the selected owning database, validate JPA against it, retain restrictive constraints and translate unique/FK/optimistic conflicts without hiding unrelated DB failures. Do not copy a cross-database FK or the Category REQUIRES_NEW/advisory-lock pattern into Seller/Store orchestration. Re-run real PostgreSQL concurrency and rollback checks on that production implementation; prototype PASS cannot substitute for it.

### Reusable acceptance matrix (specification, not executable HTTP tests)

No Seller/Store/Product/Catalog production endpoint is exposed by this delivery. The cases below are **NOT_RUN for HTTP/JWT** until the indicated gate is approved. Existing domain, SQL and negative-security tests are supporting evidence only. Exact status/error envelopes are assertions to finalize with owners, not new shared contract declarations.

| Case | Future action and required observation | Existing evidence / gate |
|---|---|---|
| SELLER-01 | Verified account registers once; REJECTED resubmit retains ID; persisted transition clears/requires reason correctly | Seller domain + runtime PostgreSQL. REST/DTO/error mapping/current principal: TV1/TV2 |
| SELLER-02 | Two verified requests for one exact owner cannot create two profiles; outer rollback leaves no write | Seller runtime unique/REQUIRED/concurrency PG. HTTP conflict mapping: pending |
| AUTH-01 | Missing/invalid JWT, forged owner/role headers and stale grants cannot gain writes or moderation; self-moderation denied | Policy unit/negative route tests only; positive JWT/current-role integration: TV1 |
| STORE-01 | Current APPROVED Seller creates one Store; duplicate Seller or canonical slug conflicts; foreign Seller cannot read private data/edit/open/close | Store domain + candidate PG, not runtime. S01/DTO/auth required |
| STORE-02 | Suspend Seller or close Store after Product approval; subsequent public/Checkout lookup rejects it; never trusts client sellerId/open flags | Domain visibility tests; authoritative source/freshness/security: TV1/TV2/TV3 |
| PRODUCT-01 | Create/edit permitted states; all forbidden transitions denied; edit preserves Product/Store/SKU identity and exact price | 76 Product domain invocations. Runtime CRUD/JPA/M01/authorization: pending |
| PRODUCT-02 | Cross-Seller edit and seller self-approve/reject/suspend denied; only verified current moderator can moderate | Domain policy tests; actual JWT/Admin/ownership and HTTP: TV1/TV2 |
| SKU-01 | Same exact SKU in two Stores, including concurrent transactions, yields one committed Product; Product ID never substituted for SKU | Candidate PostgreSQL + fixture; runtime global uniqueness/Inventory mapping: TV2/TV4 |
| CATEGORY-01 | Unknown Category rejected; referenced Category cannot be deleted during Product creation/deletion race | Domain lookup + candidate FK only. Runtime Product FK/Category conflict API: TV2/TV5 |
| PUBLIC-01 | Public results include only ACTIVE/current APPROVED/open/valid Category; search/filter/page boundaries are deterministic and exclude private fields | Domain projection only. Query/ordering/pagination/search HTTP contract: pending, no implementation |
| CATALOG-01 | 1–100 distinct IDs; exact one result per ID; all backend Product/SKU/Store/Seller/price/currency/House facts; expected fields only compare | 50 Catalog unit cases; candidate v0.2 TV2 provider/TV3 consumer scope accepted, overall C01/E01 REVISE |
| CATALOG-02 | Explicit authoritative ABSENT may map to NOT_FOUND; omitted/duplicate/unrequested/null/contradictory/unavailable/stale source rejects whole batch, no usable READY subset | Internal regression PASS; trusted adapter freshness and wire serialization/provider-consumer HTTP tests absent |
| CATALOG-03 | Change price/status/Store/Seller/House/SKU/Category between reads; reject changed facts under signed policy before reserve/write; no false commit-time guarantee | No production source/lease. MD07 last-verified race acceptance: TV1/TV5/named human; TV3/TV4 journal/recovery prerequisite for Phase B |
| CATALOG-04 | Candidate 200 evaluated/allReady=false still aborts Checkout; candidate 400/401/403/503 map strict envelope/challenge/privacy; forged service/delegated actor never gains scope | Negative route tests only. TV1 pair/delegation/privacy + C01/E01 approval required before executable positive API tests |
| TWO-STORE-01 | Two independently owned Stores map distinct Product UUIDs to exact SKUs, prices and Category; private DRAFT withheld; repeated seed creates no duplicates | Existing `Week1CatalogFixtureTest` checks TEST_ONLY_NOT_PERSISTED JSON, not durable seed. No actual Inventory stock, positive Catalog call or Checkout E2E |

Reuse the existing fixture mapping table in [the progress report](TV2_WEEK1_PROGRESS.md#reusable-two-store-fixture). Three public candidates plus one private DRAFT already exist; no duplicate fixture or invented stock is added. Once persistence is approved, build an idempotent seed through the reviewed ownership flow, then coordinate exact SKU/balances with TV4 and consumer tests with TV3. Keep Finance disabled and adapters fail-closed meanwhile.
