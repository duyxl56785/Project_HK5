# TV2 HTTP integration checkpoint — 28/10/2026

Branch: feat/tv2-protected-http-api-integration. Worktree: .worktrees/tv2-protected-http-20261028.
Base dependency: PR85 feat/tv2-seller-store-http-read at fc39c7100caa7ffb49f9baa27b18dde595b54d1f; main bb0b9a2d0d2e909a59653619bce98c77f75e77d2 merged natively for testing, not into main.
See the private tv2-protected-http-20261028/CHECKPOINT.md for final publication SHA/CI/raw evidence; this tracked file describes the source scope, not production acceptance.

## Actual API inventory

| Feature | Existing source | HTTP registered | Auth enforced | Tests | Gap |
|---|---|---|---|---|---|
| Seller own profile | SellerPersistenceService.findOwn; SellerStoreReadHandler.sellerOwn | No | Deny-default, no runtime JWT | ReadIntegration + security slice | Verified current-account adapter |
| Seller register/resubmit | PersistenceService + new ManagementHandler + strict Json | No | Account port and domain policy; deny-default HTTP | ManagementHandlerTest/ManagementIntegrationTest | Servlet registration, JWT and wire acceptance |
| Seller admin moderation | findForModeration/approve/reject/suspend/reinstate | No | Domain moderation policy + self-denial, no runtime JWT | ManagementIntegrationTest | Current ADMIN adapter; queue/list HTTP absent |
| Store management | findOwn/create/revise/setOpen + new ManagementHandler | No | Current owner/APPROVED domain rules, deny-default HTTP | ManagementIntegrationTest | Current SELLER/JWT adapter |
| Product create/detail/edit/list | ProductManagementHandler/ApplicationService + owner search | No | Product deny-default HTTP | Existing Product build regression | Production ProductManagementAuthority/ProductAccessPolicy |
| Product submit/admin/status | Handler.submit/approve/reject/suspend/deactivate | No | Product deny-default HTTP | Existing Product build regression | Current roles/Store/Seller facts and reviewed wire/errors |
| Product image metadata | ProductImageMetadataService.reserve/cancel/list on main V3 | No | Owner policy; Product deny-default HTTP | Existing Metadata integration tests | Upload/key binding only in unmerged116 |
| Image upload/read/cancel | Draft116 V4/ProductImageLifecycleService | No | Owner policy, no registered HTTP | Historical116 tests, not inherited here | Unmerged dependency; runtime/security/retention gates |
| Category reads | CategoryController.list/get at /api/categories | Yes, GET/HEAD | Explicit public reads | CategorySecurityTest/CategoryIntegrationTest | Gateway integration not accepted |
| Category management | CategoryController.create/update/delete | Yes, but403 denied | CategorySecurityConfiguration deny-all writes | CategorySecurityTest | Verified current ADMIN adapter |
| Public Catalog | ProductCatalogSearchImpl + ProductCatalogSnapshotSource | No | Product deny-default HTTP | Existing Catalog regression | CurrentStoreFactsSource.loadCurrent(Store IDs), House/freshness, accurate eligible pagination, service-pair contract |

Seller positive tests use a test-only request authority with real local PostgreSQL services; they are not real signed-JWT, Gateway or positive HTTP acceptance. Security tests use the real servlet filter chain and test-only probe/principal. No positive business HTTP endpoint was registered.

## Binding dependency evidence

- Auth80 at00c962c, bootstrap101 ate45cede and HTTP/Gateway102 at36ad517 remain OPEN/unmerged at preflight. CurrentAccountAuthority is Auth-DB local; importing it into Seller/Product would break database-per-service. Hk5JwtCodec is not a shared verifier module.
- Gateway102 GatewayAuthConfiguration/GatewayJwtDecoder are Auth-route candidates, not accepted Seller/Product route or current-authority service-pair adapters. No JWT source copy, direct cross-service DB access or client identity headers are introduced.
- SellerStoreReadAuthority has only requireCurrentAccount/requireCurrentSeller; SellerAccessPolicy still enforces moderation permission, StoreAccessPolicy rechecks persisted Seller owner/APPROVED. No role supplied by payload is accepted.
- UI95/110 and shared105 remain separate unmerged candidates. No frontend/shared code changes in this slice.

## Execution plan and stop conditions

1. Verify clean baseline main+85 with actual Seller/Product build and PostgreSQL; preserve original worktree.
2. Test first the missing command boundary; add Seller/Store register/resubmit/current-Store management/admin commands using existing codec/DTO/services, no duplicated lifecycle.
3. Add module-local deny-default servlet security, no JWT verifier or business controller. Retain CSRF and health GET/HEAD only; even accidental business handlers and claimed ADMIN identities must remain inaccessible.
4. Keep real JWT positives, 401/current-account/service-pair semantics, protected API registration and frontend E2E BLOCKED_TV1; do not invent a second security mechanism or activate flags.
5. Run tests, inspect explicit stage list, commit/push to the existing PR85 if appropriate, update its scope/review request once, Issue2 checked subtasks only; no merge.

Prepared JSON fields are inherited from SellerStoreJson: register {}, version commands {expectedVersion}, create Store {name,slug}, revise {name,slug,expectedVersion}, open/close {open,expectedVersion}, moderation reason {expectedVersion,reason}. Versions stay canonical decimal strings. No new public route or shared wire contract is approved by this source work.

## TV1 handoff required before real API

Provide accepted per-service verifier/current principal and a current-account/effective-role lookup protocol; effective role must not exceed signed token AND current grants. Define authenticated service-pair route/method/DTO/freshness/failure behavior for Product's current Seller/Store facts, without default House values. Confirm Seller/Store/Product/image paths, 401/403/404/409/503 privacy/error contracts and Gateway allowlist. Then register thin HTTP controllers and run real signed-token + Gateway + PostgreSQL positive/negative tests.

Continue: inspect current branch status and private final checkpoint; fetch latest85/80/102/116 before edits. API/P0/E2E remain PARTIAL, no deployment/flags.

## Verified milestone

Full command: `backend/gradlew.bat -p backend build --rerun-tasks --no-daemon --console=plain` with Java21.0.12/Gradle8.10/Docker29.9.1/PostgreSQL16.6 and process-local `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`.
Actual final invocation:1540PASS/0FAIL/errors/3SKIPPED, exit0/2m10s/73tasks. Seller303unit+164PostgreSQL-tagged=467; Product573unit+303PostgreSQL-tagged=876; Auth31/Cart11/Inventory27/Order128 are the remaining197passes. Counts are from one invocation, not combined with baseline/focused runs. Existing SQL-prototype cases remain prototype evidence, not production JWT/API acceptance.
Three optional checks SKIPPED: Auth/User DatabaseReadinessTest (TV1_DATABASE_TESTS absent), Auth EmailInventoryTest (AUTH_EMAIL_INVENTORY_JDBC_URL absent). No test was disabled to achieve success. New subset26management-unit+15real-filter security-slice+12PostgreSQL command cases is included in467, not added again. Positive authorities are test-only; no production account/role is granted by this work.
Guard regression: with the actual test probe registered, default security lets an authenticated fixture reach it (HTTP200);15tests RED before the custom deny boundary, then15GREEN. Initial fixture-only health404 was corrected by explicit test-controller import without changing assertions; that setup failure is not the meaningful RED. The first full backend run failed6old Store route404 assertions; they now require403, fixed diagnostic, no owner leak and unchanged DB; every case is retained.
Independent read-only final review found no actionable defect. Full log SHA256:`56f234b7e796d04474c64a54e90d6b3fee81082fd8e3f8b76729b748fd15d12b`. Full testing precedes final commits; the final Java/build/YAML/test content is fingerprint-verified unchanged. Source-only local success does not imply shared CI success or HTTP activation.
Publish into existing Draft85; request TV1/TV5 on the new scope/head once. Shared readiness CI had Auth/Testcontainers initialization failure on main before this slice; its actual new-head status must be checked separately. TV2 does not edit Auth/Gateway/shared CI or promote the local API flag to repository configuration.

## Fast-track follow-up — 28/10/2026

- [TV2 formal source-candidate APPROVE for Auth102](https://github.com/duyxl56785/Project_HK5/pull/102#pullrequestreview-5681166198) at36ad517: repository suites147PASS/2optionalSKIP, including10real Gateway→Auth→PostgreSQL E2E. The same invocation has one historical private-probe403 expectation failure (overall161PASS/1FAIL/2SKIP, exit1); a separate probe updated to exact404/405 is15PASS/0FAIL/SKIP, exit0. No TV1 source edit or runtime approval.
- [Role Sync principles confirmed](https://github.com/duyxl56785/Project_HK5/issues/8#issuecomment-6466445250); exact wire/ACK/OD-09 credential still needs TV1+TV2 signoff.
- Separate [Draft118](https://github.com/duyxl56785/Project_HK5/pull/118) at7d5213f adds local same-transaction Seller status snapshots/backfill, not a sender or role grant. One full Seller+Product invocation1269PASS/0FAIL/SKIP, exit0; this source is not included in85.
- Native detached rehearsal85@24b072e +118@7d5213f has tree41ca94084338dc58d4c372f48a19c766f797c550: Seller full476PASS/0FAIL/SKIP, exit0/1m58s. Only README append conflict; both sections preserved. No original branch changed and no rehearsal commit pushed. Do not add476 to1269 as one run.
- [CI118 run40673244143](https://github.com/duyxl56785/Project_HK5/actions/runs/40673244143) FAILED in AuthPersistenceTest initialization at line51; local Seller/Product PASS does not replace shared CI. Prior85 run40634379898 also failed Docker/Testcontainers startup. TV1 owns runner/provider diagnosis; no test skipped or shared CI edited.
- Seller/Product business HTTP remains BLOCKED_TV1: accepted per-service Servlet verifier, exact principal/current effective grants, authenticated current Store/Seller/House/freshness source and agreed route/DTO/error mapping. Image116 remains separate/unmerged; UI95/110/105 are not wired. F03–F08/P0/API/E2E remain PARTIAL.

NEXT EXACT ACTION: TV1 completes per-service authority/wire handoff and CI diagnosis; TV1/TV5 review118 and116. Then wire existing handlers with thin protected controllers and real JWT/Gateway/PostgreSQL tests. No merge/deploy/flags in this follow-up.

## TV1 engineering takeover — 29/10/2026

- [Draft119](https://github.com/duyxl56785/Project_HK5/pull/119), edf0320: all Gradle Test JVMs use the existing Docker API1.44 policy. Testcontainers fallback1.32 is below local/runner minimum1.40. Local fullbackend+OrderPG1486PASS/3optionalSKIP; [CI40821482449SUCCESS](https://github.com/duyxl56785/Project_HK5/actions/runs/40821482449), actual OrderPG29PASS and full buildPASS. This fix is not yet merged into85/118/main; their historical RED runs are not relabelled.
- [Draft120](https://github.com/duyxl56785/Project_HK5/pull/120),120d05e: verification-only shared JWT core actually reused by Auth and exact scoped-pair guard. Stacked102@36ad517, includes only2-file cherry-pick119, not othermain source. Exact-head local182PASS/0FAIL/2optionalSKIP, including35core,28AuthPG,5current-authorityPG and10real Auth/Gateway/PG E2E. No downstream Servlet/current-account transport or authority bean is registered.
- The user confirmed no FINAL CO-SIGN exists. RoleSync ACK/schema, StoreFacts DTO and current-account lookup/pair/freshness remain unapproved. Do not implement/enable the dependent transport as though it were signed; existing Seller/Product deny-default remains.
- NEXT EXACT ACTION: independent review119/120, then exactwire signoff. Only then continue receiver/dispatcher/current-authority adapters and protected HTTP. Latest takeover source/test/CI commands are in private tv1-takeover-20261029/CHECKPOINT.md. F03–F08/P0/API/E2E remain PARTIAL; no merge/deploy/flags.
