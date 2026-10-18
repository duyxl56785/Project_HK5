# product-service

**Owner:** TV2 (Catalog) + TV5 (Review).

Category Management is implemented in this module. Seller, Product, moderation, images and Review business features remain outside this change. Read ../../docs/OWNERSHIP.md before edits. This work references Issue #2; Category alone does not complete that issue.

## Deployment status and Auth integration

**Not ready for deployment as a Category CRUD API.** Verified Auth/Gateway integration has not been implemented.

- GET/HEAD Category endpoints are public.
- Every other method under `/api/categories` and `/api/categories/**` is denied by the production SecurityFilterChain, including requests containing an unverified Bearer token or an authenticated ADMIN identity.
- Writes return HTTP 403 with `CATEGORY_WRITE_AUTH_NOT_CONFIGURED`. No enable-write property, assumed JWT claims, HTTP Basic login or header-based permission bypass is provided.
- Controller write behavior is tested without security in an isolated MockMvc test. These tests do not claim that production HTTP writes are usable. Security tests and full application integration tests separately verify write denial.
- Before authorized writes, record the agreed signing profile/issuer, audience, role/authority mapping, Category write permission, Gateway routes, CORS and 401/403 contract with TV1. JWKS is required only if the agreed signing profile uses it. Enforce verified permissions at the service boundary exposed to HTTP; a Gateway-only check is insufficient.
- The Category filter chain and controller advice remain scoped to Category. A last-priority fallback chain denies every otherwise unmatched route, including future Product/Review/internal handlers and Actuator info/details. Its only exception is GET/HEAD of the exact `/actuator/health` path, preserving the existing operational health read. Gateway must not publish Actuator routes; detailed health exposure is not enabled here.
- Unconfigured routes return 403 `PRODUCT_ROUTE_AUTH_NOT_CONFIGURED` before their handler executes, even for a mocked ADMIN or forged identity headers. A new feature needs its own reviewed chain before it can be exposed. This fallback is not a JWT verifier or a production authorization adapter.

## Module API proposal for integration

This documents the approved module implementation; it does not replace or modify the shared `docs/CONTRACTS.md`. Consumer/owner agreement is still required before integration.

Request: `{ "name": "Electronics", "parentId": null }`.
Response: `{ "id": "<uuid>", "name": "Electronics", "parentId": null }`.

| Method | Endpoint | Controller behavior | Current production access |
|---|---|---|---|
| GET | `/api/categories` | 200, flat array sorted by name then ID, each item includes parentId | Public |
| GET | `/api/categories/{id}` | 200, or 404 when missing | Public |
| POST | `/api/categories` | 201 with Location and response DTO | Blocked, 403 pending Auth |
| PUT | `/api/categories/{id}` | 200 with updated DTO | Blocked, 403 pending Auth |
| DELETE | `/api/categories/{id}` | 204 without body | Blocked, 403 pending Auth |

`PUT` replaces the name and parent association; omitted/null parentId makes the category a root. IDs are immutable. Names are required, not blank, at most 255 Java String characters, and stripped of leading/trailing whitespace before persistence. Name uniqueness, slug, depth limits, pagination and a dedicated tree endpoint are not part of this scope. Frontend can construct the hierarchy using id/parentId.

Errors consistently contain `{ "code": "...", "message": "...", "errors": {} }`. Field validation fills the errors map. Internal SQL/exception messages are not returned to clients.

This existing Category errors map is preserved. The TV1 Auth draft proposes an optional errors array for its APIs; it does not silently change Category's contract. Agree any future alignment and consumer tests in a separate integration PR. The generic fallback uses only code/message, with no field-validation details.

| HTTP | Code | Meaning |
|---|---|---|
| 400 | VALIDATION_ERROR | Name validation failed |
| 400 | INVALID_REQUEST | Malformed JSON/UUID or other invalid request |
| 404 | CATEGORY_NOT_FOUND | Requested category does not exist |
| 404 | CATEGORY_PARENT_NOT_FOUND | Proposed parent does not exist |
| 409 | CATEGORY_CYCLE | Self-parent, descendant-parent or corrupt ancestor cycle |
| 409 | CATEGORY_HAS_CHILDREN | Delete would remove a parent with children |
| 409 | CATEGORY_CONFLICT | Database relationship/constraint conflict |
| 403 | CATEGORY_WRITE_AUTH_NOT_CONFIGURED | Writes disabled pending Auth |
| 500 | INTERNAL_ERROR | Unexpected failure; details remain server-side |

## Persistence and concurrency

PostgreSQL database belongs to product-service. Migration `V1__create_categories.sql` creates UUID keys, a nonblank/length-constrained name, nullable self-reference parent_id, parent index, self-parent CHECK and ON DELETE RESTRICT. There is no cascade deletion or cross-service foreign key. Hibernate uses `ddl-auto: validate`; Flyway owns schema creation.

Set these environment variables before starting the application; there are no default credentials:

```text
PRODUCT_DB_URL=jdbc:postgresql://<host>:<port>/<product-database>
PRODUCT_DB_USERNAME=<database-user>
PRODUCT_DB_PASSWORD=<database-password>
```

Create/update/delete acquire `pg_advisory_xact_lock(2142, 1)` on the same connection/transaction before reading categories. The lock serializes Category writers until commit/rollback. Ancestor traversal uses a visited-ID set and rejects any repeated ancestor. JDBC and JPA participate in the same JpaTransactionManager transaction, with READ_COMMITTED and open-in-view disabled. Writes use REQUIRES_NEW so an integration caller's older snapshot or preloaded persistence context cannot bypass cycle checks.

All structural writers, including future imports/admin tools, must use CategoryService and the same lock protocol. Do not write directly through the repository/SQL in production. The database FK/CHECK protects missing/self parents; multi-node cycle prevention is a service invariant, not a recursive database trigger. Category writes commit independently of an outer caller transaction, including when that caller subsequently rolls back; integration tests verify create/update/delete across this boundary. Do not hold the hierarchy lock or Category row locks in a suspended outer transaction. Nested calls need an additional database connection; pool sizing and atomic Product/Seller workflows must be reviewed when integrating. An outer persistence context that already loaded a Category remains stale after an independent write; callers must explicitly refresh it before relying on it.

Before merging with another Category migration, verify that V1 remains unused in the integration branch. Applied migrations must not be edited.

## Product/Seller/Frontend integration follow-up

- Product does not exist yet. This implementation cannot claim to reject deletion of categories referenced by Product.
- When Product is added in this same database, use a new Flyway migration for `products.category_id` with `ON DELETE RESTRICT`, after agreeing the model with TV2/TV5. Define a `CATEGORY_IN_USE` conflict and service validation alongside it; preserve the FK as the final guard against concurrent product creation/deletion.
- Do not add a fake Product repository or a guard that always reports no references. Product's schema and lookup contract need their own reviewed integration change.
- Seller integration must not grant Category mutation rights merely because a caller is an approved seller. TV1/TV2 must confirm who manages the global taxonomy.
- Frontend should use Gateway after route/CORS agreement. Currently only Category reads are usable; write forms must reflect pending authorization integration.

## Build and tests

Keep the shared Java 21 / Spring Boot 3.3.5 stack. Module dependencies add JPA, Validation, Security, PostgreSQL and Flyway. Test dependencies add Security Test and Testcontainers using the existing Boot dependency management; no framework version or shared build change is required.

From the repository's backend directory, using Gradle 8.x:

```bash
gradle :product-service:test
gradle :product-service:integrationTest
gradle :product-service:build
```

- `test`: unit tests, isolated HTTP contract tests, and real Spring Security filter-chain tests. No Docker/database needed.
- `integrationTest`: PostgreSQL 16.6 via Testcontainers; requires a running Docker Linux engine and permission to pull images. It verifies Flyway, JPA, FK/CHECK/index, rollback, real application reads/write denial and concurrent reparenting.
- `build` includes both suites through `check`. Integration tests are never silently skipped when Docker is absent; report the failed environment initialization and mark unreached cases NOT_RUN.
- Docker API defaults to 1.44 only in this module's integration task, because the managed older client otherwise fails against newer engines. For an older engine use `-PdockerApiVersion=<supported-version>`. No user/global Docker config is changed.
- Reports: `build/reports/tests/test/index.html` and `build/reports/tests/integrationTest/index.html`; XML under `build/test-results/`. Generated artifacts are ignored by Git.

The concurrent test pauses the first service transaction after it has acquired the real PostgreSQL lock, observes the second service transaction waiting on the exact advisory lock in pg_locks, lets A become a child of B and commit, and verifies B's attempted reverse reparent fails with CATEGORY_CYCLE. Only timing is coordinated by a spy; the lock/transactions are real. Another test verifies an older REPEATABLE_READ caller snapshot cannot bypass cycle checking.

## Architecture review — 2026-10-14

The existing `com.projecthk5.productservice.category` package is a single small feature with controller, DTO, service, repository/entity, exception handling, lock and security configuration responsibilities separated by class. This is Layered Architecture + Package-by-Feature; no module-wide controller/service/repository folders are introduced. With eleven production classes, additional layer subpackages would require broader visibility changes without improving a demonstrated integration problem, so the cohesive feature package is retained.

Refactored two concrete dependency issues: CategoryService/CategoryException no longer import HTTP status types; the domain exception contains a typed code and CategoryExceptionHandler alone maps it to the existing HTTP status/error string. CategoryResponse is now a plain DTO without a Category/JPA reference; the small conversion is private to CategoryService, avoiding an unnecessary mapper class. Controller remains free of business rules; repository only accesses the product database.

Endpoint/payload/error strings, validation, migration, security, transaction propagation/isolation and the advisory-lock protocol remain unchanged. REQUIRES_NEW is intentional and retained: actual PostgreSQL tests cover stale caller snapshots, lock contention, and create/update/delete remaining committed across an outer rollback. Integration callers still need to respect that boundary.

See CATEGORY_TEST_REPORT.md for actual verification results and remaining integration work.

## Week 1 independent follow-up — 2026-10-18

`product` now contains an internal immutable Product model, lifecycle/policy proposal and public-visibility projection; it is not a Product JPA model or a running Catalog API. No policy implementation or lifecycle Spring bean is registered. Existing Category security and the unmatched-route deny-all chain are unchanged.

The two-Store JSON fixture is test-only and not persisted; prices have no newly approved currency/rounding policy, and candidate quantities are not actual stock. Product/Store schema, global SKU uniqueness, Product→Category restrictive FK, upload/storage and provider/consumer APIs still require their own reviewed integration. Earlier Category-only statements above describe the original baseline; Product persistence remains absent. See [TV2 week 1 progress](../../docs/TV2_WEEK1_PROGRESS.md) for details and actual test results.

The Catalog follow-up adds an internal batch-validation proposal and isolated SQL-prototype PostgreSQL tests. There is no production source adapter, Catalog controller or Product migration; the proposed route remains denied by the existing fallback. Read the [Catalog handoff](../../docs/TV2_CATALOG_HANDOFF.md) for pending Store/API/money/security decisions and the verified 340-test two-module run. Prototype constraints and test fixtures do not establish production persistence or live Checkout readiness.
