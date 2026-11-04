# Demo readiness — 04/11/2026

Refs #18, #154, #155, #156, #157. **NOT_DEMO_READY**; final UAT remains NOT_RUN/BLOCKED. This checkpoint supplements the TV5 acceptance matrix and does not approve execution of fault fixtures.

## Main and merge gates

Main: `3b4cf7d6909ed19db209bf0eb3fb63d092352cdb`, tree `a5e524b3eb2260d0c31a9aee0ae3af312a937402`. Local main was fast-forwarded; existing untracked `.claude/` was preserved. No reset/force push, automatic approval or reviewer-thread resolution.

| PR | Verified HEAD | Disposition / remaining gate |
| --- | --- | --- |
| [#69](https://github.com/duyxl56785/Project_HK5/pull/69) | `f2d56d7` | MERGED `3b4cf7d`; independent TV3 APPROVED at this HEAD, hosted readiness/frontend/e2e SUCCESS, no open threads. Java-only candidate; V3 stays outside default Flyway. No Inventory HTTP/wire/policy promotion. |
| [#168](https://github.com/duyxl56785/Project_HK5/pull/168) | `d290559` | MERGED `6872162`; TV3/TV5 APPROVED at this HEAD, hosted checks SUCCESS, no open threads. Notification persistence/retry fix only. |
| [#166](https://github.com/duyxl56785/Project_HK5/pull/166) / [#167](https://github.com/duyxl56785/Project_HK5/pull/167) | `7172398` / `f37dfdb` | #167 already merged into #166. CI SUCCESS; TV5 verified identical tested tree but submitted COMMENT, explicitly not APPROVE. WAITING_HUMAN_REVIEW before main. Existing request/comment retained without duplicate reminders. |
| [#171](https://github.com/duyxl56785/Project_HK5/pull/171) | `60b3def` | CI SUCCESS; TV5 independently reports six PostgreSQL PASS and default-OFF fix verified. Formal CHANGES_REQUESTED still present; reviewer must update it. No purge run on demo/shared DB. |
| [#173](https://github.com/duyxl56785/Project_HK5/pull/173) | `7ce40eb` | CI SUCCESS, no formal approval; patch targets #171 and duplicates its independently fixed default-on cause. Do not integrate both implementations independently; TV4/TV3/TV5 must agree disposition. |
| [#172](https://github.com/duyxl56785/Project_HK5/pull/172) | `cff27fb` | Frontend/e2e SUCCESS; REVIEW_REQUIRED. No automatic approval. |
| [#165](https://github.com/duyxl56785/Project_HK5/pull/165) | `4a0adc0` | CI SUCCESS; REVIEW_REQUIRED. TV5 evidence/matrix PR, not final UAT acceptance. |
| [#169](https://github.com/duyxl56785/Project_HK5/pull/169) | `436358a` | Draft, frontend/e2e SUCCESS, REVIEW_REQUIRED; Inventory UI candidate. |
| [#170](https://github.com/duyxl56785/Project_HK5/pull/170) | `f90754d` | Draft, no hosted check reported; four author-reported loopback harness tests are mechanics evidence only. No live fault execution approval. |
| [#152](https://github.com/duyxl56785/Project_HK5/pull/152) | `fb1732e` | REVIEW_REQUIRED; retention/UNKNOWN draft is not an execution permit. |

There were ten open PRs at preflight, eight after the two verified merges. `protect-main` requires one approval and resolved threads; normal merge commands with exact expected HEAD were used, without administrative bypass. Main CI runs [41772246477](https://github.com/duyxl56785/Project_HK5/actions/runs/41772246477) and [41772246436](https://github.com/duyxl56785/Project_HK5/actions/runs/41772246436) were IN_PROGRESS at this checkpoint, not claimed PASS.

## Executed checks and evidence

Integration rehearsal commit `ce54c221b7a7b8325752d32b50a0ca497aa3932c` has exactly the same tree as the resulting main above. This is source-tree equivalence evidence, not a full Docker UAT run on the main commit.

| Check | PASS | FAIL | SKIPPED | Scope |
| --- | ---: | ---: | ---: | --- |
| Inventory unit | 27 | 0 | 0 | Fresh execution, cache disabled |
| Inventory PostgreSQL | 38 | 0 | 0 | Fresh PostgreSQL Testcontainers; candidate/default-schema coverage |
| Notification focused | 7 | 0 | 0 | Four unit + three PostgreSQL/Kafka cases; fresh test task |
| Full Docker UAT / both UNKNOWN cases | — | — | — | NOT_RUN/BLOCKED; no fault injection or shared demo writes |

Java 21 / Gradle 8.10 / Docker 29.9.1. Both commands exited 0:

```text
backend/gradlew.bat -p backend :inventory-service:test :inventory-service:postgresIntegrationTest :notification-service:test --tests '*NotificationPersistenceTest' --tests '*OrderEventKafkaIT' --no-daemon --console=plain
backend/gradlew.bat -p backend :inventory-service:test :inventory-service:postgresIntegrationTest --rerun-tasks --no-build-cache --no-daemon --console=plain
```

The first command restored Inventory test results from cache; those are not counted as a fresh run. The second actually executed all five tasks. Total unique executed cases: **72 PASS / 0 FAIL / 0 SKIPPED**. Raw log/XML copies are retained in the session evidence directory `demo-readiness-20261104`, without rewriting bytes. Log SHA256: initial integration `ec8ce561aa12f56adad6266002f30f2abdff67cf06136904b9bff9077e47c4b5`; fresh Inventory `06555733aaca817a812516f2f14cb5bfc8363e74d4a723416195ce0d4b69ff8f`.

## Exact remaining UAT blockers

- **TV5:** formal review of #166 and replacement of the existing #171 CHANGES_REQUESTED after its current-head verification. Do not reinterpret COMMENT as approval.
- **TV2 + TV4:** separately reviewed UNKNOWN runner/Compose overlay. The existing runner accepts only `hk5-uat-*` and enforces Inventory pair OFF; the agreed plan uses `hk5-unknown-<timestamp>`, loopback ports 18082/5175 and an Inventory-network sidecar with pair opt-in confined to that project. Do not weaken or repurpose the existing safe runner in place.
- **TV3 + TV4 + TV5:** approve immutable attempt/key/body/operation/Store/SellerOrder tuple or an explicit narrow T1 waiver, provider canonical fingerprint/committed stock receipt, two-retry/quarantine oracle, fixture SHA and case-specific QA execution permission. Proxy byte hash and an HTTP timeout do not establish a provider outcome. Approval to prepare mechanics is not permission to run them.
- **Claim-conflict:** BLOCKED_DESIGN; no genuine contention generator has been accepted. No fake 409, production monkey-patch, row deletion, new operation ID or blind RELEASE/CONSUME.

Next sequence: receive independent review decisions, finish/review the isolated runner and fixture oracles, then run the approved full React→Gateway→backend→PostgreSQL UAT on the exact integrated main SHA and retain receipts. Until then Issue #18 remains OPEN/PARTIAL; no production gates, retention jobs or shared-stack fault tests are enabled by these merges.

## Follow-up: isolated configuration preparation

Main CI runs 41772246477 and 41772246436 subsequently completed SUCCESS; this does not include the two gated UNKNOWN acceptance cases. [Draft #175](https://github.com/duyxl56785/Project_HK5/pull/175), commit `4ca06352368f2cf610bde5fc1dd3cc9886e08c28`, adds four preparation files on #170 without modifying the helper or existing UAT scripts. **11 Node tests PASS / 0 FAIL / 0 SKIPPED**, exit 0, including the four fake loopback harness cases. Docker Compose config-only verification at that committed source passed isolated volume/secret bindings, two loopback ports, pair OFF, original Order transport and proxy network namespace; no containers were started.

Prepared plans always deny execution. Static review identified and corrected a checkout-path boundary defect, with regression coverage for `..runtime` and the primary checkout's shared-secret tree. TV3/TV4/TV5 review has been requested on #175; no hosted checks were reported there at publication. This is **not** the approved execution/teardown runner, live lost-response or provider COMMIT evidence. Exact tuple/oracles, independent case approval, claim-conflict design and final UAT remain BLOCKED/NOT_RUN. No aggregate checklist is completed by these preparation tests.
