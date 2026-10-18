# TV2 — Week 1 implementation and handoff

Refs #2. Status: **PARTIAL / integration BLOCKED**. Updated 2026-10-18 (Asia/Saigon).

Delivery: [Draft PR #37](https://github.com/duyxl56785/Project_HK5/pull/37), `feat/tv2-week1-completion` → `main`. Verified code commit: `20f684d699a288ee5f558c38aec40e934bc91939`; subsequent report/README changes are documentation only. Review decisions belong to TV1/TV3/TV4/TV5; this task does not approve or merge the PR. [Issue #2](https://github.com/duyxl56785/Project_HK5/issues/2) remains the acceptance source of truth.

## Baseline and scope

Branch `feat/tv2-week1-completion` starts from main `edb6886`, after #7, #29, #27, #26 and #31 were merged. Working tree was clean; existing Category/Seller code and migrations are preserved. Issues #9/#15 were read for scope only; this change does not implement shared UI or all W2/W3 work.

The user confirmed that Store DB/module boundaries, Seller/Store/Product HTTP contracts and the current-ADMIN authorization dependency are **not agreed yet**. Continue independent domain/tests and keep APIs fail-closed. Auth v1.0 fixes HS256/identity/policies, but does not approve these business routes or provide a working current-authorization adapter. No new controller, Spring production policy bean, default allow policy, schema, migration, storage adapter, Gateway route or shared DTO is introduced here.

## Implemented independently

- Seller `findOwn`: verified-policy check before exact owner lookup; accepts no target owner supplied by a client. `findForModeration`: authentication + moderation policy before ID lookup. REQUIRED read-only queries; existing registration/moderation writes unchanged. New PostgreSQL tests cover committed versions, missing/denied actors and exact opaque identity semantics.
- Store immutable internal model and lifecycle: policy resolves the actor's persisted Seller, checks owner mapping and current APPROVED on every operation, creates/revises/closes/reopens. Lowercase hyphenated slug and metadata bounds are an **internal proposal**, not a shared contract. No database uniqueness or one-Store constraint is claimed.
- Product immutable internal model and six states, guarded lifecycle and public projection. Policy must verify roles/current authorization and provide authoritative Store facts; service independently checks Store identity/owner, approved/open status and self-moderation. Category existence uses the existing repository. SKU stays unchanged on edit, prices use exact BigDecimal without assigning a currency/scale. State transitions/reason limits are an **internal proposal for review**, not a published enum/API/schema.
- Public projection returns only ACTIVE with approved Seller, open Store and an existing Category. This is a domain rule, not a public search API. Store/Product policies have no production implementation or component registration.

## Proposed Product transitions for owner review

| Operation | Allowed source | Target |
|---|---|---|
| create | new | DRAFT |
| edit | DRAFT, REJECTED, INACTIVE | same state, immutable ID/Store/SKU |
| submit | DRAFT, REJECTED, INACTIVE | PENDING_REVIEW; clear reason |
| approve | PENDING_REVIEW | ACTIVE; clear reason |
| reject | PENDING_REVIEW | REJECTED; required reason <=500 code points |
| suspend | ACTIVE | SUSPENDED; required reason <=500 code points |
| deactivate | ACTIVE | INACTIVE |

Admin role verification belongs to the future policy; moderation is denied for the Store owner even if that owner is an Admin. Seller cannot reactivate directly or escape SUSPENDED by editing/submitting. Mocks/fixture policies are test-only, not JWT proof. Future persisted mutations must validate live status and concurrency again inside the agreed orchestration boundary.

## Reusable two-Store fixture

Source: `backend/product-service/src/test/resources/fixtures/week1-catalog.json`. **TEST_ONLY_NOT_PERSISTED**. It contains two distinct Account/Seller/Store mappings, one Category, three ACTIVE Product candidates and one private DRAFT. Product IDs and SKUs are deliberately different; the SKU strings are unique within this fixture only.

| Store | Product | SKU | Exact price | Candidate stock |
|---|---|---|---:|---:|
| `40000000-0000-4000-8000-000000000001` | `50000000-0000-4000-8000-000000000001` | TV2-DEMO-A-001 | 10000 | 20 |
| `40000000-0000-4000-8000-000000000002` | `50000000-0000-4000-8000-000000000002` | TV2-DEMO-B-001 | 15000 | 20 |
| `40000000-0000-4000-8000-000000000001` | `50000000-0000-4000-8000-000000000003` | TV2-DEMO-A-002 | 25000 | 10 |

TV3 may load this file in **test-only** Catalog/Inventory fixtures, retaining its existing port field names. `Week1CatalogFixtureTest` validates identities, references, fixture uniqueness, actual submit/approve domain transitions and public visibility for two Stores. Suggested quantities are not actual Inventory balances. Currency/money policy is not chosen or enabled by these numbers.

TV4 can review the explicit SKU/quantity mapping as a candidate input. Do not seed a live Inventory DB, run production HTTP initialization or assume global business mapping before agreeing the pair/auth and data contract. No seed SQL, credentials, tokens, account bootstrap or destructive DB operation is included. Re-loading JSON has no DB side effect; durable seed/idempotent insertion and live Checkout remain NOT_RUN/BLOCKED.

## Open decisions and next safe steps

| Owner | Required decision / evidence |
|---|---|
| TV1 + TV2 | Store module/database boundary; Seller/Store/Product paths, DTO/errors; current-ADMIN and verified policy adapter; role sync and delegated internal operations |
| TV2 + TV3 + TV4 | Authoritative Product/Store read contract, persistent productId/SKU mapping and namespace; reservation line set and lifecycle/reconciliation |
| TV2 + TV5 | Product schema, money wire/currency/scale, visibility/moderation rules and Review compatibility |
| TV1 + TV2 | Image storage, validation/decode/size and safe file delivery; no upload implemented |

Store/Product JPA/Flyway/unique owner+slug+SKU, restrictive Product→Category FK and concurrent deletion protection are NOT_IMPLEMENTED pending these decisions. Domain category lookup is not a substitute for the FK. Store/Product API/JWT/CRUD/search/filter/pagination/media, persistent two-Store seed, Gateway/UI/live E2E remain NOT_RUN. Composite Issue #2 acceptance checkboxes must stay unchecked.

## Verification and recovery

Phase: verification complete, Draft PR #37 opened, contract review pending. Code commits: `e744de7` Seller guarded reads; `c0c2967` Store domain; `f4e52f2` Product domain; `20f684d` two-Store fixture. No force push, reset or main changes. No module owned by another member is edited.

Executed final verification on source commit `20f684d699a288ee5f558c38aec40e934bc91939`, Java21.0.12/Gradle8.10, Docker Linux Engine and real PostgreSQL16 Testcontainers. From `backend`, process-only `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 -Duser.timezone=UTC`:

```powershell
.\gradlew.bat build :inventory-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain
```

**BUILD SUCCESSFUL, exit0, 1m18s, 71 tasks executed.** 11 backend modules assembled. New documentation after this run does not change compiled source.

| Suite | PASS | FAIL/errors | SKIPPED |
|---|---:|---:|---:|
| Seller unit (includes 19 new Store domain cases) | 103 | 0 | 0 |
| Seller PostgreSQL (21 existing +3 guarded-read regressions) | 24 | 0 | 0 |
| Product unit/security (includes 76 new Product domain +1 fixture case) | 160 | 0 | 0 |
| Category PostgreSQL regression | 14 | 0 | 0 |
| Inventory unit | 17 | 0 | 0 |
| Inventory PostgreSQL | 14 | 0 | 0 |
| Order Finance unit regression | 33 | 0 | 0 |
| Auth dependency check / readiness | 1 | 0 | 1 |
| User readiness | 0 | 0 | 1 |
| Total | **366** | **0** | **2** |

Auth/User readiness are disabled by their existing `TV1_DATABASE_TESTS=true` environment condition; no provisioned TV1 database credentials were supplied in this task. They are **SKIPPED / readiness NOT_RUN**, not PASS. NO-SOURCE modules are assembly outputs only. The Inventory PostgreSQL task is explicit because its module build/check does not include it. 52 PostgreSQL invocations ran; none were skipped. Results from intermediate runs are not added to this total.

Source code review + `git diff --check` PASS for the TV2 change. Schema/Flyway files and shared build remain unchanged. Actual new tests: 4 Seller policy-denial unit +3 Seller PostgreSQL +19 Store domain +76 Product domain +1 fixture =103 added invocations; none prove live JWT, Store/Product DB constraints or E2E.

Machine-readable summary: `docs/TV2_WEEK1_TEST_SUMMARY.csv`. Raw log (ignored): `backend/seller-service/build/week1-backend-final.log`, SHA256 `3BE0F8D25D0C9F898B994663AB908F3BBB2EEEAE9886F91E4050AD4301CDA820`; XML under each module's `build/test-results/{test,integrationTest,postgresIntegrationTest}`. No credentials/tokens/raw logs committed.

Resume: read this file + latest Issue #2/PR #37 comments and git status. Use Java21 and `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` process-only. From `backend`, run `.\gradlew.bat :seller-service:build :product-service:build --no-daemon --console=plain` only when source changes justify it; the full command above is the established baseline. Never count NO-SOURCE or skipped readiness tests as PASS. Do not repeat completed work if source/evidence still match.

## Audit / acceptance matrix

| Issue #2 area | Initial | New status | Checklist decision |
|---|---|---|---|
| Category hierarchy/cycles/transactions | DONE at module scope | DONE, 14 PostgreSQL + existing unit/security regression PASS | Keep existing checked Category item |
| Seller domain/persistence | DONE, API/JWT absent | Added guarded queries; 103 Seller/Store unit +24 Seller PG PASS | Main Seller flow remains unchecked |
| Store | MISSING, data/API boundary unresolved | PARTIAL: internal model/lifecycle +19 unit cases | Store/slug/ownership DB/API composite remains unchecked |
| Product/moderation | MISSING, schema/consumer contract unresolved | PARTIAL: internal lifecycle +76 domain cases | CRUD/global SKU/images/moderation/JWT composites remain unchecked |
| Public catalog/search | MISSING | PARTIAL: domain visibility projection, no HTTP/search/filter/page | Public catalog acceptance remains unchecked |
| Two-Store data | MISSING | PARTIAL: reusable candidate fixture +1 domain verification PASS | Durable seed and Checkout handoff acceptance remain unchecked |
| Full backend verification | Historical 231 PASS on older main | New 366 PASS,0 FAIL,2 SKIPPED on code20f684d | Record this run separately; not E2E |

The top-level Issue #2 boxes combine API/JWT/database/end-to-end behavior. No additional main acceptance box qualifies for ticking in this delivery. Append granular verified progress separately; preserve all historical notes/checkboxes and keep Issue OPEN/PARTIAL.

## Files in this delivery

- `backend/seller-service/.../seller/service/SellerPersistenceService.java` and its unit/PostgreSQL tests: guarded reads, no write semantic change.
- `backend/seller-service/.../store/{StoreProfile,StoreRuleException,StoreAccessPolicy,StoreLifecycleService}.java` and `StoreLifecycleServiceTest`: internal Store proposal.
- `backend/product-service/.../product/{ProductListing,ProductStatus,ProductRuleException,ProductAccessPolicy,ProductLifecycleService}.java` and `ProductLifecycleServiceTest`: internal Product/Catalog proposal.
- `Week1CatalogFixtureTest.java` and `src/test/resources/fixtures/week1-catalog.json`: reusable test-only data and verification.
- Both module READMEs, this progress/report and `TV2_WEEK1_TEST_SUMMARY.csv`: accurate readiness and recovery instructions.

No new API or migration. Draft PR #37 is the single delivery PR with Refs #2; report/README commit follows the four verified code commits. Request TV1/TV3/TV4/TV5 review and record granular progress on Issue #2 without checking its composite unfulfilled acceptance items. Do not Approve/Merge or close Issue #2. Before any future runtime wiring, first obtain the missing contracts and replace test-only policies with verified adapters; do not expose these domain types directly as request DTOs.

## Catalog follow-up checkpoint — 2026-10-18

The preceding sections and CSV preserve the historical baseline. Latest tested source is `1f2ebf5366e6046108825fd677f74aba253a3517`: internal batch validator (23 unit cases), five proposed-route security-denial cases and eleven isolated PostgreSQL SQL-prototype cases. Two-module build rerun: **340 PASS / 0 FAIL / 0 SKIPPED**, including 24 Seller +14 Category production PostgreSQL regressions and 11 prototype PostgreSQL invocations. This is a different scope from the historical full-backend run; do not combine totals.

See [Catalog handoff and pending contract decisions](TV2_CATALOG_HANDOFF.md) and [current run CSV](TV2_CATALOG_TEST_SUMMARY.csv) for DTO proposal, exact commands/log hashes, initial compile failure and correction, limitations and next implementation gates. No Catalog API, Store/Product JPA/runtime migration or live seed has been introduced. S01/C01/M01/E01 are still pending owner confirmation; Issue #2 remains PARTIAL. PR #37 is now observed OPEN/non-draft; earlier Draft references above describe the original handoff, not an automatic change by this task.

## Catalog explicit-source checkpoint — 2026-10-18

Latest tested/pushed code: `63668b870c6e2e65e86c28b8c79715f2d0a9e7c8`, branch `feat/tv2-week1-completion`, PR37. Three Java files changed: CatalogSnapshotSource, CatalogBatchValidator and its unit test. Explicit FOUND/ABSENT/UNAVAILABLE plus complete coverage fixes missing-vs-unavailable; contradictory/current Store facts fail closed; rejected batches withhold every snapshot. Existing23 Catalog cases retained/adapted,27 added. RED0PASS/3FAIL reproduced before fix; GREEN Catalog50PASS and full two-module build **367PASS/0FAIL/0SKIP** (Product215unit+25PG, Seller103unit+24PG). Raw hashes, command, class scopes, limitations and [new CSV](TV2_CATALOG_SOURCE_FIX_TEST_SUMMARY.csv) are in the [source-fix recovery report](TV2_CATALOG_SOURCE_FIX.md). Do not combine historical runs.

TV3 accepted v0.2 as a technical candidate and resolved its QA conflict at28dd0cf; no common contract became APPROVED. S01/M01/Security PENDING; C01/E01 REVISE. Store/Product production JPA/API/auth/seed and live consumer/E2E remain NOT_RUN/BLOCKED. Do not touch TV3 QA/conflict or other-owner modules. Update Issue2's narrow internal-source/regression item after push; leave composite business boxes unchecked and Issue OPEN/PARTIAL. Resume from the new report and latest GitHub decisions; implement persistence/API only when the corresponding boundary/money/security approvals exist.
