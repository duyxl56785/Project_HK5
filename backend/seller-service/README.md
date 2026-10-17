# seller-service

**Owner:** TV2.

Seller's internal domain/lifecycle and persistence implementation are present. HTTP API, verified Auth integration, events and Store remain separate integration work. Read ../../docs/OWNERSHIP.md before edits. Refs #2; this work does not complete the full Issue. The TV1 persistence confirmation supplied by the project owner is recorded in [SELLER_PERSISTENCE_CONTRACT.md](SELLER_PERSISTENCE_CONTRACT.md).

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

This persistence branch temporarily targets feat/tv2-seller-product while PR #7 is unmerged. Retarget after the dependency merges without rewriting the existing history.

See SELLER_TEST_REPORT.md for actual verification and pending work. Shared build/settings and other members' modules remain unchanged.

## Week 1 independent follow-up — 2026-10-18

Guarded read-only queries now support exact own-profile lookup and permission-checked moderation lookup, with PostgreSQL regression. They remain internal methods; no new HTTP route or verified production policy is registered.

`store` contains an immutable model, policy boundary and lifecycle proposal. Store operations recheck the resolved Seller's identity and APPROVED state, but Store persistence, slug uniqueness, one-Store-per-Seller, JWT/API and the database boundary are not implemented or approved. Unit fixtures do not prove those properties. See [TV2 week 1 progress](../../docs/TV2_WEEK1_PROGRESS.md) for scope, evidence and pending decisions.
