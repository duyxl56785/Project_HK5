# seller-service

**Owner:** TV2.

Seller's internal domain and lifecycle rules are implemented. HTTP API, verified Auth integration, JPA persistence, migrations, events and Store are pending agreed contracts. Read ../../docs/OWNERSHIP.md before edits. Refs #2; this work does not complete the full Issue.

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
```

Layered Architecture + Package-by-Feature: the seller feature owns its domain and service layers. Additional controller/dto/repository/entity/infrastructure packages should be added when those implementations exist. The current domain is immutable Java data, not a JPA entity or an HTTP DTO. State transitions and ownership checks live in the lifecycle service. No Service/ServiceImpl pair, mapper, fake repository or in-memory persistence is introduced.

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

All other state/action combinations fail with an internal INVALID_STATE rule violation. Resubmission and transitions to APPROVED clear the prior review reason. Identity references are exact opaque values: no lowercase/trim normalization or assumed UUID/JWT subject format. Review reasons are stripped and must be nonblank for REJECTED/SUSPENDED. Length limits and profile-field validation belong to the future agreed request/schema contract.

For now all self-moderation is denied, including self-approval and self-reinstatement, even for an actor granted moderation permission. This conservative internal separation-of-duties rule needs TV1/TV2 confirmation before exposing an API. Returning a new immutable application leaves the original unchanged on both success and failure.

## Fail-closed integration boundary

SellerLifecycleService requires a SellerAccessPolicy implementation. This interface is a necessary boundary to the unconfirmed Auth integration, not a ServiceImpl abstraction. Policy methods must validate the trusted actor and throw on denied/unverified access; identity/permission must never be accepted from request fields.

**No production policy implementation, Spring service bean, controller or business HTTP endpoint is registered.** Test mocks permit selected cases only to exercise the rules. They do not prove real JWT authorization. Registration takes the verified applicant identity; resubmission checks exact ownership; all moderation paths call the permission policy and deny self-moderation. There is no default allow-all implementation or public write route.

TV1 must confirm JWT issuer/audience/JWKS, stable identity namespace (including issuer if needed), role/permission mapping, moderation duties, endpoint/DTO/error contract, Gateway routes and CORS. Do not publish internal enum names as a shared Seller contract without agreement.

## Persistence and transactions pending schema review

The user confirmed TV1 has not agreed the Seller schema/Auth contract. Therefore no JPA mapping, repository, Flyway migration, database configuration or new dependency is added. Seller writes are not durable yet, and duplicate registrations/profile validation/concurrent state changes cannot be claimed as complete.

Once agreed, create persistence only in seller-service's own logical PostgreSQL database. Use logical owner references, not a cross-database FK to user/auth-service. Place the persistence transaction in the orchestration service: load the existing application, enforce verified access/state rules, persist and protect concurrent updates with the reviewed locking/version strategy. Do not copy Category's REQUIRES_NEW/advisory lock automatically; Seller needs its own atomic transaction policy. No transaction annotation is added to pure in-memory rule evaluation.

## Verification

From backend using Java 21 and Gradle 8.x:

```bash
gradle :seller-service:build
gradle :product-service:build :seller-service:build
```

Seller tests need no Docker/database: immutable model/identity validation, the full lifecycle, all 20 state/action combinations, authentication/permission denial, ownership, self-moderation and invalid input. Reported authorization tests use a mocked access policy; real JWT/API/database integration remains NOT_RUN.

See SELLER_TEST_REPORT.md for actual verification and pending work. Shared build/settings and other members' modules remain unchanged.
