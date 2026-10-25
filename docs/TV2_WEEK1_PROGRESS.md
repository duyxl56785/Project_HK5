# TV2 — Week 1 implementation and handoff

## Strict JSON and private response preparation — 2026-10-22

**PARTIAL — WAITING_TV1_RUNTIME.** Continues PR66 on `feat/tv2-seller-store-http-v02`, base deliveryce2e684;
code/test commit `fcaca6d34f234a505a5ade46ce8e53128ff20bde`. PR66 is returned to Draft per the new task;
no Approve/Merge or new PR. Existing input/domain/persistence are reused; no schema/build/other-module changes.

TV1's response relayed by the user confirms11paths/201-create/200-other, required registration `{}`,
canonical decimal-string version and strict JSON/private representations. No newer public TV1 review or
comment was present on PR66 when checked; this is not a forged GitHub approval. Exact joint HTTP wire/error
review and production JWT/current authority/Gateway/role-sync remain pending. Earlier checkpoints below
retain their historical scope/test outcomes, including Docker failures and successful recovery.

- [x] Immutable SellerPrivate/StoreOwner response DTOs and explicit mapping from trusted service results,
  no ownerReference/role grants/JWT/JPA internals. reviewReason only exists on private Seller representation.
  Mapper does not authorize callers; existing guarded own-read/moderation methods must precede mapping.
- [x] Isolated SellerStoreJson parser for registration and existing5input shapes: exact fields/types,
  duplicate detection including escaped names, missing/null/unknown/type/coercion/overflow rejection,
  object-only root and no trailing JSON. No Spring ObjectMapper/HTTP handler change.
- [x] CanonicalVersion parse/format, including0/Long.MAX_VALUE and JS-unsafe integers; typed response writers
  emit version strings and UTC timestamp text without exposing Entity serialization.
- [x] New90unit contract/version/response cases: RED77assertionFAIL/0error/skip then GREEN. Full Seller build
  **353PASS/0FAIL/errors/0SKIPPED**, exit0/55s:236unit (90new included) +117tagged integration.
  Integration: Seller26 +Store65 +SQL prototype26 =85production DB/business +6closed-route checks +26SQL prototype.
  Two new PostgreSQL cases check private mapping after owner reads, cross-Seller moderation denial and
  unavailable/missing fixture authority; not real JWT/current ADMIN or positive HTTP authorization.
- [x] Independent read-only reviewer found no actionable code findings. Scope/diff/secret-marker checks;
  no production REST/security bean/role-sync wiring, no schema or shared config changes.
- [ ] Joint HTTP handler/error review and production issuer/verifier/current-account/current ADMIN/SELLER
  adapters, Gateway/public/private method allowlist, role-sync command/ack/retry/reconciliation and live E2E.
  No REST/JWT/role-sync/E2E aggregate checkbox or entire Issue2/9 DONE from these results; flags OFF.

Java21.0.12 / Gradle8.10 / Docker29.9.1; JVM UTC; existing integration task Docker API1.44;
real PostgreSQL16.6 Testcontainers with production Flyway/JPA validation. Current commands, separate runs:

```powershell
backend/gradlew.bat -p backend :seller-service:test --tests '*http.*Test' --rerun-tasks --no-daemon --console=plain
backend/gradlew.bat -p backend :seller-service:build --rerun-tasks --no-daemon --console=plain
backend/gradlew.bat -p backend :product-service:build --rerun-tasks --no-daemon --console=plain
```

First command is preserved RED90/77FAIL before implementation. Seller full55s/exit0 is the current353PASS.
Separate Product55s/exit0:309PASS/0FAIL/errors/0SKIP (271unit/API/security +38integration =14Category +24SQL prototype).
No combined662release count, no earlier261/CI/historical counts reused as a fresh run.
Original raw log/XML and per-suite counts stay ignored under `backend/seller-service/build/http-json-20261022/`;
Seller resource manifests0prior containers/volumes missing. Hashes below identify local originals, not public artifacts.

| Raw log | SHA256 |
|---|---|
| contract-red.log | BF53E2E9FE748EDF847A40D536893C47E636C49B818F0D3CB48C9F9E6908F4F6 |
| full-build.log | BC583DEC98C026B9120B5C99635DB13CDB4C00FB4048B45EC597B8366BC4994A |
| product-regression.log | 91E1093E5F72D05D0568580D77CE6A493CEE5D553D16D3B51C6D3BB490E34794 |

Positive JWT/current authority, HTTP status/body binding through controllers, Gateway and live cross-service
E2E **NOT_RUN**; parser/fixture-policy tests do not substitute for them. Next: TV1 final review of this prep
and delivery of actual security adapters, then a separately verified controller integration. Publication
SHA/PR/checklist/CI receipts are recorded on GitHub after push, not inferred from this checkpoint.

## Seller/Store v0.2 preparation — 2026-10-21

**PARTIAL — WAITING_HTTP_AGREEMENT.** Current work starts from main
`4f12b05d0d6408deb42cb329676e643351106fcd`, branch `feat/tv2-seller-store-http-v02`.
The dated checkpoints below remain historical. PR61 is merged; no exact joint HTTP wire approval or
Auth issuer/verifier/current-authority runtime was found in latest source, Issue1/2/9 or PR61 replies.
The user's new TV1 handoff accepts v0.1 as a basis; HS256/verified account sub/current ADMIN/DB status/
Gateway/after-commit durable role synchronization stay existing requirements, not new runtime claims.
Code/test commit: `2e79c66f27fcda31e06369154212b55fb88908d7`; Java/test/resources are identical to the
tested worktree. Documentation-only follow-up does not change that test source. Independent read-only
whole-patch review found no actionable code findings; it recommends Draft PR while PostgreSQL is blocked.

- [x] [v0.2 candidate](../backend/seller-service/README.md#sellerstore-http-v02-preparation--2026-10-21)
  consolidates eleven endpoint/body/success/response/state/error/privacy/transaction proposals and four
  exact TV1 decisions. Decimal-string versions, JSON strictness, private reason visibility and role-sync
  intent/ack remain pending. No shared contract or signature is invented.
- [x] Five internal typed input DTOs: Seller Version/Moderation, Store Create/Update/SetOpen, consumed by
  additive persistence-service overloads. Registration keeps existing verified-applicant-only method.
  StoreDetails shares unchanged name/slug validation with StoreProfile; no schema/business invariant change.
  Null/version/reason/Unicode boundaries checked; identity/status/grants cannot be supplied through these inputs.
- [x] Internal mapping delegates the existing policy/REQUIRED/ownership/version operations. No controller,
  production writer/policy bean, response serializer, public handler, JWT adapter or new dependency.
  Domain-compatible inputs are not an approved JSON wire or HTTP forbidden-field rejection.
- [x] Fresh baseline107unit PASS; new input RED31cases/30assertion FAIL then GREEN full138unit PASS;
  mapping/null regression RED19cases/2assertion FAIL then full146unit PASS. These are separate invocations,
  not counts to sum. No assertions disabled or tests removed to obtain GREEN.
- [ ] Local full Seller build: **FAIL/exit1** because integrationTest has three Testcontainers class-init
  failures. Compile/assemble and146unit PASS in that invocation; PostgreSQL business cases **BLOCKED/NOT_RUN**,
  not113/115PG PASS. Docker Linux Engine pipe unavailable; normal Desktop startup fails on Secrets Engine
  engine.sock rename/access. No socket/config/volume/database deletion or test exclusions.
- [ ] Two added PostgreSQL cases compile but await execution: typed Seller lifecycle/ID/version/permission/
  REQUIRED rollback and typed Store metadata/open/ID/current-owner/stale-version/rollback. Existing migrations,
  uniqueness, optimistic-lock/concurrency suites remain unchanged and require actual PostgreSQL regression.
- [ ] HTTP request/response binding, strict unknown/duplicate-field handling, response privacy mapper and
  error handler await exact joint wire approval. Real JWT/current ADMIN/current SELLER/Gateway/role-sync
  positive integration and live E2E remain NOT_RUN. Product/M01/Finance/Inventory unchanged, flags OFF.

Java21.0.12, Gradle8.10, JVM UTC; existing module task pins Docker client API1.44. Full attempt:

```powershell
backend/gradlew.bat -p backend :seller-service:build --rerun-tasks --no-daemon --continue --console=plain
```

Full attempt21s/exit1; unit146PASS/0FAIL/error/skip, integration3initializationFAIL/0skip. Raw log/XML and
per-class counters preserved under ignored `backend/seller-service/build/http-v02-20261021/`.
`full-build-initial.log` SHA256 `BA86421ADD8F59C741E27194EDB097EFCB200F379002FC8049D051135DC6ABDF`.
Input RED `2160E2D899A538023F28A7F076F8F92EA08E1861A75AF1FA97F3989FD23BC632`;
input GREEN `123FEF318D8A468E007A2295B320E081EC793B9204BC3AC4114F9D9957722B88`;
mapping RED `4186DA9842A91B7D7E42A98A9653C2B6286EAD0411379E4B0A5095E42E3CB588`.
Private raw logs/XML are not public CI artifacts. Future CI/owner/local DB retries must be recorded as
separate evidence at their exact SHA; do not relabel this failed build as PASS.

Product/Category regression and full-backend build **NOT_RUN** here: no Product/shared-build dependency
or implementation changed. Commit/PR publication receipts and granular Issue2/9 updates belong on GitHub
after push verification, not inferred from this pre-publication checkpoint. Issues remain OPEN/PARTIAL;
REST/JWT/role-sync/E2E aggregate boxes remain unchecked. Next highest-value coding task is the TV1-approved
verified HTTP adapter using these inputs after concrete verifier/current-authority and wire handoff.

## Current W1 boundaries — 2026-10-20

**W1 BLOCKED** on shared wire/current-authority/business decisions, not on another Store DB architecture vote.
Audit/test source: main `03fb1b2e8a1c1261568446c1f9ed68d264784e4e`; delivery branch
`feat/tv2-w1-seller-store-catalog-handoff`. The dated checkpoints below remain historical.
Issue [#2](https://github.com/duyxl56785/Project_HK5/issues/2) is W1; [#9](https://github.com/duyxl56785/Project_HK5/issues/9)
is D8–D14 full Seller/Store/Product/UI acceptance. A module DB PASS does not complete either issue.

| Task | Existing code / tests / merge | Can proceed now / blocker / owner |
|---|---|---|
| Category | Production CRUD/hierarchy/advisory lock;14PG regression PASS; PR7 merged | Preserve; slug/status contract, JWT writes and production Product FK still pending TV1/TV2 |
| Seller DB | JPA/Flyway V1/REQUIRED/@Version/unique owner;24PG PASS; PR29 merged | Preserve; real policy/REST/role sync require TV1+TV2 |
| Store DB | Production V2/local Seller FK/unique Seller+slug/current facts;63integration PASS; PR52 merged | S01 DB boundary implemented; HTTP/current actor/House remain separate gates |
| Image Validation |56unit PASS; PR55 merged | Preserve; upload/storage/JWT/DB association are W2 integration, not completed by validator |
| Seller/Store REST/DTO/errors | No controller, agreed wire DTO, verified policy or production writer bean | Concrete proposal below; code waits specific TV1/TV2 wire/privacy/current-role agreement |
| Product/SKU/Catalog | Domain and50Catalog unit tests; no Product production JPA/UNIQUE SKU/provider | TV2 implementation after applicable M01/schema/security; no fixture-as-authority |
| Auth/User/Gateway | Auth V2 persistence PR51 merged; no production issuer/current-role adapter or Gateway routes | TV1 delivery; persisted Auth accounts alone cannot authenticate TV2 HTTP |
| Seed/UI/live E2E | JSON fixture only, not durable data; UI boundary placeholder | W1 handshake/stub contract still missing; complete Product/UI/two-Store workflow belongs to W2 |

### New deliverable and exact remaining decisions

- [x] Audit main, Issue2/9, merged PR7/29/37/48/51/52/53/55, open PR39/60 and latest owner replies; no completed domain/DB/validator reimplementation.
- [x] [Seller/Store HTTP candidate](../backend/seller-service/README.md#sellerstore-http-handoff-candidate--2026-10-20): explicit proposed paths/request/response/validation/error mapping tied to existing services; **not approved or implemented DTO/controller**. This gives TV1 a concrete decision without opening unverified writes.
- [x] [Current Catalog source inventory](TV2_CATALOG_HANDOFF.md#current-provider-inventory--2026-10-20): production local Store facts separated from Product candidate SQL, unimplemented HTTP and consumer design acceptance. SKU consumer confirmation is already recorded, not requested again.
- [ ] **TV1+TV2 — HTTP/current authority:** approve/revise `TV2-SELLER-STORE-HTTP-v0.1`, actual issuer/verifier/current ADMIN/account provider, Gateway exposure/privacy, and versioned durable Seller→Auth grant/revoke wire. [Auth §8](TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md#8-gateway-routing-cors-và-internal-boundary) leaves business paths open; [S01/OD09](https://github.com/duyxl56785/Project_HK5/pull/48#issuecomment-6251503735) allows isolated implementation, not positive traffic. No fixture/static ADMIN adapter is acceptable.
- [ ] **TV1/project authority+TV5 — Product price:** identify the authorized business signer/operator and sign/revise same-version `M01-PRICE-SCOPE-v1-DRAFT` MD01/02/unit-capMD03 plus applicable schema. [Existing decision request](https://github.com/duyxl56785/Project_HK5/issues/5#issuecomment-6254401376) remains active; PR53 merge was documents-only. No new approval or repeated generic request.
- [ ] **TV1+TV2+TV3 — Catalog wire/security/freshness:** C01/E01 v0.2 provider/consumer technical acceptance exists; approved HTTP/privacy/current facts/House and read→commit policy still missing. TV3 owns adapting CatalogPort; missing/stale source aborts the whole batch before reserve.
- [ ] **TV3+TV4 — Inventory/live handoff:** exact SKU-only mapping accepted; journal/fencing exists after PR54/58, but positive transport, approved provenance/lifecycle/RELEASE linkage and real two-Store seed/E2E remain absent. No blind RELEASE, inferred stock or flag enablement.

### Fresh verification on the audited production source

Windows/JDK21.0.12/Gradle8.10/Docker29.8.2/PostgreSQL16 Testcontainers, JVM UTC/API1.44.
`backend/gradlew.bat -p backend :seller-service:build :product-service:build --rerun-tasks --no-daemon --continue --console=plain`
**BUILD SUCCESSFUL, exit0/1m2s/18tasks;529PASS/0FAIL/errors/0SKIPPED**:

| Task | PASS | FAIL/errors | SKIPPED |
|---|---:|---:|---:|
| Seller unit |107|0|0|
| Seller integration |113|0|0|
| Product unit/API/security |271|0|0|
| Product integration |38|0|0|

The151 integration invocations comprise95production DB/business (Seller24+Store57+Category14),6closed-route
negatives in Store PG context and50test-only SQL candidates (Store26+Product24). Catalog50/Image56 are included
in271, not added again. Mock/route-absence checks are not production authentication. No positive Seller/Store/Catalog
HTTP or Gateway server was started; those, signed-JWT/current ADMIN/role-sync/consumer contract/live E2E/full-backend
are **NOT_RUN/BLOCKED**, not inferred from this two-module regression. Existing Category GETs have module API coverage;
Category writes remain403; proposed Catalog route403 and absent Seller/Store routes404 retain their existing behavior.

Original log/XML/derived counters are ignored at `backend/seller-service/build/w1-handoff-20261020/`;
build log SHA256 `2717085806EAE1C4840FB331CEFC944DE4A71115E25D7D483960A589EDE629C2`.
This delivery changes three existing TV2 documentation files only; no runtime/schema/config/dependency/contract approval.
Highest-value next task: **TV1+TV2 finalize Seller/Store HTTP and current-authority adapter handoff**, then implement
that independently approved slice. Do not create another prototype or repeat completed tests to substitute for this gate.

---

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

## Product image validation — 2026-10-20

This checkpoint supersedes the historical Images NOT_STARTED entry for **independent validation only**. Audited `origin/main` at `8cc8254`: PR37/52/53 are merged; M01 documentation merge remains distinct from business approval. User explicitly authorized JPEG/PNG/WebP validation without upload/storage/API or Product persistence. Branch: `feat/tv2-product-image-validation`, from that main, with a clean starting worktree.

- [x] Internal stateless validator with typed errors and immutable validated bytes: JPEG/PNG/WebP byte detection + declaration consistency, real pixel decode, PNG CRC/zlib integrity, strict RIFF framing, empty/invalid/truncated/spoofed content rejection. No filename is opened as a path, no HTTP error contract is introduced.
- [x] Two MiB inclusive per image (2,097,152 bytes, explicit task convention); bounded stream read of limit+1; at most five including a trusted backend stored count; empty collection allowed. Future persistence must enforce that count under concurrency, not accept it from client input.
- [x] Decode resource guards (8192 per dimension,16M pixels, bounded PNG inflation); animations rejected rather than partially validated. These limits/still-image scope require TV1 review before an upload contract. Single module-local BSD-licensed TwelveMonkeys WebP3.15.3 runtime plugin; JDK JPEG/PNG reused, no shared build/framework change.
- [x] **56 image unit cases PASS**, including actual lossy/lossless/alpha WebP, inclusive byte boundaries, endless stream, metadata bounds and decode corruption. Initial missing-zlib-trailer regression RED1FAIL fixed without weakening/removing its assertion. Final full Product build at source/resources `abf6a12`: **309PASS/0FAIL/errors/0SKIP**, exit0/52s/9tasks (271unit/API/security,38PG=14Category+24SQL-proposal). No historical runs added to totals.
- [ ] Product upload HTTP, verified JWT/current ownership, authoritative stored-count locking, persistent image association/storage/re-encoding/safe serving and positive upload/E2E tests. NOT_IMPLEMENTED/NOT_RUN in this slice; future integration TV1+TV2, Product/Review compatibility TV5 and Catalog consumers TV3/TV4 as applicable.

Logic `1654f87`, tests/resources `abf6a12`; usage, security boundaries, exact commands and raw-log hashes: [Product image validation](../backend/product-service/IMAGE_VALIDATION.md). No `.env`, Docker config, existing database/volume/container or migration change. Existing Docker manifests equal before/after full tests. Issue2 stays OPEN/PARTIAL; the composite Product CRUD/SKU/images acceptance remains unchecked. Next safe TV2 task is an approved Product persistence/verified ownership integration slice; this validator does not resolve M01, C01/E01 or security-pair gates.
