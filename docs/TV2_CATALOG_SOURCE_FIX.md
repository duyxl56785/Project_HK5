# TV2 Catalog source consistency — 2026-10-18

Refs #2, [PR #37](https://github.com/duyxl56785/Project_HK5/pull/37), [Checkout PR #28](https://github.com/duyxl56785/Project_HK5/pull/28). Branch `feat/tv2-week1-completion`. **Internal ambiguity FIXED; overall Issue PARTIAL / runtime integration BLOCKED.** Tested source commit: `63668b870c6e2e65e86c28b8c79715f2d0a9e7c8`; subsequent report changes are documentation only. [Source/test delivery comment](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246452552).

## Audit and contract gate

Started with clean TV2 head `c375951`, same as PR37; no unreviewed source from another contributor. Fetched main `524e4f3` and latest TV3 branch, read Issue2/checklists, PR37/PR28 comments/reviews, ownership, common/Auth contracts and the four prior TV2 handoffs. TV3 has since merged main into its branch at `28dd0cf` and resolved QA conflict; this task never touches `docs/QA.md`, Cart/Order/Inventory or another owner's source. Main is not pushed or merged by this task.

| Contract | Latest verified state | Remaining confirmation |
|---|---|---|
| S01 | PENDING | TV1 Store service/DB boundary, current Seller/Store/House authority; TV2 proposal remains Store in Seller DB with local Seller FK + unique Seller/slug, Product logical Store ID. |
| C01 | REVISE | [TV3 accepts candidate v0.2 for further sign-off](https://github.com/duyxl56785/Project_HK5/pull/37#issuecomment-6246255122), including canonical UUID strings, decimal strings, distinct1–100 and allReady; exact shared/runtime approval, source freshness and service pair still absent. |
| E01 | REVISE | Same TV3 technical acceptance of top-level code/message/errors and proposed statuses; TV1 auth/privacy/masking and consumer final agreement remain. Internal omission bug is now fixed, HTTP remains unimplemented. |
| M01 | PENDING | Product-side technical compatibility with TV5-FIN-v0.1's VND/integral/bounds is not TV5/human policy approval; Cart/Order currency/scale and unit/line/Store/parent aggregate bounds must agree before RESERVE. |
| Security | Concrete pair PENDING; OD-09 baseline DECIDED | TV1 Order→Catalog per-pair/path/method/delegated current actor, live proof; no public internal Gateway route or broad Inventory token. |

No whole contract newly qualifies as APPROVED. No production Store/Product JPA, migration, Catalog controller, verified policy adapter or persistent seed is added. Existing design/candidate SQL/fixture remain review inputs. No further production persistence work is authorized by silence or a green test.

## Internal implementation

Changed only three Java files under `backend/product-service`:

- `catalog/CatalogSnapshotSource.java`: `load(Set<UUID>)` now returns `List<LookupResult>`. Each result has `productId`, `LookupStatus` (FOUND / ABSENT / UNAVAILABLE), and optional `ResolvedProduct facts`. Exactly one result is required for every requested Product ID.
- `catalog/CatalogBatchValidator.java`: validates identity coverage and status/facts consistency before classifying any item; checks Product/Store mapping, SKU uniqueness within the batch and consistent current Store/Seller/owner/approved/open/House facts across rows sharing a Store. A rejected batch withholds **every** snapshot.
- `catalog/CatalogBatchValidatorTest.java`: all previous23 cases retained with explicit source outcomes, plus27 new invocations =50. No negative security test is altered or disabled.

| Source result / condition | Validator result |
|---|---|
| ABSENT for the requested ID, with no facts, complete batch | Item PRODUCT_NOT_FOUND; batch not valid. |
| FOUND with matching verified Product/Store/Seller/currency/category facts | Evaluate eligibility and compare expected identity/price/currency; caller expectations never supply facts. |
| UNAVAILABLE, with or without plausible cached facts | Throw DEPENDENCY_UNAVAILABLE for the whole batch. |
| Omitted/empty/null/incomplete response; duplicate/unrequested IDs; mismatched Product ID; contradictory ABSENT/FOUND payload | Whole-batch DEPENDENCY_UNAVAILABLE, never infer absence. |
| Source exception/timeout; inconsistent Store facts or duplicate SKUs | Whole-batch DEPENDENCY_UNAVAILABLE; no upstream/private detail in the domain error message. |
| Product non-sellable, expected ownership/price/currency mismatch or confirmed absence among otherwise valid items | Preserve per-item diagnostic codes, `valid()==false`, **all snapshots null**, so no usable READY subset. |

For an invalid batch a diagnostic item can still have code READY, but its snapshot is null; only a wholly valid batch releases snapshots. This is an internal result rule, not an implemented/published HTTP allReady schema. Future TV3 integration must still reject the entire batch before any reserve/order side effect. No cross-service price lock or transaction is claimed.

FOUND/ABSENT are assertions required from a **trusted future adapter**, not cryptographic proof introduced by this enum. That adapter must actually verify current persisted Product/Store/Seller/House/category/currency facts; stale, partial or unverified cache/dependencies must yield UNAVAILABLE or throw. No freshness TTL, timestamp policy, verified production source or auth bypass is invented here. A forged source marking stale facts FOUND cannot be independently detected from the current internal records; approving/implementing the authoritative adapter and freshness remains a release gate.

## Regression and actual execution

RED before fix: source `c375951` plus three new regressions, **0 PASS /3 FAIL**, exit1/19s/5tasks. Empty and partial source lists did not throw; a mixed price-change batch still returned a READY snapshot. Raw RED XML/log preserved. This is an intentional reproduced defect, not an environment initialization failure or a deleted test.

GREEN after fix: all50 Catalog unit invocations PASS, exit0/15s/5tasks. Coverage includes explicit confirmed absence; unconfirmed omitted/empty responses; unavailable/stale cached payloads; timeout; duplicate/unrequested/contradictory results; null/incomplete facts; Store/Seller/owner/status/House inconsistency; price/currency/ownership/non-sellable rejection; full-batch snapshot withholding and private-data exclusion. Twenty-seven new invocations supplement23 existing cases; no historical counts are added to the final run below.

Final execution at source63668b8: Windows, Java21.0.12, wrapper Gradle8.10, Docker Linux29.8.2, real PostgreSQL16.6 Testcontainers. From `backend`, process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`; both existing module integration tasks supply Docker API1.44 and are dependencies of check/build:

```powershell
.\gradlew.bat :product-service:build :seller-service:build --rerun-tasks --no-daemon --continue --console=plain
```

**BUILD SUCCESSFUL, exit0,52s,17 tasks executed.**

| Suite | PASS | FAIL/errors | SKIPPED |
|---|---:|---:|---:|
| Product unit/security (includes50 Catalog cases) | 215 | 0 | 0 |
| Product PostgreSQL:14 Category regression +11 SQL prototype | 25 | 0 | 0 |
| Seller/Store unit | 103 | 0 | 0 |
| Seller PostgreSQL production regression | 24 | 0 | 0 |
| Total | **367** | **0** | **0** |

All49 PostgreSQL invocations actually executed; **11 concern the isolated Product SQL candidate, not a production Product migration/schema/JPA**. Category/Seller persistence, original migrations/transactions and security implementation remain unchanged. Existing negative HTTP403 tests PASS; they do not prove positive JWT/Catalog API. No NO-SOURCE task is counted as a test. This is a two-module build, not a newly executed full-backend run. Docker inventories before/after show0 missing prior containers/volumes; no reset/delete/prune or existing DB changes.

Production Store/Product database/API, strict HTTP UUID/decimal-string serialization, Auth-issued JWT/service-pair tests, live CatalogPort consumer contract, durable two-Store seed and Checkout E2E: **NOT_RUN/BLOCKED**, because those components/contracts are not implemented/approved. Current TV3 `CatalogPort.verify(String)` still lacks inventorySku/currency/batch fields; it is not silently adapted to these Java types.

## Evidence and recovery

New machine-readable run: [TV2_CATALOG_SOURCE_FIX_TEST_SUMMARY.csv](TV2_CATALOG_SOURCE_FIX_TEST_SUMMARY.csv). Earlier CSVs/reports preserve their own historical runs and are not overwritten. Local ignored directory `backend/seller-service/build/catalog-source-fix/`:

| Artifact | SHA256 |
|---|---|
| red.log | C04FF2DB09C97C1D4CE562CA18DFDE70A844714ED25CCC104170C7CC84AB322F |
| red-result.xml | A8DEA928141C74A069D89D8C66DFEF3129CB5433B4C00BFF89DD86A69A41C771 |
| green-unit.log | BE6A7CFF1A6F4B0351A8CCA7571C811ED8763DD1F84536E19B5FC8760B28D35C |
| final-build.log | D1A9E0BE9A99624EAE8582023B77FBDA8AF5EBEE2EA43E86D7D375BD16214260 |

Final XML/HTML remain under module build outputs; raw logs/XML are not published with captured ephemeral runtime credentials. `git diff --check` for code/test PASS. Source/test commit63668b8 was pushed to PR37; report/checkpoint updates follow without changing tested Java.

Next session: read this report, [contract response](TV2_CATALOG_CONTRACT_RESPONSE.md), latest Issue2/PR37/PR28 replies, Git status and remote heads. Do not redo the confirmed-absence fix. If source changes, rerun the final command above; otherwise verify existing evidence. S01/M01/Security still require accountable owner/human decisions and C01/E01 final approval. Once those gates are explicitly satisfied, implement reviewed V2+ Store/Product persistence, trusted adapters, secured Catalog API and consumer tests, then idempotent seed/live two-Store validation. Do not rewrite Category/Seller, V1 or other owners' modules to bypass the gate. Issue #2 stays OPEN/PARTIAL; only the narrow internal-source fix and its evidence qualify for completion now.
