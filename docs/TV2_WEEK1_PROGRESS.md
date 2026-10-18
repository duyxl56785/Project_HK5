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

## Provider-design / freshness reply — 2026-10-18

Audited clean TV2 head9e1794a against remote, main524e4f3 and TV3 head28dd0cf; no new TV2 source. TV3 now confirms consumer-design scope for candidatev0.2 (#6246536042), but S01/M01/Security still lack required approvals. TV5 independently verified Catalog50unit at63668b8 and confirmed the earlier documentation repair; do not repeat that work or present367 historical tests as a new run.

Updated only this checkpoint and the existing [contract response](TV2_CATALOG_CONTRACT_RESPONSE.md): TV2 confirms its own provider-design scope, clarifies whole-invalid-batch snapshot withholding, and recommends last-verified-snapshot acceptance for MVP **subject to TV1/TV5/human approval**, with explicit read→commit and cross-source observation races. No current quote/lease/version token guarantees those facts; a strict guarantee would require a separately agreed protocol. No TTL, new runtime model, migration, DTO/controller or policy flag was introduced. Runtime tests NOT_RUN/docs-only; verify existing evidence, links and diff instead. All composite business boxes remain unchecked; no new production acceptance is earned by this reply.

Resume by checking the exact owner replies on PR37/Issue2. Store persistence may proceed once S01 is approved; Product schema additionally needs applicable M01/schema decisions; Catalog exposure requires C01/E01/security/freshness and actual trusted sources. If these remain pending, do not manufacture more prototypes, reread unchanged tests indefinitely or introduce a quote abstraction. Preserve the current implementation and report the specific decision owner.

## Independent database verification — 2026-10-18

Latest user request explicitly authorizes additional **test-only** Store/Product SQL constraint/concurrency preparation. This narrows the preceding stop guidance: implement actual missing DB evidence, without opening production contract gates or adding redundant abstractions. Audited clean branch `feat/tv2-week1-completion` at42cc4e7, CONTRIBUTING/OWNERSHIP/Auth, latest main `ac7ca4fd8a8fe60a8d70fb15f98af9a0bf47bc59`, PR37 and PR28. Fetch after the first push confirmed main unchanged; PR28 source was21f6d53. No new domain defect was demonstrated; Seller/Category production code, their migrations and the already-fixed Catalog validator remain unchanged.

### Actual changes

- **`8abc1ce2d55e62e20367c5db2050e08bd5d7ee51`**, pushed: new Seller-module `src/test/resources/proposals/store-candidate.sql` and `store/StoreSchemaProposalPostgresTest.java`. Candidate local restrictive Seller FK, derived exact owner, canonical unique slug, one Store/Seller, required metadata/open; real PostgreSQL durable reads, invalid values, rollback and concurrent duplicate Seller/slug tests. Fresh isolated database only; no production Store JPA/schema/API created.
- **`3ecf9c3d5f7794cced7c01e75191f1d9c87f684c`**, pushed and full-build tested: extend existing Product candidate SQL and `CatalogSchemaProposalPostgresTest.java` with timestamps, version compare-and-set, stale/conflicting updates and atomic rollback. Keep all11 prior cases; add13. These are SQL commands, not production JPA @Version or SKU-immutability enforcement.
- This checkpoint plus [existing Catalog handoff](TV2_CATALOG_HANDOFF.md#independent-schema-preparation-and-pending-api-acceptance--2026-10-18): current audit, migration handoff, a reusable 15-case API acceptance matrix and exact remaining owner gates. No additional fixture/report abstraction; historical reports/CSVs stay unchanged.

### Current feature audit

| Feature | Status and verified scope | Remaining work / owner |
|---|---|---|
| Category | **DONE at module scope**: hierarchy/cycles/REQUIRES_NEW/advisory locking;14 actual PG regressions and existing unit/API/deny-write tests PASS | Verified write authorization/Gateway and runtime Product reference integration: TV1/TV2/TV5 |
| Seller | **PARTIAL overall**; domain, production persistence, guarded reads DONE;24 PG and existing unit tests PASS | REST/JWT/current-role policy/role sync/live authorization: TV1/TV2 |
| Store | **PARTIAL**:19 domain cases +26 candidate PG cases PASS; current exact owner/APPROVED checked in domain | **BLOCKED** production boundary/JPA/unique constraints/CRUD/auth on S01: TV1/TV2 |
| Product | **PARTIAL**:76 domain cases; candidate SQL has24 PG cases incl SKU/FK/version/time | **BLOCKED** production schema/JPA/API/current Store source and M01: TV1/TV2/TV5 |
| Catalog | **PARTIAL**:50 internal unit cases, whole-invalid batch withholding, negative HTTP denial PASS | **BLOCKED** authoritative adapter, positive HTTP/wire/pair/privacy/freshness and consumer integration |
| Moderation | **PARTIAL**: Seller runtime orchestration + Product pure lifecycle/self-moderation unit tests | Verified current ADMIN/owner at HTTP boundary and Product persistence: TV1/TV2 |
| Images | **NOT_STARTED** | Storage/validation/serving/limits contract and implementation: TV1/TV2 |
| Search/filter/pagination | **NOT_STARTED**; existing visibility projection is not a search query | Reviewed query/ordering/page/API contract and persistence: TV2 |
| Fixtures/seed | **PARTIAL**: existing two-Store JSON/domain test PASS; mapping already documented | Durable idempotent seed/actual stock/live Checkout **BLOCKED/NOT_RUN**: TV1/TV2/TV3/TV4 |
| Authorization | **PARTIAL**: fail-closed routes/negative-security tests PASS | Positive JWT/scoped pair/delegated actor/current grants **BLOCKED/NOT_RUN**: TV1 |
| Database migrations | Category/Seller production V1 **DONE** and regression PASS; candidate SQL preparation **DONE in test scope** | Store/Product runtime migrations **BLOCKED**, no V1 change or cross-DB FK |
| Unit/PostgreSQL tests | **DONE for implemented local scope**, new full two-module run below | Positive Store/Product/Catalog API/provider-consumer/live E2E **NOT_RUN**, not inferred from tests |

### New actual verification (not historical367)

Java21.0.12, Gradle wrapper8.10, process UTC, Docker Engine29.8.2 Linux, PostgreSQL16.6 Testcontainers. Both module `check` tasks depend on their tagged PostgreSQL `integrationTest` task. Commands executed from `backend`, with `JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot` and `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`:

```powershell
.\gradlew.bat :seller-service:integrationTest --tests '*StoreSchemaProposalPostgresTest' --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :product-service:integrationTest --tests '*CatalogSchemaProposalPostgresTest' --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :product-service:build :seller-service:build --rerun-tasks --no-daemon --continue --console=plain
```

Focused runs: Store26PASS/exit0/29s, Product24PASS/exit0/20s. **Final full two-module BUILD SUCCESSFUL, exit0,45s,18 tasks executed**, source3ecf9c3. Counters read from final JUnit XML, with no test disabled/deleted and no failure in these three runs:

| Final suite | PASS | FAIL/errors | SKIPPED |
|---|---:|---:|---:|
| Product unit/security/fixture | 215 | 0 | 0 |
| Product PostgreSQL: Category production14 + Product SQL candidate24 | 38 | 0 | 0 |
| Seller/Store unit | 103 | 0 | 0 |
| Seller PostgreSQL: Seller production24 + Store SQL candidate26 | 50 | 0 | 0 |
| **Total, this full run only** | **406** | **0** | **0** |

**88 actual PostgreSQL invocations =38 production Category/Seller +50 SQL-candidate invocations**. Net39 added cases (26 Store +13 Product), not50 new cases. Reused two-Store fixture and Catalog50 PASS in this same run. Do not add focused reruns, historical367, other-owner runs or old full-backend totals. No new all-backend/Inventory/Auth run: **NOT_RUN**, out of this change's two-module scope. Positive JWT/Store/Product/Catalog HTTP, durable seed and live Checkout E2E: **NOT_RUN/BLOCKED**. No new production endpoint is callable; existing unmatched-route security remains deny-all. Testcontainers cleaned its own disposable test runtime; no manual container/volume deletion/prune/reset was issued or existing PostgreSQL data targeted. Post-run volume inventory was inspected; no before/after resource-count assertion is claimed for this run.

Ignored evidence directory: `backend/seller-service/build/tv2-independent-20261018/`. Original raw bytes retained; do not publish generated test credentials from XML/logs.

| Raw log | SHA256 |
|---|---|
| store-prototype.log | `83CD73BE8B7CDA044F3DCFFC9DE7AF758C29B7E6CCB38FAD1A0ACD4A6C91E865` |
| product-prototype.log | `0A0C5AB66AE81230E35BA95D553DA68131CE10ECE126B74B6BB9067E2A0B5CBE` |
| full-build.log | `9CAE2F08FC2200D0335F0B51C42A18AF2604818285DECF3A5143771E0316D45D` |

### Current gates and next checkpoint

- **S01 PENDING — TV1/TV2**: Store service/database and authoritative current ownership/status. This is the highest-priority unlock for actual Store persistence.
- **C01/E01 REVISE overall — TV1/TV2/TV3**: TV3 comment6248646211 accepts consumer-design v0.2 only; TV2 provider-design acceptance is likewise scoped. Wire/UUID/decimal/error/privacy/provider tests are not approved runtime APIs.
- **M01 PENDING — TV5 + named human business decision maker**: decision packet TV5-M01-DECISION-v1/MD01–MD10, currency/scale/rounding/bounds/House and explicit MD07 last-verified read→commit race acceptance. TV5 comment6247437614 is not a business signature; no default currency or Finance flag enabled.
- **Security PENDING — TV1**: current-role source, scoped Order→Catalog service pair, delegated actor, privacy/masking and allowlist. No unverified public mutation/API opened.
- **Inventory/Checkout integration — TV3/TV4**: approved immutable Product→SKU mapping, balances, reserve/retry/durable journal and exact-ID RELEASE/UNKNOWN recovery. Phase-B revalidation is conditional on those prerequisites, not implemented by these SQL tests.

PR37 remains the existing OPEN/non-draft delivery; reviewers already requested are preserved, no self-review or duplicate request. Progress comments: [Store26](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248726487), [Product24](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6248733814). Tick only separate candidate-schema/API-spec preparation items on Issue2 after push; retain historical content and unchecked production composites, keep Issue OPEN/PARTIAL. No Approve/Merge, direct main push, force/reset, common contract or other-owner module edit.

**Independent preparation in this request is complete; production continuation is BLOCKED by the above decisions.** No useful remaining independent domain defect was demonstrated; do not add more prototype frameworks or repeated checklists just to generate commits. Resume by checking `git status`, `git branch -vv`, latest PR37/28 and Issue2 decisions, fetch without rewriting history, and compare remote head. First implement reviewed Store runtime persistence when S01 is explicitly approved; re-run the corresponding PostgreSQL/authorization tests on runtime code rather than treating this prototype as its acceptance.

## Store production persistence checkpoint — 2026-10-19

**Store DB/local source DONE at module scope; WAITING_REVIEW.** [PR #52](https://github.com/duyxl56785/Project_HK5/pull/52), branch `feat/tv2-store-persistence`, base `a0e99b981a752026f8f7d29e3c6b7c43b6e5ac45`, tested code `727402a9083d60efcbdacc79ef2af90c09db012e`. This supersedes earlier Store-persistence BLOCKED notes: latest user authorization and merged [PR #48](https://github.com/duyxl56785/Project_HK5/pull/48), version **TV1-S01-OD09-20261018-v1**, permit Store production JPA/Flyway/local read immediately. PR #29/#37/#28/#48 merged were verified before implementation. Shared HTTP/security/business gates are not implicitly approved by those merges.

### Implemented files and behavior

| File, relative to seller-service | Result |
|---|---|
| `src/main/resources/db/migration/V2__create_stores.sql` | Production Store table, local restrictive Seller FK, unique Seller/canonical slug, OPEN/CLOSED, version and finite timestamptz constraints |
| `store/StoreEntity.java`, `StoreStatus.java` under src/main/java/com/projecthk5/sellerservice | Separate JPA model, no owner copy/Seller cascade, @Version, timestamps and mapping to existing immutable domain |
| `store/StoreRepository.java` | ID/Seller/slug queries and single-statement scalar Store→Seller current-facts projections |
| `store/StoreCurrentSource.java` | Internal DB-backed IDs/statuses/versions/eligibility; no House, ownerReference or recipient/contact fields; no HTTP |
| `store/StorePersistenceService.java`, `StorePersistenceException.java` | REQUIRED create/revise/open/close/guarded own-read, locked current eligibility, expectedVersion/optimistic protection and named conflict errors |
| `src/test/java/.../store/StoreEntityTest.java` | Four new entity/domain mapping/ownership tests |
| `src/test/java/.../store/StorePersistenceIntegrationTest.java` | 63 integration invocations (57 DB/business,6absent HTTP-route negatives) against production V2/JPA in real PostgreSQL context |
| Existing `StoreSchemaProposalPostgresTest.java` | Exact expected runtime history now V1,V2; candidate still not a runtime migration. No old test removed |
| Seller README and this checkpoint | Actual commands, results, scope, recovery and remaining gates |

Commits pushed: `8f602827da9d342caf507097f45e85caba3b723c` foundation/schema/local facts; `727402a9083d60efcbdacc79ef2af90c09db012e` lifecycle/ownership/concurrency tests. Documentation-only checkpoint follows these two code commits; determine its exact HEAD with git log. No existing Seller production class, Store domain/lifecycle/policy, V1, Product/Category source or shared build was changed.

### Transactions / current-state finding

Store writer requires explicit bean/proxy registration with a verified StoreAccessPolicy; no writer/controller/default authorization bean is component-scanned. After policy verification it resolves server-owned Seller by exact opaque account reference, then **refresh(PESSIMISTIC_READ)** before reading eligibility. Shared Seller lock is held to Store commit, blocking concurrent suspension; Store @Version remains the concurrent-write guard. Store entity is refreshed before comparing expectedVersion. Everything joins REQUIRED, including outer rollback; no REQUIRES_NEW or Category advisory lock.

First Store-focused run: **62PASS/1FAIL**, exit1, on the initial uncommitted orchestration. A locking owner query attempted Hibernate's old-version check on an already-managed Seller before refresh; the stale-cache regression expected current SUSPENDED denial and caught OptimisticLockException instead. Changed identity lookup to an ordinary query, then acquired lock and reloaded current facts together through refresh **before** authorization. Assertions unchanged; rerun **63PASS/0FAIL/0SKIP**, followed by full regression below. RED logs/XML retained with original bytes; no fake RED, disabled case or assertion weakening.

Local source uses one SQL projection over Store/Seller to avoid first-level entity-cache staleness and two-query torn facts. Eligibility is OPEN && APPROVED, not FK existence. Retained REPEATABLE_READ/SERIALIZABLE caller snapshots fail closed; the source is a READ_COMMITTED observation, not a commit-time guarantee or agreed Catalog freshness lease.

### New verification on 727402a

Windows Java21.0.12/Gradle8.10, Docker29.8.2 Linux/PostgreSQL16.6 Testcontainers, UTC. Process JAVA_HOME=`C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot`, JAVA_TOOL_OPTIONS=`-Duser.timezone=UTC`. Registered PG task is **integrationTest**; build/check already includes it, so no shared/module build configuration changed.

```powershell
# from backend
./gradlew.bat :seller-service:build :product-service:build --rerun-tasks --no-daemon --continue --console=plain
```

**BUILD SUCCESSFUL,exit0,48s,18tasks.** JUnit XML counters for this full run only:

| Suite | PASS | FAIL/errors | SKIPPED |
|---|---:|---:|---:|
| Seller/Store unit |107|0|0|
| Seller tagged integration: Seller24 + Store63 + SQL prototype26 |113|0|0|
| Product unit/security/fixture |215|0|0|
| Product tagged integration: Category14 + SQL prototype24 |38|0|0|
| **Total** |**473**|**0**|**0**|

322unit +151tagged integration =473. Integration scope separates **95production DB/business cases** (Seller24,Store57,Category14), **6closed-route MockMvc negatives** in the Store PG context, and **50test-only SQL prototypes** (Store26/Product24). Fixture-policy ownership tests use actual DB/JPA/transactions but are not JWT proof. Do not add foundation/focused reruns, historical406 or Cart/Order117/118 to this total. Other backend modules were not rebuilt in this delivery.

Production Store tests cover fresh migration/JPA validation, V1→V2 upgrade with existing Seller owner/status/reason/version/checksum preserved, durable commit/read/update/open/close/timestamps, REQUIRED rollback, SQL invalid values/FK/restrict/uniqueness, exact owner and cross-Seller denial, stale policy/current status and stale managed entities, current projections and missing facts. Six actual concurrent scenarios ran: same-Seller registration, same-slug across Sellers, competing service expectedVersion updates, direct JPA optimistic update race, suspension-before-Store lock and Store-before-suspension lock. Unique/suspension races observe PostgreSQL lock wait; barriers coordinate competing JPA reads. No repository mocks substitute for DB race proof.

Ignored raw evidence directory `backend/seller-service/build/store-persistence-20261019/`; per-class counters/hash metadata in junit-summary.json. Generated fixture credentials in raw output are not published. V1 checksum/source unchanged; source/secret-pattern/diff checks PASS, existing Seller24PG/Category14PG regression PASS.

| Evidence | SHA256 |
|---|---|
| foundation-unit.log | `AA9559343240B32F2C6E952E8B3FBDF69B590488F44DC2E9AF985579E77F3305` |
| foundation-pg.log | `1B00795560EBA9E25BE9860AE9F2F2AE4C3317470D77ACEAB50DBE580F2CE38D` |
| store-pg-initial.log (RED) | `C0269C29C3ACA8FCBA67F03AA37D52B29D8D397E949E4ADB987EBF2E0578D951` |
| store-pg-initial.xml (RED) | `D297BF6BCAB2429622AA1605A1DCDDEE24EDA7C2CA374F3C7CFF6F2493926CD0` |
| store-pg-green.log | `A4DC90E8E5CD0C50AC1D93B7CD8C14CA2B11EF4432D9F9C4D71083682FEFFBA4` |
| full-regression.log | `CB12A0448A8F067B402D982B3736B5502F0E2892C32E1863C09DD2D1278F4F77` |

### Readiness / next action

- Store domain **DONE**, production V2/JPA/repository/lifecycle persistence **DONE at module scope**, local Store/Seller DB facts **DONE**; [PR52](https://github.com/duyxl56785/Project_HK5/pull/52) **WAITING_REVIEW**, TV1 `lexuanbao258-collab` requested.
- Protected Store HTTP/positive JWT/current ADMIN and actual verified StoreAccessPolicy, Seller→Auth role sync: **BLOCKED/NOT_RUN**, TV1/TV2 integration. No Store endpoint is callable yet;404 tests prove absence only, not positive authorization.
- Product runtime persistence/money/House: **PENDING applicable M01/schema approvals**, TV5/named human +TV2. No Product table/FK/seed was added by Store V2.
- Catalog production C01/E01 DTO/errors/pair/actor/privacy/freshness +live Inventory/Checkout: **PENDING/NOT_RUN**, TV1/TV2/TV3/TV4/TV5. Current local Store facts do not authorize a remote caller. Finance/adapters unchanged.
- Two-Store fixture remains TEST_ONLY_NOT_PERSISTED; durable seed, actual Inventory balances and live two-Store E2E **NOT_RUN**.

Migration is additive but its restrictive FK deliberately changes Seller-delete eligibility. App rollback can leave V2 applied; removing Store data/table needs a separately reviewed migration plan. No V1 edits, destructive rollback/clean/prune, direct-main/force push, self-approval/merge or Issue close performed. Issue2 stays OPEN/PARTIAL: add only granular DB/local-source completion evidence, leave composite Store API/ownership/E2E acceptance unchecked.

Resume: preserve git status; fetch origin; read PR52 review at exact head and respond to TV1 findings. Highest priority is **TV1 review/acceptance of Store persistence**, then implement the specific verified-policy/HTTP or Catalog integration slice once its own contract is ready. Do not rewrite Seller/Category or infer House/current ADMIN/JWT/service-pair authority from fixture tests.
