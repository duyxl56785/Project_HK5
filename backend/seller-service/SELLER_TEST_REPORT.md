# Seller domain verification — Refs #2

**Latest recheck — 16/10/2026:** see [SELLER_PERSISTENCE_RECHECK.md](SELLER_PERSISTENCE_RECHECK.md). Seller 80 and Category/security 53 tests were rerun and PASS; assemble 11 modules PASS. Full backend build/check was actually executed and FAIL because both PostgreSQL suites failed Testcontainers initialization (21 Seller + 14 Category DB test bodies NOT_RUN). Docker Secrets Engine remains blocked. Persistence is PARTIAL, PR #25 stays Draft; historical PASS/NOT_RUN entries below are not the latest full-build result. Auth v1.0 decisions now exist in unmerged PR #24, while runtime/wire adapters remain pending.

Verified 2026-10-14, Asia/Saigon, branch `feat/tv2-seller-product`.

Status: **PARTIAL for Seller Management integration**. Internal domain/lifecycle and unit tests are implemented. The user explicitly confirmed TV1 has not agreed the Auth/schema contract and requested domain/tests first, leaving API/persistence pending.

## Architecture and scope

`seller.domain`: SellerApplication, SellerIdentity, SellerStatus, SellerRuleException.
`seller.service`: SellerLifecycleService, SellerAccessPolicy.

This is Layered Architecture + Package-by-Feature. Immutable domain data has no Spring/JPA/HTTP dependency; service owns state/ownership/access checks. SellerAccessPolicy is the necessary Auth adapter interface; it has no production implementation or allow-all fallback. SellerLifecycleService is not a Spring bean. No controller, externally agreed DTO, endpoint, claim/role mapping, JPA entity/repository, migration, Store or Product is introduced.

Internal state transitions implemented and tested:

- Register starts PENDING_REVIEW after the access-policy check.
- Reject: PENDING_REVIEW to REJECTED, with a nonblank reason.
- Owner resubmits: REJECTED to PENDING_REVIEW; old reason clears.
- Approve: PENDING_REVIEW to APPROVED.
- Suspend: APPROVED to SUSPENDED, with a nonblank reason.
- Reinstate: SUSPENDED to APPROVED; old reason clears.
- All other state/action combinations are rejected.
- Every moderation operation requires the policy check and a different actor from the owner; self-approval/self-reinstatement are blocked even with a granted test moderation permission.
- Null/blank identities, missing application/actor/status, missing review reason, non-owner resubmission, denied authentication and denied permission are rejected.
- Operations return immutable new snapshots; failed operations leave the original unchanged.

These are internal rules/proposals, not a published API or schema. Moderation separation of duties, state labels, profile fields, reason limits and identity mapping still require TV1/TV2 agreement before integration. A future HTTP adapter must load persisted owner/current state server-side and derive actor identity from verified Auth; it must never accept a current SellerApplication or permission flags from the client.

## Actual build/test evidence

Environment: Java 21, Gradle 8.14.3. Seller tests use JUnit 5/Mockito from the existing shared starter test dependency; no module dependency/build/config change is needed.

| Suite | PASS | FAIL | SKIPPED |
|---|---:|---:|---:|
| SellerApplicationTest | 13 | 0 | 0 |
| SellerLifecycleServiceTest | 50 | 0 | 0 |
| Total Seller | **63** | **0** | **0** |

The lifecycle suite exercises all 20 combinations of five actions and four current states, successful lifecycle, ownership, policy-denial and self-moderation boundaries. The access policy is mocked: these results do not prove real JWT verification/authorization.

The initial domain-only build passed 13 tests before its commit. The lifecycle increment passed all 63 tests and was committed separately. A standalone Seller build completed successfully; the final combined full build reran all tests:

```powershell
& 'C:\Users\ADMIN\AppData\Local\Temp\project-hk5-category-tools\gradle-8.14.3\bin\gradle.bat' -p backend :product-service:build :seller-service:build --rerun-tasks --no-daemon
```

**BUILD SUCCESSFUL**, exit 0; 63 Seller + 52 Category tests = **115 PASS**, 0 failures/skipped. Compile, bootJar, test, integrationTest for Category and checks completed. Generated reports are under `build/reports/tests/test/index.html` and `build/test-results/test/`; generated artifacts are ignored and not committed.

Earlier combined attempts failed during Category Testcontainers initialization because Docker Desktop's Secrets Engine socket prevented daemon startup. Seller tests passed in those attempts; Category PostgreSQL cases were NOT_RUN until Docker responded. After the runtime directory was observed backed up externally and Docker restarted, final combined verification passed. No test was deleted/skipped to obtain PASS.

## Pending integration / NOT_RUN

| Item | Status | Needed decision |
|---|---|---|
| HTTP API/DTO/error mapping and Bean Validation requests | NOT_RUN / not implemented | TV1/TV2 endpoint and profile contract |
| JWT and production SellerAccessPolicy | NOT_RUN / not implemented | TV1 verified identity namespace and permission mapping |
| Seller database/JPA/Flyway/repository | NOT_RUN / not implemented | Approved owner ID/type, profile schema and constraints |
| Durable registration/unique owner/concurrent updates | NOT_RUN / not implemented | Schema and transaction/locking strategy |
| Gateway/CORS, Frontend, live security/E2E | NOT_RUN | Shared integration contract/environment |
| Store/Product behavior or events on suspension | Outside scope | Separate reviewed integration work |

No database transaction annotation is attached to pure rule evaluation. Future persistence orchestration must atomically load/check/save Seller in seller-service's own database and protect competing transitions with an agreed concurrency strategy. Do not reuse Category REQUIRES_NEW/advisory locks by assumption, and do not create cross-database FKs to Auth/User.

## File inventory and commits

Created:

- `src/main/java/com/projecthk5/sellerservice/seller/domain/SellerStatus.java`
- `src/main/java/com/projecthk5/sellerservice/seller/domain/SellerRuleException.java`
- `src/main/java/com/projecthk5/sellerservice/seller/domain/SellerIdentity.java`
- `src/main/java/com/projecthk5/sellerservice/seller/domain/SellerApplication.java`
- `src/main/java/com/projecthk5/sellerservice/seller/service/SellerAccessPolicy.java`
- `src/main/java/com/projecthk5/sellerservice/seller/service/SellerLifecycleService.java`
- `src/test/java/com/projecthk5/sellerservice/seller/domain/SellerApplicationTest.java`
- `src/test/java/com/projecthk5/sellerservice/seller/service/SellerLifecycleServiceTest.java`
- `SELLER_TEST_REPORT.md`

Modified: `README.md` only. Seller build.gradle/application.yml/entry point are unchanged.

Verified local increments:

- `c4ad14a` — `feat(seller): add internal application model and validation (refs #2)`.
- `5646445` — `feat(seller): implement guarded lifecycle transitions (refs #2)`.

Documentation is committed separately after verification. The original four Category commits are preserved; no push, PR, merge or edits to main/other members' modules are performed. Suitable for a draft PR scoped to Category architecture and Seller domain, with integration blockers explicit; not sufficient to close F03-F04 or Issue #2, or deploy Seller Management.

## Seller persistence verification — 2026-10-16

The project owner supplied TV1's minimum persistence confirmation, recorded at Issue #2 comment 6205790432 and SELLER_PERSISTENCE_CONTRACT.md. Branch feat/tv2-seller-persistence depends on PR #7; only seller-service changes are introduced. Existing domain/lifecycle/policy and their 63 tests are unchanged.

Implemented separate JPA entity/repository, Flyway V1, exact VARCHAR(128) unique owner, UUID Seller ID, status/reason checks and @Version. REQUIRED orchestration reuses lifecycle rules, keeps resubmission ID, clears obsolete reasons, accepts expectedVersion and translates the named owner-unique constraint/optimistic-lock failures into internal errors. No HTTP API/production Auth policy is registered; integration wiring exists only in tests.

Actual verification: Java 21.0.12.1, Gradle 8.10.

```text
gradle -p backend assemble test --rerun-tasks --continue --no-daemon --console=plain
```

**PASS:** BUILD SUCCESSFUL, exit 0, 59 executed tasks; all 11 backend modules assemble. Latest XML totals:

| Suite | PASS | FAIL | SKIPPED |
|---|---:|---:|---:|
| Existing SellerApplicationTest | 13 | 0 | 0 |
| Existing SellerLifecycleServiceTest | 50 | 0 | 0 |
| SellerProfileTest | 10 | 0 | 0 |
| SellerPersistenceServiceTest | 7 | 0 | 0 |
| Seller total | **80** | **0** | **0** |
| Product/Category regression | 53 | 0 | 0 |
| All executed unit/controller/security tests | **133** | **0** | **0** |

Entity tests cover exact owner/length, fresh registration states, reason boundaries including supplementary Unicode, owner immutability and reason clearing. Service unit tests prove denied authentication/moderation cannot reach repository lookups/writes. Policy is mocked; these are not JWT tests.

**FAIL — environment:** `:seller-service:integrationTest --rerun-tasks --no-daemon` executed, exit 1, Testcontainers initializationError because Docker Linux Engine is unavailable. It did not reach PostgreSQL test bodies. The suite defines 21 planned invocations (10 ordinary/concurrency cases plus 11 SQL-constraint rows); these are **NOT_RUN**, not PASS or skipped. All source/test classes compile. No test/tag/task was disabled to obtain the unit PASS result.

The PostgreSQL suite is prepared to verify durable registration/Flyway/JPA, no cross-DB FK, duplicate/exact owner, resubmit ID/full lifecycle, REQUIRED registration/moderation outer rollback, stale expectedVersion, SQL null/length/status/reason/version constraints, a real unique-insert lock wait observed in pg_stat_activity, and two real transactions loading the same version before concurrent moderation. None of those DB/concurrency claims is considered proven until this suite runs.

Docker Desktop startup was attempted. Host logs identify Secrets Engine failure to rename its existing zero-byte encrypted/reparse engine.sock. A non-destructive runtime backup attempt was denied by Windows; the original socket remains, and no images/volumes/factory reset or successful decryption occurred. Docker recovery requiring host access remains a blocker.

Logs: build/persistence-verification/all-unit-build.log and postgresql-attempt.log; raw initialization XML preserved in initial-postgresql/. Generated artifacts are ignored. Full build/check including PostgreSQL, the 14 existing Category DB tests, live JWT/API/Gateway/E2E remain NOT_RUN in this final verification. Persistence AC is PARTIAL pending PostgreSQL PASS; Issue #2 stays OPEN/PARTIAL and the PR stays Draft.
