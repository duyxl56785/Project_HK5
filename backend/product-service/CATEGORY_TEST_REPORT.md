# Category Management verification — Refs #2

Verified on 2026-10-13, Asia/Saigon. Branch: `feat/tv2-seller-product`.

Status: **PARTIAL for integration; module implementation and tests PASS**. Auth writes are intentionally disabled. The initial handoff was uncommitted; the follow-up review creates local Conventional Commits on the TV2 branch. No push, Pull Request, merge or Issue update was performed. `main` remains at `d6ddbb8261594024837a4b581003849e693a9335`.

## Actual verification

Environment: Java 21.0.12.1, Gradle 8.14.3 downloaded to a temporary tools directory, Docker Desktop with Linux engine 29.8.2, Testcontainers PostgreSQL 16.6-alpine.

Actual final command from repository root (PowerShell):

```powershell
& 'C:\Users\ADMIN\AppData\Local\Temp\project-hk5-category-tools\gradle-8.14.3\bin\gradle.bat' -p backend :product-service:build --rerun-tasks --no-daemon
```

Final result: **BUILD SUCCESSFUL**, exit code 0. Compile, JAR packaging, `test`, `integrationTest` and `check` passed. Counts below are taken from the generated JUnit XML, not inferred from source.

| Suite | PASS | FAIL | SKIPPED |
|---|---:|---:|---:|
| CategoryServiceTest | 18 | 0 | 0 |
| CategoryControllerTest | 13 | 0 | 0 |
| CategorySecurityTest | 7 | 0 | 0 |
| CategoryIntegrationTest | 14 | 0 | 0 |
| Total | **52** | **0** | **0** |

All 49 original tests remain present and passed again. Review added three PostgreSQL regression cases for create/update/delete committing independently of a caller rollback. `--rerun-tasks` ensured the verification executed rather than returning UP-TO-DATE.

Evidence generated locally:

- `build/reports/tests/test/index.html`
- `build/reports/tests/integrationTest/index.html`
- `build/test-results/test/TEST-com.projecthk5.productservice.category.*.xml`
- `build/test-results/integrationTest/TEST-com.projecthk5.productservice.category.CategoryIntegrationTest.xml`
- `build/libs/product-service-0.1.0-SNAPSHOT.jar`

These are ignored build outputs, not files to commit.

## Failure and correction

The first full build **FAIL**ed at Testcontainers initialization: the managed Docker client was rejected by the newer Docker engine. The PostgreSQL test bodies were **NOT_RUN** in that attempt. Added configurable `api.version=1.44` solely to this module's integrationTest task; no global Docker settings or shared build versions were changed. The following full build passed. A final full build after the additional error handling and older-snapshot regression test also passed.

Gradle reports deprecated features incompatible with Gradle 9. This does not fail Gradle 8.14.3. The shared project does not have a Wrapper; TV1 should review/add the shared Gradle 8.x Wrapper in a separate change.

## What was exercised

- Root/child creation, DTO listing/detail, rename, valid reparenting, moving to root, leaf deletion.
- Missing IDs/parents, null/empty/blank/oversized names, malformed UUID/JSON.
- Self-parent, two-node/deeper cycles and pre-existing corrupt ancestor chains.
- Rejected writes leave stored relationships unchanged; advisory locks release on rollback.
- Real PostgreSQL Flyway migration and Hibernate schema validation; parent FK, self-parent/name constraints, parent index and restrictive deletion.
- Two actual service transactions: the first holds the real PostgreSQL advisory lock, the second is observed waiting in pg_locks, and the reverse reparent is rejected after the first commits.
- A caller's older REPEATABLE_READ snapshot does not permit a cycle: mutations use a fresh REQUIRES_NEW/READ_COMMITTED transaction.
- Create/update/delete remain committed when the caller transaction subsequently rolls back, verifying the documented REQUIRES_NEW boundary.
- HTTP contract with security isolated: 200/201/204/400/404/409/500 and Location/error payloads.
- Actual security filter chain: anonymous writes, unverified Bearer token and even a test authenticated ADMIN identity are all blocked. PostgreSQL-backed full application reads pass and public create/update/delete cannot mutate data.

## Remaining work / NOT_RUN

| Item | Status | Reason |
|---|---|---|
| Authorized write API with real JWT | NOT_RUN / not implemented | TV1 has not confirmed JWT/permission contract; all writes remain 403 |
| Gateway routing, CORS, live Frontend/Seller integration | NOT_RUN | Shared contracts/modules outside this change |
| Reject deletion when Product references Category | NOT_RUN / not implemented | Product entity/schema does not exist |
| Cross-service or browser E2E | NOT_RUN | No integrated Seller/Product/Frontend workflow yet |
| Shared contract publication, review and merge | Pending | Owner agreement and user inspection required |

When Product is implemented, use a new migration for its category FK with ON DELETE RESTRICT and agree a CATEGORY_IN_USE error/validation with TV2/TV5. This change does not provide a fake reference check.

Category mutations commit independently of any caller transaction. Review that transaction boundary before composing Product/Seller workflows; do not assume an outer rollback also undoes a successful Category write. Do not hold hierarchy/Category row locks in a suspended outer transaction. Nested calls require an additional connection, and the caller's already-loaded entities remain stale after an independent write.

## Final code review

Reviewed all 20 changed files and their callers/dependencies. No production logic defect requiring an architecture or API change was found. Kept REQUIRES_NEW/READ_COMMITTED: the existing older-snapshot regression and actual lock-contention test prove its benefit in this implementation. Every writer takes the same transaction-scoped PostgreSQL advisory lock before any hierarchy reads; JDBC and JPA share the transaction.

Changes made during review:

- CategoryIntegrationTest: added three actual-database tests for independent commits across caller rollback; retained all existing tests.
- README: clarified connection-pool needs, suspended outer locks, stale caller entities and rollback semantics for integration owners.
- This report: updated verification counts and local commit status; preserved the initial Docker failure history.

The commit sequence keeps dependencies and relevant tests together: persistence foundation; transactional service with its DTOs/unit tests; HTTP/security with API/PostgreSQL tests; documentation. Staged snapshots are exported to ignored `build/category-review/checkpoint-*` directories and verified independently so later unstaged files cannot conceal a broken intermediate commit.

| Staged snapshot | Build | Executed tests |
|---|---|---|
| checkpoint-1: persistence/dependencies/config | PASS | None present at this checkpoint |
| checkpoint-2: service/DTOs/unit tests | PASS | 18 unit tests, 0 failures |
| checkpoint-3: API/security/integration tests | PASS | 52 tests, 0 failures, 0 skipped |

The documentation-only fourth commit does not change the tested sources. Both working-tree and staged whitespace checks passed. All 20 Category files are included in the four local commits, with no build outputs or files outside product-service. Read-only remote verification found main and the TV2 branch still at the original base SHA, so the local commits form a fast-forward candidate; no remote mutation was made.

## Original Category delivery file inventory — 2026-10-13

All paths below are relative to `backend/product-service`; no other module or shared build was edited.

Modified (3):

- `build.gradle`
- `README.md`
- `src/main/resources/application.yml`

Created (17):

- `CATEGORY_TEST_REPORT.md`
- `src/main/resources/db/migration/V1__create_categories.sql`
- `src/main/java/com/projecthk5/productservice/category/Category.java`
- `src/main/java/com/projecthk5/productservice/category/CategoryRepository.java`
- `src/main/java/com/projecthk5/productservice/category/CategoryService.java`
- `src/main/java/com/projecthk5/productservice/category/CategoryHierarchyLock.java`
- `src/main/java/com/projecthk5/productservice/category/CategoryController.java`
- `src/main/java/com/projecthk5/productservice/category/CategoryRequest.java`
- `src/main/java/com/projecthk5/productservice/category/CategoryResponse.java`
- `src/main/java/com/projecthk5/productservice/category/CategoryError.java`
- `src/main/java/com/projecthk5/productservice/category/CategoryException.java`
- `src/main/java/com/projecthk5/productservice/category/CategoryExceptionHandler.java`
- `src/main/java/com/projecthk5/productservice/category/CategorySecurityConfiguration.java`
- `src/test/java/com/projecthk5/productservice/category/CategoryServiceTest.java`
- `src/test/java/com/projecthk5/productservice/category/CategoryControllerTest.java`
- `src/test/java/com/projecthk5/productservice/category/CategorySecurityTest.java`
- `src/test/java/com/projecthk5/productservice/category/CategoryIntegrationTest.java`

## Review readiness

Suitable for a scoped **draft PR** referencing #2 after user inspection. It is not a completed/deployable CRUD integration and must not close Issue #2. Before integration/merge, TV1 should review the fail-closed security boundary, JWT/permission/401/403/Gateway/CORS contracts, PostgreSQL environment variables and Gradle/Docker compatibility. TV2/TV5 should review Category schema/migration and future Product reference policy. Consumer agreement is required before promoting this module proposal to the shared API contract.

## Architecture follow-up — 2026-10-14

Retained the small feature package `com.projecthk5.productservice.category`. Layers already have separate responsibilities: controller validates/dispatches HTTP, service owns transactions/rules, repository owns JPA access, entity owns persistence mapping, DTOs carry data, and lock/security classes handle infrastructure. Moving all classes into layer subpackages would expand visibility changes without a demonstrated benefit at this scale.

Two dependency issues were corrected without changing the API contract:

- CategoryService/CategoryException depended on HttpStatus. Exceptions now contain typed domain codes; CategoryExceptionHandler maps those codes to the same HTTP statuses and error strings.
- CategoryResponse contained Entity-to-DTO conversion. It is now a plain record with no JPA/entity reference; a small private conversion in the service replaces it without creating a mapper class.

Adapted all affected unit/API/PostgreSQL tests to the internal enum. No original test was removed. REQUIRES_NEW, READ_COMMITTED, advisory-lock acquisition before reads, validation, schema/Flyway migration, endpoints/payloads and fail-closed security remain unchanged. Independent rollback semantics are intentional and remain covered by three actual-database cases.

Verification executed on Java 21/Gradle 8.14.3:

```powershell
& 'C:\Users\ADMIN\AppData\Local\Temp\project-hk5-category-tools\gradle-8.14.3\bin\gradle.bat' -p backend :product-service:build :seller-service:build --rerun-tasks --no-daemon
```

Final command: **BUILD SUCCESSFUL**, exit 0; Category **52 PASS / 0 FAIL / 0 SKIPPED**, Seller **63 PASS / 0 FAIL / 0 SKIPPED**, total **115 PASS**. All test tasks executed in the final command, not UP-TO-DATE. This is full build for the two changed modules, not an E2E or full deployment verification for every microservice.

Initial attempts failed at Docker/Testcontainers startup, so Category PostgreSQL test bodies were NOT_RUN in those attempts. Docker Desktop logs showed Secrets Engine could not rename its stale engine.sock (Windows ERROR_CANT_ACCESS_FILE). The runtime directory was observed already moved to backup on the machine; this task did not rename/delete it or reset Docker. After restarting Docker, Engine 29.8.2 responded and all tests passed. This failure is distinct from the older Docker API mismatch documented above. Similar startup failure is reported at https://github.com/docker/for-win/issues/15064.

Files changed in this follow-up: CategoryException, CategoryExceptionHandler, CategoryResponse, CategoryService; CategoryControllerTest, CategoryIntegrationTest, CategoryServiceTest; README and this report. Existing four Category commits remain intact; follow-up changes use new commits only. Seller API/JWT/schema/persistence remain deferred following the user's explicit confirmation that TV1 has not agreed those contracts. No shared build, other member's module, main, applied migration, Store or Product implementation is changed; no push/PR/merge is performed.

## TV1 review remediation — 2026-10-16

Responds to the TV1 technical review on PR #7. The previous Category-only chain left otherwise unmatched requests without a security chain. A test-only Product/Review/internal controller reproduced the defect: all nine selected anonymous requests returned 200 instead of the expected 403. That red run is preserved in `build/pr7-security-red.log` and `build/pr7-review-20261016/red/`.

Added `security/ProductSecurityConfiguration`: a last-priority, stateless fallback denies every request not matched by a feature chain. Only GET/HEAD of the exact `/actuator/health` path remains permitted for operational health reads. Unconfigured routes return 403 `PRODUCT_ROUTE_AUTH_NOT_CONFIGURED` with code/message; this is a fail-closed boundary, not JWT authentication. The existing Category chain still runs first and its API/error contract is unchanged. New routes require an explicit reviewed security policy.

Added 15 `ProductServiceSecurityTest` cases with real Spring Security filters and test-only handlers: Product verbs, Review/internal paths, a Category-like prefix, mocked Admin, forged headers/Bearer, unmatched routes and health read/write policy. Handler invocation counters verify denial before business execution. The health handler is a policy fixture, not a live Actuator/database health check. Existing CategorySecurityTest now imports both chains, preserving all seven regressions.

Verification executed on Java 21.0.12.1 / Gradle 8.10:

```text
gradle -p backend assemble test --rerun-tasks --continue --no-daemon --console=plain
```

**PASS:** BUILD SUCCESSFUL, exit 0, 59 tasks executed; all 11 modules assemble. Product has 53 tests (18 service + 13 controller + 7 Category security + 15 fallback security), Seller has 63, total **116 PASS / 0 FAIL / 0 SKIPPED**. Other modules report NO-SOURCE, which is not business-test PASS. Log: `build/pr7-review-20261016/green-unit-build.log`.

**NOT_RUN:** 14 PostgreSQL integration tests, full build/check including integrationTest, live JWT/Gateway/UI/E2E. Docker Engine does not expose the dockerDesktopLinuxEngine pipe on this machine. Historical PostgreSQL PASS results above are not counted as newly executed. No test is removed or disabled; the existing test/integrationTest split is preserved.

REQUIRES_NEW/READ_COMMITTED, lock key/acquisition order, all 14 PostgreSQL tests and the V1 migration remain unchanged. The rollback, additional-connection, suspended-lock and stale-persistence-context limits remain documented in README; this fix does not make Category writes atomic with a future Product transaction. PRODUCT_DB_URL/USERNAME/PASSWORD remain required, Flyway remains enabled and JPA ddl-auto remains validate; no shared database/runtime configuration is changed.

Category's existing errors map is retained; TV1's proposed optional errors array requires a separately agreed integration change. The signing/identity/role adapter is not implemented here. This addresses the fallback finding for re-review; independent approval is still required before merge, and Issue #2 remains PARTIAL.
