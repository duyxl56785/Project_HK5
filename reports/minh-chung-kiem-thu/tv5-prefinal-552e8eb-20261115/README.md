# TV5 — PRE-FINAL rehearsal evidence (15/11/2026)

**Status: PRE-FINAL only. This is not final UAT and is not a release/acceptance approval.**

## Source and GitHub state

- Backend/frontend source tested: `main@552e8ebdf9b6f83fde65e71d297ca18bc73ff93b`.
- Current GitHub `main` at audit time: `b2bee1d13eef2d404bc79c917b36af9489983351`.
- The `552e8eb..b2bee1d` diff contains documentation, report drafts, and evidence files only; no backend or frontend source file changed. Tests below are evidence for the tested source tree, not a FINAL run at the future frozen SHA.
- PR #194 (`42a09be6112a2ef05ddf3105d051d135871d7003`) was APPROVED by `duyxl56785` and merged by the maintainer as `b2bee1d` at 2026-11-15T14:40:58Z. It is closed, with no reviewer request and zero inline review comments. No check runs were returned for the PR head or merge commit. Since #194 is merged, this receipt is a separate PRE-FINAL record and does not claim to update its code.
- On tested `main@552e8eb`, GitHub Actions readiness, frontend, and E2E checks were successful (runs 43702848981 and 43702848877). Current merge SHA `b2bee1d` has no check runs in the GitHub API at this audit time.
- PR #203 is open and clean at `29149baf`; it changes only the comment at the top of `docker/compose.demo.yml` to show the opt-in demo flags. Its defaults remain OFF. Its three CI checks are successful. No review request was present.
- Issues #162 and #18 remain OPEN. Issue #158 sets the main freeze for 19/11, final clean-stack E2E/install check for 20/11, and submission for 21/11. Do not tick final UAT or submission checkboxes from this preflight.

## Backend and frontend

- Environment: Windows 11, JDK 21.0.7, Gradle Wrapper 8.10.
- Gradle Wrapper JAR SHA-256: `2DB75C40782F5E8BA1FC278A5574BAB070ADCCB2D21CA5A6E5ED840888448046`; the JAR contains `org/gradle/wrapper/GradleWrapperMain.class`.
- Root cause of the earlier `ClassNotFoundException`: launching Java from the project path containing Vietnamese accented characters failed path/classpath handling on this Windows setup. Running from the same checkout via temporary drive `H:` worked. No Wrapper or project source change was needed.
- Command: `gradlew.bat clean build --rerun-tasks --no-daemon --max-workers=1 --console=plain`; exit 0, 97/97 tasks executed, duration 8m48s.
- Backend JUnit XML totals: 1,180 unit tests + 497 integration tests = 1,677 total; 1,661 passed, 16 skipped, 0 failed, 0 errors.
- An earlier parallel invocation had one `AuthPersistenceTest` PostgreSQL connection timeout. The test then passed alone, and the complete forced serial build also passed. The earlier timeout's exact cause was not proven; no source was changed to make it pass.
- Frontend at the same source SHA: `npm ci`, typecheck, production build, and 178 unit tests passed. `npm ci` reported one moderate advisory; dependencies were not changed to suppress it.
- Raw command logs are in `logs/backend/` and `logs/frontend/`. JUnit XML remains in each backend module's `build/test-results/{test,integrationTest}` directory in the tested checkout.
- Raw logs are preserved byte-for-byte; the folder `.gitattributes` disables only trailing-space/blank-EOF whitespace checks for captured tool output, so `git diff --check` can still validate the report text without rewriting logs.

## Isolated rehearsal snapshot

- Compose project: `hk5-uat-tv5-20261115230000`.
- 15 containers were running; Gateway published only at `127.0.0.1:18081`, frontend only at `127.0.0.1:5174`.
- Project-only resources: network `hk5-uat-tv5-20261115230000_default`; volumes `hk5-uat-tv5-20261115230000-pgdemo` and `hk5-uat-tv5-20261115230000-productimages`.
- Eight application databases each had `flyway_schema_history`. The public `postgres` and `hk5_demo` databases are not counted as application databases.
- Fake seed: 3 open Stores, 6 ACTIVE products, 5 accounts. A read-only check found 0 cart rows at snapshot time. No checkout/order/COD/settlement operation was attempted.
- Both UAT flags remained OFF: `INVENTORY_ORDER_PAIR_ENABLED=false`, `HK5_FINANCE_POLICY_APPROVED=false`.
- Smoke script exited 0 and confirmed database bootstrap/Flyway history, fake seed, and public Catalog reads. Read-only HTTP probes returned 200 for Gateway health, categories, Catalog products, and frontend root.
- The per-project database snapshot and product/store rows are in `rehearsal-before-uat.json`, `products-db-readonly.txt`, and `stores-db-readonly.txt`. No credentials or `.env` files are included.

## B.1–B.5 and Checkout 6.4

Canonical in-repository definitions: [`TV5_APPENDIX_B_CAPTURE_SHEET.md`](../../../docs/project-report/TV5_APPENDIX_B_CAPTURE_SHEET.md). The report's Figure 6.4 is described in [`tv3/CHECKOUT.md`](../../../docs/project-report/tv3/CHECKOUT.md) as a real cart/checkout screenshot containing products from two Stores. The repository's historical `Task6.4` in `tests/integration/README.md` is a separate regression task, not that screenshot requirement. No distinct functional specification named “Checkout 6.4” was found.

| Item | State | Evidence and limit |
|---|---|---|
| B.1 Catalog | PRE-FINAL partial capture | `screenshots/B1-catalog-pre-final.png` is a real browser capture of the running frontend with seeded Catalog products. The database/API were checked separately. The screenshot does not show SKU or a search/filter action and is not final B.1 acceptance. |
| B.2 two-Store cart | NOT RUN | Cart had no seeded rows; no customer session was used. |
| B.3 root Order + SellerOrders | BLOCKED / NOT RUN | No checkout was attempted; Inventory pair gate was OFF. |
| B.4 Seller isolation/Finance | BLOCKED / NOT RUN | Authenticated cross-Store checks and Finance acceptance were not run; Finance gate was OFF. |
| B.5 Admin moderation/COD | BLOCKED / NOT RUN | Admin moderation and COD were not run; Finance gate was OFF. |
| Checkout Figure 6.4 | BLOCKED / NOT RUN | No cart containing products from two Stores or completed Checkout was created. |
| COD / Settlement | BLOCKED / NOT RUN | No business authorization was provided to enable the Finance policy for UAT. |

No production/shared flags were changed. PR #203 documents a demo command with explicit opt-in flags but does not authorize their use for UAT. Final UAT needs owner-approved isolated runner/gates and the frozen main SHA described by Issues #158/#18. Keep this Compose rehearsal running until authorized evidence capture is complete; cleanup is **PENDING**.

## Evidence file integrity

- `SHA256SUMS.txt` records hashes for the included snapshot, raw logs, read-only data, and screenshot.
- `github-status.md` records the current PR/Issue audit summary.
- No code files were modified by this run.
