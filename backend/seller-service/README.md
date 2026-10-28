# seller-service

**Owner:** TV2.

Current preparation: [private responses and isolated strict JSON — 22/10/2026](#private-responses-and-strict-json-preparation--2026-10-22).
Earlier dated HTTP limitations below are historical where superseded by this checkpoint; no HTTP route is enabled.

Seller/Store domain, production persistence and local current-facts reads are present. HTTP API, verified Auth integration and role synchronization remain separate integration work. Read ../../docs/OWNERSHIP.md before edits. Refs #2; this work does not complete the full Issue. The TV1 persistence confirmation supplied by the project owner is recorded in [SELLER_PERSISTENCE_CONTRACT.md](SELLER_PERSISTENCE_CONTRACT.md).

## Actual package structure

```text
com.projecthk5.sellerservice
  SellerServiceApplication
  seller.domain
    SellerApplication
    SellerIdentity
    SellerStatus
    SellerRuleException
  seller.service
    SellerLifecycleService
    SellerAccessPolicy
    SellerPersistenceService
    SellerPersistenceException
  seller.entity
    SellerProfile
  seller.repository
    SellerProfileRepository
```

Layered Architecture + Package-by-Feature: seller.entity now contains SellerProfile, seller.repository contains SellerProfileRepository, and seller.service adds REQUIRED SellerPersistenceService orchestration. The domain stays immutable Java data, not a JPA entity or HTTP DTO. State/ownership rules remain in the existing lifecycle. No Service/ServiceImpl pair, fake repository or in-memory persistence is introduced.

## Domain behavior implemented

Internal model: an opaque owner identity reference, lifecycle state and latest review reason. These names/types are **not** an approved API, JWT mapping or database schema. No contact/tax/document fields, persisted IDs, uniqueness constraints or external endpoints are guessed.

| Operation | Required current state | Result | Actor requirement |
|---|---|---|---|
| register | New domain application | PENDING_REVIEW | Verified authenticated applicant |
| resubmit | REJECTED | PENDING_REVIEW | Authenticated owner |
| approve | PENDING_REVIEW | APPROVED | Separate actor with moderation permission |
| reject | PENDING_REVIEW | REJECTED | Separate actor with moderation permission; nonblank reason |
| suspend | APPROVED | SUSPENDED | Separate actor with moderation permission; nonblank reason |
| reinstate | SUSPENDED | APPROVED | Separate actor with moderation permission |

All other state/action combinations fail with an internal INVALID_STATE rule violation. Resubmission and transitions to APPROVED clear the prior review reason. Identity references are exact opaque values: no lowercase/trim normalization or consumer-side UUID parsing. Review reasons are stripped and must be nonblank for REJECTED/SUSPENDED. Persistence enforces the confirmed owner/reason limits; future profile/request validation needs its own HTTP contract.

For now all self-moderation is denied, including self-approval and self-reinstatement, even for an actor granted moderation permission. This conservative internal separation-of-duties rule needs TV1/TV2 confirmation before exposing an API. Returning a new immutable application leaves the original unchanged on both success and failure.

## Fail-closed integration boundary

SellerLifecycleService requires a SellerAccessPolicy implementation. This interface is a necessary boundary to the unconfirmed Auth integration, not a ServiceImpl abstraction. Policy methods must validate the trusted actor and throw on denied/unverified access; identity/permission must never be accepted from request fields.

**No production policy implementation, Spring service bean, controller or business HTTP endpoint is registered.** Test mocks permit selected cases only to exercise the rules. They do not prove real JWT authorization. Registration takes the verified applicant identity; resubmission checks exact ownership; all moderation paths call the permission policy and deny self-moderation. There is no default allow-all implementation or public write route.

The verified Auth adapter still needs the approved signing profile/issuer/audience, role/permission mapping, endpoint/DTO/error contract, Gateway routes and CORS. JWKS is relevant only if the selected signing profile uses it. Do not publish internal enums as a shared HTTP contract without agreement; the persistence decisions below do not implement JWT verification.

## Confirmed persistence and transactions

TV1 has now confirmed the minimum persistence contract through the project owner. Flyway V1 creates seller_profiles in seller-service's own logical PostgreSQL database: separate UUID ID, exact VARCHAR(128) owner with UNIQUE/collation C and no cross-service FK, four checked states, reason <=500 and JPA @Version. Registration inserts once; REJECTED resubmission keeps ID. Reasons are cleared outside REJECTED/SUSPENDED. No additional profile fields or Store ownership boundary is assumed.

SellerPersistenceService uses REQUIRED and joins the caller transaction; outer rollback undoes writes/version. Commands require expectedVersion, stale requests/DB optimistic lock failures become internal SELLER_VERSION_CONFLICT. UNIQUE owner is the final concurrent registration guard; only its violation maps to SELLER_ALREADY_EXISTS. No automatic retry, REQUIRES_NEW or Category advisory lock is introduced. No transaction annotation is added to pure lifecycle evaluation.

The orchestration is deliberately not component-scanned as a production bean. Future verified Auth integration must explicitly wire it as a Spring bean with SellerAccessPolicy so @Transactional is applied; manually constructing it is not a supported transactional integration. Test-only wiring registers the real transaction proxy with fixture identities/Admin grants, not a production JWT adapter. Authentication/moderation is checked before update lookup, with domain owner/state/self-moderation checks retained. There is no new public Seller HTTP write endpoint or default allow-all production policy.

Set SELLER_DB_URL/USERNAME/PASSWORD for this service's own DB. Flyway is enabled, JPA ddl-auto validates and open-in-view is false. API/error DTO/verified JWT/Gateway/CORS/role synchronization/Store/Product remain separate PRs. Internal result/exception types are not shared HTTP contracts.

## Verification

From backend using Java 21 and Gradle 8.x:

```bash
gradle :seller-service:assemble :seller-service:test
gradle :seller-service:integrationTest
gradle :seller-service:build
gradle :product-service:build :seller-service:build
```

The test task needs no Docker: existing domain/lifecycle regressions plus entity boundaries and persistence policy denial tests. The tagged integrationTest task requires PostgreSQL 16.6 Testcontainers/Docker and is included in check/build, never silently disabled. It covers migration, durable/unique/exact owner, lifecycle, REQUIRED rollback, stale version, SQL constraints, real concurrent unique wait and optimistic locking. Docker API 1.44 is scoped to that task, overridable with -PdockerApiVersion. No H2 substitution is used. Fixture/mock policy is not JWT verification; actual results are in SELLER_TEST_REPORT.md.

The original Seller persistence branch temporarily targeted feat/tv2-seller-product; PR #7/#29 have since merged. The Store follow-up below is a separate PR into main.

See SELLER_TEST_REPORT.md for actual verification and pending work. Shared build/settings and other members' modules remain unchanged.

## Week 1 independent follow-up — 2026-10-18

Guarded read-only queries now support exact own-profile lookup and permission-checked moderation lookup, with PostgreSQL regression. They remain internal methods; no new HTTP route or verified production policy is registered.

At that historical checkpoint, `store` contained only an immutable model, policy boundary and lifecycle proposal. Store persistence was absent; the production follow-up below supersedes that persistence limitation, without claiming JWT/API readiness. See [TV2 week 1 progress](../../docs/TV2_WEEK1_PROGRESS.md) for evidence and decisions.

## Store production persistence — 2026-10-19

[PR #52](https://github.com/duyxl56785/Project_HK5/pull/52), branch `feat/tv2-store-persistence`, implements the Store DB/local-source scope authorized by **TV1-S01-OD09-20261018-v1** in merged PR #48. Store lives in Seller DB; Product may retain only a logical Store UUID. There is no cross-service FK/JOIN, House classification, contact/recipient source or Auth role synchronization in this delivery.

Flyway **V2__create_stores.sql** creates `stores`: UUID PK, restrictive local `seller_id → seller_profiles.id`, UNIQUE Seller and globally unique canonical lowercase ASCII slug, name/status/version/timestamps. Status is persisted as OPEN/CLOSED; the existing immutable domain keeps its boolean representation. Slugs must already be canonical (`[a-z0-9]+(?:-[a-z0-9]+)*`, at most128); uppercase/padded/invalid input is rejected, not silently rewritten. Domain strips name and retains its255 UTF-16-unit limit; PostgreSQL VARCHAR255 separately bounds DB characters. JPA uses explicit enum mapping, `@Version`, microsecond `Instant` timestamps and no Seller cascade or duplicated owner column. V1/checksum/Seller data remain unchanged; `ddl-auto=validate` remains enabled.

`StorePersistenceService` reuses `StoreLifecycleService` for create/revise/open/close and guarded own-read, returns immutable internal `PersistedStore`, and joins **REQUIRED** at READ_COMMITTED. Update commands take expectedVersion; database uniqueness and JPA version are final guards, with distinct internal duplicate-Seller/duplicate-slug/version errors. A verified `StoreAccessPolicy` is checked before resolving the exact account reference against Seller DB. Policy-supplied Seller ID is checked against server ownership; policy status/version never substitutes for current DB facts.

Before authorization, the service refreshes Seller under a shared PostgreSQL row lock; status updates wait until the Store transaction commits. A locking query cannot safely precede refresh for a stale managed Seller, so the initial owner lookup resolves identity only. Store is likewise refreshed before expectedVersion comparison; competing Store writes still use optimistic locking. No Category REQUIRES_NEW/advisory lock or automatic retry is introduced. Caller rollback undoes all Store writes/version/timestamps.

`StoreCurrentSource` is a component for **trusted same-module** reads by Store UUID, Seller UUID or canonical slug. Its repository scalar projection joins Store/Seller in one statement and bypasses stale managed entities. `Facts` contains Store/Seller IDs, both statuses and versions; eligibility is OPEN and APPROVED. It excludes ownerReference, House/contact and permissions. Missing rows return Optional.empty; incomplete facts fail closed; retained non-READ_COMMITTED caller snapshots are rejected. This is an observed current DB snapshot, not a price/authorization lease or an HTTP service-pair contract.

The writer still requires **explicit Spring proxy wiring with a verified policy**; no default policy, production writer bean or Store controller is registered. Do not manually construct it expecting transactions. The fixture policy used in tests is not JWT/current ADMIN authorization. Store routes remain absent, including requests with forged headers. Protected HTTP/pair/Auth integration must arrive separately; local facts alone do not authorize remote callers.

Verified on code `727402a`: full Seller/Product build/check **473 PASS /0 FAIL/errors /0 SKIPPED**, exit0. Seller107unit +113tagged integration (Seller24, Store production class63 incl6absent-route checks, Store SQL prototype26); Product215unit +38integration (Category14, Product SQL prototype24). Store-specific coverage includes fresh V2/JPA validation, upgrade from V1 with preserved Seller data/checksum, durable lifecycle, current ownership, rollback, constraints, actual competing transactions and both suspension/Store lock orders. An initial stale-L1 failure was fixed and rerun with the original regression unchanged; logs/hashes and scope breakdown are in the progress checkpoint.

Run the actual module tasks from backend with Java21/UTC:

```powershell
./gradlew.bat :seller-service:build :product-service:build --rerun-tasks --no-daemon --continue --console=plain
./gradlew.bat :seller-service:integrationTest --tests '*StorePersistenceIntegrationTest' --rerun-tasks --no-daemon --console=plain
```

`seller-service:integrationTest` is the registered PostgreSQL task and is included by check/build; no new `postgresIntegrationTest` task or shared build edit was needed. Testcontainers uses PostgreSQL16.6; no H2 replacement. The SQL prototype remains test-only and distinct from production V2.

V2 is additive. Reverting application code can leave V2/table present, but the FK continues restricting Seller deletion; removing schema/data needs a separate reviewed migration plan. Do not edit V1, run Flyway clean, reset Docker or delete existing data to roll back. Positive JWT/Store HTTP, Seller→Auth role sync, durable two-Store seed and live Checkout E2E remain **NOT_RUN/BLOCKED**, not inferred from the local persistence tests.

## Seller/Store HTTP handoff candidate — 2026-10-20

**PROPOSED, NOT APPROVED OR IMPLEMENTED: `TV2-SELLER-STORE-HTTP-v0.1`.**
PR29/52 persistence is merged. Auth PR51 supplies database persistence, not a token issuer/current-role provider;
Gateway still has no routes. [Auth v1.0 §8](../../docs/TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md#8-gateway-routing-cors-và-internal-boundary)
explicitly leaves business paths/DTOs to owner agreement. The table is a concrete TV2 proposal for TV1 review,
not new callable routes, Java DTOs, a shared-contract change or permission to wire fixture policies.

| Proposed method/path | Request fields | Existing internal operation |
|---|---|---|
| POST `/api/sellers/me` | Empty object; reject client identity/role/status fields | `SellerPersistenceService.register` |
| GET `/api/sellers/me` | None | `findOwn` |
| POST `/api/sellers/me/resubmit` | `expectedVersion` | Resolve own ID server-side, then `resubmit` |
| POST `/api/admin/sellers/{id}/approve` | `expectedVersion` | `approve` |
| POST `/api/admin/sellers/{id}/reject` | `expectedVersion`, `reason` | `reject` |
| POST `/api/admin/sellers/{id}/suspend` | `expectedVersion`, `reason` | `suspend` |
| POST `/api/admin/sellers/{id}/reinstate` | `expectedVersion` | `reinstate` |
| POST `/api/stores/me` | `name`, `slug` | `StorePersistenceService.create` |
| GET `/api/stores/me` | None | `findOwn` |
| PUT `/api/stores/me` | `name`, `slug`, `expectedVersion` | Resolve own ID server-side, then `revise` |
| PUT `/api/stores/me/open` | `open`, `expectedVersion` | Resolve own ID server-side, then `setOpen` |

Candidate request rules: reject unknown/null fields; version nonnegative; rejection/suspension reason nonblank
and at most500 Unicode code points; Store name nonblank and stripped length at most255 UTF-16 units (current
domain rule), slug at most128 matching `[a-z0-9]+(?:-[a-z0-9]+)*`. Do not silently change these existing rules
or choose version wire encoding before review. Candidate Seller response is `id,version,status,reviewReason`;
Store response is `id,sellerId,name,slug,open,version,createdAt,updatedAt`, with no `ownerReference`, Entity
serialization or secrets. Property names, JSON types, success statuses and privacy still need explicit approval.

Candidate error mapping for review: invalid fields400; missing/invalid credentials401 per TV1;
verified denial/self-moderation403; approved-scope missing resource404; unique/version/invalid-state conflict409;
unavailable current authority503. Preserve existing typed domain/persistence codes as mapping inputs; TV1/TV2
must approve final wire codes/envelope and concealment before implementing an exception handler. No current
HTTP status/code is changed here, including Category/Product fallback403.

**Release gates:** TV1 supplies real issuer/verifier and current-account/ADMIN authority; TV1+TV2 approve this
wire/privacy/Gateway scope and a versioned, durable Seller→Auth grant/revoke protocol. Only then explicitly wire
transaction-proxied services with verified policies, bind actor to exact verified `sub`, resolve ownership from DB,
deny self-moderation and stale/unavailable authority, and run signed-JWT/HTTP/DB/cross-Seller tests. Registration
must not require SELLER first; Store management requires current APPROVED/ownership plus the agreed Auth policy.
Approval of this proposal must not enable Product/Catalog/M01 or auto-grant roles. Existing active decision requests
are retained; [the current checkpoint](../../docs/TV2_WEEK1_PROGRESS.md#current-w1-boundaries--2026-10-20)
records what is actually available and the next owner action.

## Seller/Store HTTP v0.2 preparation — 2026-10-21

**`TV2-SELLER-STORE-HTTP-v0.2`: CANDIDATE / NOT JOINTLY APPROVED / NO HTTP ENDPOINT EXPOSED.**
This section supersedes v0.1 as the current proposal, preserving its history above. Base audited:
`4f12b05d0d6408deb42cb329676e643351106fcd`. PR61 is merged, not reopened. Issue1/2/9 and PR61
comments/reviews were read; no newer public TV1 approval of the exact HTTP wire/runtime was found.
The project owner's 21/10/2026 instruction relays TV1's acceptance of v0.1 as a **basis**, not approval
of these new field types, success statuses, privacy representations or role-sync messages.

### Existing decisions and actual code boundary

Auth v1.0 and TV1-S01-OD09-20261018-v1 remain authoritative: HS256 verified signature/iss/aud/exp/nbf/iat,
kid/key rotation/jti/roles; 15-minute access token; runtime secrets; exact verified account `sub` to
`SellerIdentity`, never Seller/Store UUID. Current Auth account/ADMIN authority, local current Seller/Store
facts, cross-Seller isolation, no self-moderation and unavailable-authority fail-closed are requirements,
not runtime delivered by this PR. Gateway allowlist/independent verification and public `/internal/**`
denial remain TV1's implementation. No static ADMIN, identity headers, new verifier or fixture production policy.

| Component | Implemented | Evidence scope | Contract decision | Next safe action / owner |
|---|---|---|---|---|
| Seller domain/DB | Existing lifecycle, V1, unique exact owner, REQUIRED, @Version | Existing module suites; current run in progress checkpoint | Persistence confirmed | Preserve; TV2 |
| Store domain/DB | Existing V2, restrictive local FK, unique Seller/slug, current locked Seller/Store facts | Existing module suites; current run in progress checkpoint | S01 DB boundary confirmed | Preserve; TV2 |
| Internal input DTOs | SellerInputs.Version/Moderation; StoreInputs.Create/Update/SetOpen | Focused constructor/policy tests | Existing domain-compatible inputs; **not HTTP binding** | Use explicitly at service boundary |
| Input validation/mapping | StoreDetails reused by StoreProfile; typed service overloads delegate to existing operations | Unit; added PG mapping/rollback cases require real DB execution | Existing invariants unchanged | Future HTTP adapter maps approved wire to inputs |
| HTTP request/response DTOs | Not bound/serialized by any handler | NOT_RUN | Wire/privacy pending | TV1+TV2 approve exact representation |
| JWT/current-authority policies | Interfaces only; no production writer/policy bean | Fixture-policy unit/DB is not authentication | Architecture decided, implementation absent | TV1 runtime + TV2 integration |
| Live HTTP/Gateway/E2E | No Seller/Store controller or Gateway route | NOT_RUN | Release gates not met | Keep endpoints absent |

The input models are immutable **internal DTOs**, used by additive persistence-service overloads; legacy
methods remain supported. Registration still calls register(verifiedApplicant) with no invented profile
fields/empty Java DTO. Version inputs serve resubmit/approve/reinstate; moderation inputs serve reject/suspend.
No input carries ownerReference, actor, Seller/Store ID, target status, role, House, timestamps or persisted version.
The target ID and verified actor remain separate server-resolved arguments, checked by the existing services.
Versions are preconditions, never assigned to JPA @Version. Store update preserves identity/Seller/open;
setOpen changes only open. Existing proxy-based REQUIRED, policy, locking and conflict behavior is retained.

Long/Boolean input fields reject null instead of becoming zero/false. Versions are nonnegative through
Long.MAX_VALUE. Store name is nonblank after existing strip semantics and <=255 UTF-16 units; slug is exact
ASCII `[a-z0-9]+(?:-[a-z0-9]+)*`, <=128, without normalization. Reasons are stripped, nonblank, <=500 Unicode
code points, matching domain plus persistence. StoreDetails centralizes the unchanged name/slug rules.
These constructors do not authenticate anyone or prove HTTP unknown-field rejection. No Jackson configuration,
request serializer/deserializer, response mapper, public exception handler or validation dependency was added.
Those await approval; binding these records directly as HTTP bodies is not the proposed integration.

### Endpoint, success and state proposal (all rows pending TV1+TV2 agreement)

Response names below are schema proposals only: **SellerPrivate** = `id,version,status,reviewReason`;
**StoreOwner** = `id,sellerId,name,slug,open,version,createdAt,updatedAt`. No public Seller representation proposed.

| Method/path | Body proposal | Success / response | Actor and current state |
|---|---|---|---|
| POST `/api/sellers/me` | `{}` only; mandatory versus absent body still needs agreement | 201 / SellerPrivate; proposed Location `/api/sellers/me` | Verified active applicant; CUSTOMER may apply, SELLER not required; no existing Seller |
| GET `/api/sellers/me` | None | 200 / SellerPrivate | Verified active exact owner; all four Seller states readable |
| POST `/api/sellers/me/resubmit` | expectedVersion | 200 / SellerPrivate | Exact owner, REJECTED→PENDING_REVIEW; same Seller ID |
| POST `/api/admin/sellers/{id}/approve` | expectedVersion | 200 / SellerPrivate | Current ADMIN, not self; PENDING_REVIEW→APPROVED |
| POST `/api/admin/sellers/{id}/reject` | expectedVersion, reason | 200 / SellerPrivate | Current ADMIN, not self; PENDING_REVIEW→REJECTED |
| POST `/api/admin/sellers/{id}/suspend` | expectedVersion, reason | 200 / SellerPrivate | Current ADMIN, not self; APPROVED→SUSPENDED |
| POST `/api/admin/sellers/{id}/reinstate` | expectedVersion | 200 / SellerPrivate | Current ADMIN, not self; SUSPENDED→APPROVED |
| POST `/api/stores/me` | name, slug | 201 / StoreOwner; proposed Location `/api/stores/me` | Verified current SELLER grant + exact current APPROVED Seller; no existing Store; creates OPEN |
| GET `/api/stores/me` | None | 200 / StoreOwner | Same management gate; existing OPEN or CLOSED Store |
| PUT `/api/stores/me` | name, slug, expectedVersion | 200 / StoreOwner | Same management gate; OPEN or CLOSED; preserves open/owner/Seller |
| PUT `/api/stores/me/open` | open, expectedVersion | 200 / StoreOwner | Same management gate; may close/reopen own Store; CLOSED is not a prerequisite failure |

Proposed JSON: UUID domain IDs as canonical strings; expectedVersion/response version as canonical decimal
**strings** (`0|[1-9][0-9]*`, <=9223372036854775807), avoiding JavaScript precision loss. This encoding is
**new candidate, not implemented/approved**; internal boxed Long does not select a wire number/string.
open must be an explicit JSON boolean; strings are strings, not coerced numbers/arrays. Required fields must
be present/non-null; reject unknown fields (including client owner/role/ID/status), duplicate keys and wrong
JSON kinds. No silent version/slug coercion. Datetimes proposed as ISO-8601 UTC, preserving DB microseconds.
Version checks apply to all seven Seller change/Store mutation endpoints, not only moderation.

### Error proposal, visibility and privacy

Proposed envelope `{code,message,errors?}`; optional errors are `{field,message}` without rejected values.
Shared Auth codes/challenges come from [Auth v1.0 §7](../../docs/TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md#7-http-status-và-error-contract--od-04-decided).
The table does not implement an HTTP handler or change Category/Product fallback behavior.

| Scope / condition | Proposed HTTP / code | Visibility / guard before disclosure |
|---|---|---|
| All: invalid JSON/fields/version/reason/path UUID | 400 VALIDATION_FAILED | Authenticated, otherwise use Auth protocol; no body echo or stack trace |
| All: missing/invalid Bearer; malformed authorization | 401 AUTH_ACCESS_REQUIRED / AUTH_ACCESS_INVALID; malformed header400 AUTH_REQUEST_INVALID | Standard Bearer challenges; identity never from body/header aliases |
| Moderation: no current ADMIN/self; owner mismatch; Store Seller not APPROVED/current grant denied | 403 AUTH_FORBIDDEN | No unrelated Seller/Store fields or current owner ID in error |
| Own Seller read; authorized moderation lookup | 404 SELLER_NOT_FOUND | Only authoritative absence after applicable authentication/permission checks |
| Own Store read/update/open with verified eligible Seller | 404 STORE_NOT_FOUND | Verified management scope; missing current Seller facts are not Store absence |
| Seller registration duplicate | 409 SELLER_ALREADY_EXISTS | Applicant's own conflict only |
| Store creation duplicate Seller; create/update duplicate slug | 409 STORE_ALREADY_EXISTS / SLUG_ALREADY_EXISTS | Never reveal holder Seller/Store/owner |
| Version conflict (resubmit/moderate/update/open) | 409 SELLER_VERSION_CONFLICT / STORE_VERSION_CONFLICT | Do not return another owner's state/version |
| Invalid Seller transition | 409 SELLER_INVALID_STATE (new candidate code) | Permission first; lifecycle state remains server-owned |
| Key/current Auth authority unavailable | 503 AUTH_VERIFICATION_UNAVAILABLE / AUTHORIZATION_DEPENDENCY_UNAVAILABLE | Never turn timeout/missing verification into absence or allow |
| Store source cannot verify current Seller/mapping | 503 AUTHORIZATION_DEPENDENCY_UNAVAILABLE | Distinguish from verified unauthorized403 and proven Store absence404 |

No private arbitrary-ID Store read route or concealment behavior is added. Existing Auth baseline defaults
ownership denial to403; any resource-specific concealment404 remains pending and must not be guessed.
SellerPrivate reviewReason (including null clearing) is proposed **only** for the authenticated same owner
or a current authorized moderator on these routes. TV1 must approve this disclosure; it is not public Catalog
data. ownerReference, Auth grants, JWTs, passwords, secrets and JPA fields remain excluded from all proposed
responses. StoreOwner is own-management scope, not a general public Store lookup or a trusted remote ownership API.
No invented contact/tax/recipient/House fields, list endpoints or reason audit history.

Future orchestration resolves `/me` IDs from exact verified sub in a transaction-proxied REQUIRED service,
not client identity, then checks expectedVersion and current DB eligibility under the existing lock protocol.
Auth current-authority verification must follow TV1's concrete procedure; no new freshness TTL or role cache
is assumed, and no network call should be inserted while holding Store/Seller locks. Authorization precedes
domain disclosure/validation in the future HTTP flow; internal input validation alone is not that flow.
Registration uses DB UNIQUE(owner_reference), Store UNIQUE(seller_id/slug), writes @Version; do not retry a
conflict automatically or copy Category REQUIRES_NEW. Rejected/suspended reasons clear on resubmit/approval/
reinstatement. Seller success never claims Auth role synchronization already completed.

### Seller to Auth synchronization handoff (design only)

Keep state changes with TV2 and durable role grants with TV1. Proposed minimal intent: event/command UUID,
schema version, separate Seller UUID, exact server-owned account reference, committed Seller version/sequence,
desired SELLER-grant boolean and correlation; never arbitrary ADMIN/role list or user-supplied owner. APPROVED
desires grant; PENDING_REVIEW/REJECTED/SUSPENDED desire revoke. These field names/ack statuses are not final wire.

Propose a Seller-local transactional outbox: state update and intent insertion commit atomically; worker can
deliver only committed intents. A callback alone loses delivery on crash-after-commit. Receiver must authenticate
the exact Seller→Auth pair and scope, apply idempotently, reject same event ID with a changed payload, and prevent
an older Seller version from restoring a revoked grant. Same-version different intent is a conflict, not last-wins.
Auth persists applied version/receipt atomically with grants; retry with same ID/payload, durable backoff/status,
reconciliation from current Seller facts, and controlled credential rotation must survive sender/receiver restarts.
No endpoint/header/payload serializer/outbox table/worker/grant code is introduced until TV1+TV2 approve the exact
wire, source/provenance, replay horizon, retry/ack semantics and operations. Do not expose OWNER identifiers publicly.

### Exact remaining decisions for TV1

1. Approve/revise the eleven method/path/body/success/response rows, version decimal-string encoding and strict
   JSON rules, including registration empty object versus no body. Existing HS256/sub/security decisions are not re-requested.
2. Approve/revise SellerPrivate reason visibility, StoreOwner fields, error-code mapping/envelope and resource-specific
  403/concealment policy; no unapproved response serializer or exception handler will be installed first.
3. Supply exact issuer/verifier/current-account/current-ADMIN/current-SELLER procedure and implementation SHA/tests,
   failure semantics and Gateway allowlist handoff; define role-sync lag handling without bypassing APPROVED/ownership.
4. Approve/revise the role-sync intent/ack/monotonic version/durable outbox-reconciliation/pair protocol, including
   whether the current role read and grant-write authority are usable independently for the first REST slice.

No Product/M01/Catalog/Inventory/Finance policy is newly approved. Next release work is a separate verified HTTP
adapter only after the relevant wire and actual authority components are available. See the dated
[TV2 progress checkpoint](../../docs/TV2_WEEK1_PROGRESS.md#sellerstore-v02-preparation--2026-10-21) for real test results.

## Private responses and strict JSON preparation — 2026-10-22

**SECURITY CONTRACT CONFIRMED — AUTH RUNTIME PENDING.** The user's 22/10/2026 TV1 handoff confirms the eleven
proposed paths/201-versus200 statuses, mandatory registration `{}`, canonical decimal-string versions,
strict JSON and owner/current-ADMIN privacy requirements. No public TV1 reply on PR66 was found at audit;
this records the relayed decision's source, not an invented GitHub signature. Final joint HTTP wire/error
review and production issuer/verifier/current authority/Gateway/role-sync remain pending.

Code is under `com.projecthk5.sellerservice.http`, with no Spring component/configuration/controller:

| Component | Actual behavior |
|---|---|
| `SellerStoreResponses.SellerPrivate` | Immutable `id,version,status,reviewReason`; no ownerReference/grants/entity internals. Cleared reason remains explicit JSON null. Intended only for already authorized owner/current ADMIN. |
| `SellerStoreResponses.StoreOwner` | Immutable `id,sellerId,name,slug,open,version,createdAt,updatedAt`; no ownerReference or moderation reason. Own-management representation, not public Store/Catalog data. |
| `sellerPrivate(PersistedSeller)` / `storeOwner(PersistedStore)` | Explicit pure mapping after upstream guarded service access. Server IDs/state/version/timestamps preserved; no authority granted by mapper or by DTO name. No JPA Entity serialization. |
| `CanonicalVersion.parse/format` | String canonical ASCII decimal `0` or nonzero-leading digits, range0..Long.MAX_VALUE; no trim/plus/leading zeros/exponent/float/overflow. Response versions are validated String fields, hence serialized as JSON strings, including values above JS safe integer. |
| `SellerStoreJson` | Private isolated ObjectMapper, duplicate detection including escaped field names, exact object schema, explicit scalar kind checks and no trailing JSON. No mutation of Spring/application Jackson configuration. |

Codec request methods map into the **existing** input DTOs, retaining their domain validation:

| Method | Required exact JSON fields |
|---|---|
| `registration(String)` | Empty object only; no new open-ended registration DTO |
| `version(String)` | expectedVersion (string) for resubmit/approve/reinstate |
| `moderation(String)` | expectedVersion (string), reason (string) for reject/suspend |
| `createStore(String)` | name, slug (strings) |
| `updateStore(String)` | name, slug (strings), expectedVersion (string) |
| `setOpen(String)` | open (boolean), expectedVersion (string); false is distinct from missing/null |

All reject missing/unknown/duplicate/null/wrong-type fields, non-object/empty/malformed bodies and multiple
root values. Client identity/role/status/ID fields fail the exact field whitelist. `InvalidInput` is an
internal generic parse/validation exception, without raw Jackson cause/rejected values; no new HTTP error
envelope/status handler is installed. `writeSellerPrivate`/`writeStoreOwner` accept only the respective
response record, use string versions and ISO-8601 UTC Instants, and reject a null response. There is no
public generic Entity/object serializer or production controller binding these codecs.

Future verified adapter order: authenticate/current-authority check, parse exact request schema, guarded
server-owned service operation, map authorized result, then the matching typed writer. Existing Auth
401/403/503 and resource-specific404/409 visibility must be integrated under the jointly reviewed handler;
parser success alone does not authenticate the caller or authorize access. Do not turn this codec into
an allow-all principal factory, accept headers as identity, or expose `reviewReason` through public Catalog.

Actual code `fcaca6d34f234a505a5ade46ce8e53128ff20bde`: new90 unit contract/response/version cases, with
original RED90cases/77assertionFAIL retained; subsequent **Seller full build353PASS** (236unit+117integration).
Two new PG cases exercise mapping after real guarded owner reads, cross-Seller moderation denial and
missing/unavailable fixture authority. This is actual PostgreSQL with fixture policies, not production JWT.
Separate Product build309PASS includes Category14PG regression. No sum across these separate invocations.
Exact commands/counts/raw hashes and remaining NOT_RUN work are in the
[new progress checkpoint](../../docs/TV2_WEEK1_PROGRESS.md#strict-json-and-private-response-preparation--2026-10-22).

## Own-read handler preparation — HTTP not registered

`HTTP_PREPARED / POSITIVE_JWT_INTEGRATION_BLOCKED`.
`SellerStoreReadHandler` composes the existing guarded services and response mappers:

| Future route | Handler | Required trusted context |
|---|---|---|
| GET `/api/sellers/me` | `sellerOwn()` | Verified active account, exact subject; own profile in any Seller state |
| GET `/api/stores/me` | `storeOwn()` | Verified token and current SELLER grant; existing service also checks exact current owner and APPROVED Seller; CLOSED Store remains readable |

The handler takes no client identity/resource arguments. `SellerStoreReadAuthority` is a trusted,
request-context port with separate account and Seller checks, not a verifier or authorization implementation.
There is no production authority implementation, handler bean, business controller or business route.
The module-local deny-default security boundary described below is registered separately.
Invalid/missing trusted identity fails before persistence; domain absence/denial/dependency exceptions are
propagated, never converted to empty/null success. No final HTTP status/error mapping is introduced.
Responses reuse `SellerPrivate`/`StoreOwner`, canonical string versions and the existing JSON writers;
no Entity, ownerReference, token or role-grant fields are exposed.

TV1 integration must first supply the real JWT/key configuration, role-neutral active-account check,
current SELLER check and matching guarded-service policies. Wire the existing services through Spring
transaction proxies, not manual construction. Authority lookup happens before Store DB locks. Final
HTTP/privacy/error mapping, deny-default routing and Gateway protection need joint acceptance before
adding endpoints. Do not use a CUSTOMER requirement as a substitute for role-neutral own-profile access.

`SellerStoreReadHandlerTest` covers isolated authority orchestration and private JSON compatibility.
`SellerStoreReadIntegrationTest` uses real PostgreSQL16.6, production Flyway/JPA and **test-only authority**
to cover exact own reads, current status/role denial, rollback/fresh reads and absence. MockMvc confirms
both future routes remain denied even with spoofed headers; controller absence is established from source,
not inferred from a403 response. These tests do not verify JWT, deployed
Gateway or positive HTTP; `POSITIVE_JWT_HTTP_NOT_RUN / BLOCKED_TV1` remains.

## Management boundary and default-deny security — 28/10/2026

The unregistered `SellerStoreManagementHandler` adds register/resubmit, current Store create/revise/open,
and Seller moderation commands through the existing strict JSON codec, private DTOs and transactional
services. It does not add routes or decide public method/path/error contracts. Owner references and Store
IDs are resolved from the trusted account/current owned resource, never body fields. Moderation targets
are resource arguments only: existing `SellerAccessPolicy` still requires current moderation permission
and the lifecycle forbids self-moderation. An active account alone does not grant ADMIN authority.

`SellerHttpSecurityConfiguration` is a real fallback servlet filter chain: only health GET/HEAD are allowed;
all other requests return403 `SELLER_ROUTE_AUTH_NOT_CONFIGURED`. CSRF stays enabled, session authentication
is stateless, and Basic/form login/logout/request caching are disabled. The module excludes generated local
password-user auto-configuration. This is the same deny-default boundary principle used by Product,
not another JWT verifier or authority implementation. Even an authenticated test ADMIN cannot bypass it.

The previous unregistered-route404 assertions now require403 plus no persistence/private-data leakage.
Real JWT401/403/503 decisions, accepted current-account/SELLER/ADMIN adapters, reviewed Gateway/service-pair
routes and business HTTP controllers remain blocked on TV1. Test-only principals/policies prove local
orchestration and PostgreSQL rules, not signed-token/Gateway/E2E integration. The API inventory and resume
instructions are in [HTTP_API_CHECKPOINT](../../docs/tv2/HTTP_API_CHECKPOINT.md).
