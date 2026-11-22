# TV1 W6 — Independent clean-clone / live E2E evidence (22/11/2026)

**Issue:** [#158](https://github.com/duyxl56785/Project_HK5/issues/158). **Executor:** TV1 (`lexuanbao258-collab`). **Reviewer requested:** TV2 (`duyxl56785`) and TV5 for QA. **Frozen source:** [`eaf3bf270255b80db4525f836bfae7e495dc8651`](https://github.com/duyxl56785/Project_HK5/commit/eaf3bf270255b80db4525f836bfae7e495dc8651).

**Scope:** Clone độc lập trên **cùng máy Windows** (không phải máy thứ hai), chạy Docker stack riêng dùng dữ liệu demo giả và Playwright kết nối live qua Gateway. Không thay đổi source, schema, contract, runtime policy hoặc production; không merge/deploy/approve.

## Reproducible environment

| Field | Measured value |
| --- | --- |
| Host | Windows 11 Home Single Language; Git 2.45.1.windows.1, Java 21.0.11, Node 24.19.0, Docker Engine/Client 29.7.2, Compose v5.4.0 |
| Fresh checkout | `D:\CMC\HK5-TV1-Independent-20261122`; full HEAD `eaf3bf270255b80db4525f836bfae7e495dc8651`; `git status --porcelain` returned **empty** after runs |
| First isolated stack | `hk5-tv1-independent-20261122`, subnet `10.254.150.0/24`; data volumes kept, stack stopped separately |
| Clean E2E pass stack | `hk5-promo-test-tv1-20261122`, subnet `10.254.151.0/24`, fresh database and seed; fulfills project-label guard in `e2e/promotion-pricing.spec.ts` |
| Local demo gates during final E2E | `INVENTORY_ORDER_PAIR_ENABLED=true`, `HK5_FINANCE_POLICY_APPROVED=true`, `HK5_VOUCHER_C2_ENABLED=false` (explicit **local-only opt-in**, not production approval) |
| Final command | `npx playwright test --reporter=line,junit --output <private-evidence-dir>/playwright-promo-c2-off-artifacts` (from `frontend/`) |
| Run time (UTC) | 2026-11-22 **15:00:38 → 15:03:16** |
| Final result | **24 PASS / 0 FAIL / 1 SKIP** of 25 tests, process exit **0**; 1 SKIP is `voucher-c2.spec.ts` because its local C2 guard requires a separately approved C2-only QA project/port |

Test-by-test JUnit-derived result (not an independently executed second run): [`TV1_W6_E2E_C2_OFF_CASES_20261122.tsv`](TV1_W6_E2E_C2_OFF_CASES_20261122.tsv).

## Previous attempts preserved (do not relabel)

1. First attempted subnet `172.30.254.0/24` failed to start Docker networking: an **existing OpenTrix network has `172.30.0.0/16`**, overlapping this /24. **Only the isolated Soopi subnet was changed**; no OpenTrix network or volume was removed.
2. First cold startup on clean clone passed backend/Windows build, but `up.mjs` exceeded its four-minute readiness deadline while several Java services required around 205–230 seconds to start. After they stabilized, direct repeat of **all seven gateway probes passed** (public API 200, authenticated routes 401 as expected), exit **0**. Manual `scripts/demo/seed.mjs` completed **exit 0** on the same isolated stack. Cold-start timeout is retained; not called initial full-stack PASS.
3. Login/access suite on the first stack (all gates OFF) returned **12 PASS / 0 FAIL**, exit 0.
4. First full suite on that stack with Inventory/Finance ON, C2 OFF: **22 PASS / 2 FAIL / 1 SKIP**, exit 1:
   - `access.spec.ts:95`: test expected disabled cart button during session restoration, but observed enabled; rerun of that test three times in isolation **3/3 PASS**, likely timing-sensitive, not established deterministic application failure.
   - `promotion-pricing.spec.ts:42`: explicit project-name assertion accepted only `hk5-demo` or `hk5-promo-test-*` while the first isolated project was named `hk5-tv1-independent-20261122`; a **harness/name mismatch**, not demonstrated incorrect pricing. No assertion or source edited.
5. The second isolated stack `hk5-promo-test-tv1-20261122` was built from the **same clean frozen SHA** with the existing demo script, fresh volumes and approved local pair/finance flags. Full suite then **24 PASS / 0 FAIL / 1 SKIP**, exit 0, including the two previously failing cases.

## Evidence integrity — original files on the Windows machine

Original logs and JUnit XML remain outside the git worktree in **`D:\CMC\HK5-TV1-Independent-20261122-evidence-private`** and **were not uploaded to GitHub**. Their SHA-256 values (calculated from actual file bytes) are:

| Original file | SHA-256 |
| --- | --- |
| `readiness-default-off.json` | `A3BD9CF303B8017E2B8C556059DFC6AAE4641C8C28B63E033CB1A66C3634AFDE` |
| `readiness-pair-finance-on.json` | `E31F75FDDC8EDA16CB134B53EE8C3BDA1CCCB9CBDBDAAFFD5A350F9EBAFE2FB7` |
| `access-e2e-off.raw.log` | `67C357A48EC59C26E3B70E7E290929A8A5926C7D760A0DC212692F2EE810D154` |
| `e2e-c2-off-pair-finance-on.raw.log` (previous FAIL retained) | `64929059C109E641415BEE18D6A560FC3FBE26E9E0227DA04CD807C8FD0D6962` |
| `access-session-restore-retest-repeat3.raw.log` | `53CA89F8199B5D6EE2E87C4BD78CC6BCCDCC8CA4458EA5B8328D56EF3A07CC83` |
| `e2e-promo-c2-off-full.raw.log` (final PASS) | `75A6EBF4F7585FA958B11CC1ADD12880EB163C1474EC2501D6E861245E92B531` |
| `e2e-promo-c2-off-junit.xml` | `7734616A02AA91DCFD5D4561C079E41187DB1E1E952F023B179FF030914FA379` |

The final XML was parsed locally: **25 testcases, 0 failure, 1 skipped**. A check against common secret header/key patterns found no matches in final console log and XML. Do not treat the derived TSV as byte-identical raw evidence; original raw files are retained separately. Logs were not fabricated or rewritten to suppress failures.

## Acceptance boundaries and remaining work

- **DONE / independently evidenced:** clean Git checkout on the **same Windows machine**, local isolated build/start after troubleshooting, healthy seven-path Gateway probe, seed, 12/12 access E2E, final **24/24 runnable E2E** with C2 explicitly OFF on fresh project, exit 0. CI for the distinct [security regression PR #225](https://github.com/duyxl56785/Project_HK5/pull/225) completed SUCCESS; not evidence of these local tests.
- **NOT_RUN / still required for Issue #158:** clone/install and operational confirmation on an **actual second physical/virtual machine**; no claim that a second clone on the same machine meets the criterion.
- **NOT_RUN in this local run:** Voucher C2 ON requires a specially isolated QA project `hk5-c2-qa-YYYYMMDD`, loopback port 25173 and explicit `E2E_VOUCHER_C2=1`. This run did not set those; **C2 OFF is 1 intentional SKIP**. Historical GitHub CI C2 ON is separate evidence.
- **Not within #158:** UAT-16 claim-conflict, UAT-17 lost-response/UNKNOWN and RET-05 remain distinct blocked cases per TV3/TV4/TV5; this E2E does not unblock them.
- TV2/TV5 to review this report and source SHA; do not close #158 or tick the *máy khác* checkbox without independent-device evidence. Original local raw logs can be reviewed privately before any public attachment.

**No production secrets, credential values, personal data, files from other Docker projects, or other participants' state were modified by this report.**
