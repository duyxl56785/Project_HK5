# TV2 PR follow-up — 2026-10-18

Refs #2. Branch `feat/tv2-week1-completion`; previous delivery `9c28338`. This follow-up changes TV2 documentation only. No production Java, migration, shared contract/build or another member's source is changed. GitHub CLI was unauthenticated; reviews were successfully submitted through the connected GitHub account `duyxl56785`, without changing local credentials.

## Published reviews

| PR / inspected HEAD | TV2 action | Verified scope |
|---|---|---|
| #40 / `e91312c8d897b8faf12fed151022508025719de1` | [COMMENT review 5561615997](https://github.com/duyxl56785/Project_HK5/pull/40#pullrequestreview-5561615997) | 49/49 public blob SHA256 checks, 32 public XML suites +3 explicitly derived summaries/41 withheld testcases, reconciled counters, 18 relative Markdown links and docs diff check PASS. No new blocker in audited evidence. |
| #28 / `55bf5ff086633eddd37c245abc809f5e0ce57be8` | [COMMENT review 5561616196](https://github.com/duyxl56785/Project_HK5/pull/28#pullrequestreview-5561616196) | Delta from bd5ff09: three Finance test classes plus docs/evidence, no production/config/build/Flyway change. New Gradle/JUnit and PostgreSQL execution below; no source regression found within the tested delta. |

Neither action is APPROVE/MERGE. No other review/thread was dismissed/resolved and no duplicate reviewer was requested. PR #40's earlier PR states are historical snapshots; #19 has since merged and #28 advanced. Its raw evidence must not be rewritten to look current. Three withheld XML hashes cannot be publicly recomputed; published artifact verification is not a new execution of TV5's suites or an absolute secret certification.

## Actual new PR #28 verification

Exact snapshot Git archive of `55bf5ff`, Windows/Java21.0.12/Gradle wrapper8.10/Docker Linux29.8.2/PostgreSQL16 Testcontainers. Process-only `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`. From snapshot `backend`:

```powershell
.\gradlew.bat :cart-service:test :order-service:test --rerun-tasks --no-daemon --continue --console=plain
.\gradlew.bat :cart-service:postgresIntegrationTest :order-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain
```

Both invocations **BUILD SUCCESSFUL**, exit0; unit29s/8tasks, PostgreSQL52s/8tasks. This is unit + explicit PostgreSQL verification, not a claimed full backend build.

| Module / task | PASS | FAIL/errors | SKIPPED |
|---|---:|---:|---:|
| Cart test | 6 | 0 | 0 |
| Order test | 66 | 0 | 0 |
| Cart postgresIntegrationTest | 1 | 0 | 0 |
| Order postgresIntegrationTest | 12 | 0 | 0 |
| Total | **85** | **0** | **0** |

The 13 PostgreSQL invocations actually ran against Testcontainers/Flyway/JPA, including replay/scanner and Finance persistence/precision characterization. JWT cases load real YAML/environment but do not use live Auth-issued tokens. External Catalog/Inventory ports remain mocked/unconfigured; live service-pair authorization, durable Store/Product seed and Checkout E2E **NOT_RUN/BLOCKED**. The Finance amount test uses synthetic identities, not verified Store ownership. Do not add historical TV5 parent61+11 or supplementary72+13, TV2 Product/Seller340, or PR40 snapshots into this total.

Docker was initially stopped. Default install-path launch failed because Docker is installed per-user; the verified per-user executable started Desktop successfully. Sandbox socket access was denied, then authorized runtime access returned Client/Server29.8.2 before PG tests. No test was skipped/deleted and no Docker reset/prune/data deletion was performed. Before/after inventories show **0 missing prior containers/volumes**. These preliminary environment failures are not PostgreSQL test-body failures; both actual Gradle runs passed.

Local ignored evidence under `backend/seller-service/build/tv2-followup-review/`:

- `pr28-unit.log`: SHA256 `B94B34CA8DAE305F54FA4B9B0DA69474BB577A5015C1D55B2B2889483EEE57CC`.
- `pr28-postgres.log`: SHA256 `F1128DC088A5518F378AF2C450748B5EEEF4CC417C205549DEF808B0D6D768C6`.
- Snapshot test-results XML, `pr28-test-summary.csv`, `pr40-evidence-verification.json` and Docker inventories. Raw logs/XML remain local, not published with captured runtime credentials.

## Catalog response and remaining work

[TV2 Catalog candidate v0.2](TV2_CATALOG_CONTRACT_RESPONSE.md) answers C01/E01/UUID/decimal representation, authoritative absence versus incomplete response, Store/House authority, freshness and Product-side M01 compatibility. It proposes canonical UUID strings, decimal monetary strings and explicit `allReady`; these are not implemented/shared-approved HTTP DTOs. The current source omission ambiguity must be corrected before wiring. No runtime Catalog endpoint has become callable.

TV2 can technically support TV5-FIN-v0.1's explicit VND/integral price/bound proposal; that does not sign currency/fees/refunds as a human business owner or approve another owner's contract. S01 needs TV1, wire/error/security need TV1+TV3, money needs TV5/business approval and TV3/TV4 compatibility. Keep Finance false and adapters fail-closed.

GitHub reports PR28 has conflicts with main; TV3 owns safe branch synchronization, not TV2. Main now includes #19's plan at `524e4f3`; this task does not push main or merge any PR. Issue #2 remains OPEN/PARTIAL; only granular completed response/review work qualifies for a checklist tick.
