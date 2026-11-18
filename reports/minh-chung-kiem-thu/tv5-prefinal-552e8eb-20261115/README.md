# TV5 — PRE-FINAL rehearsal evidence (15/11/2026)

**Status: PRE-FINAL only. This is not final UAT and is not a release/acceptance approval.**

## Source and GitHub state

- Backend/frontend source tested: `main@552e8ebdf9b6f83fde65e71d297ca18bc73ff93b`, the merge commit of PR #202. PR #202 head was `058b910d8f7cd752bbd1b17296e8edcde693a2b8`.
- `552e8eb` is **not** PR #194's head. PR #194 head was `42a09be6112a2ef05ddf3105d051d135871d7003`; its merge commit was `b2bee1d13eef2d404bc79c917b36af9489983351`.
- Current GitHub `main` at this 18/11/2026 audit: `879143a1f081da4644ebc29c0950069429731f0f`. This receipt remains evidence for the earlier tested source `552e8eb`; it is not a test result for current `main` or the future frozen SHA.
- The `552e8eb..b2bee1d` diff contains documentation, report drafts, and evidence files only; no backend or frontend source file changed. Tests below are evidence for the tested source tree, not a FINAL run at the future frozen SHA.
- PR #194 (`42a09be6112a2ef05ddf3105d051d135871d7003`) was APPROVED by `duyxl56785` and merged by the maintainer as `b2bee1d` at 2026-11-15T14:40:58Z. It is closed. Since #194 is merged, this receipt is a separate PRE-FINAL record and does not claim to update its code.
- On tested `main@552e8eb`, GitHub Actions readiness, frontend, and E2E checks were successful (runs 43702848981 and 43702848877). Current merge SHA `b2bee1d` has no check runs in the GitHub API at this audit time.
- Issues #18, #158, #160 and #162 remain OPEN at this audit. The earlier 19/11 freeze, 20/11 final E2E and 21/11 submission schedule in older receipts conflicts with TV2's later 17/11 update on Issue #160: freeze at 18:00 20/11, final UAT 21/11 and submission by 23/11. Confirm the controlling schedule with TV2 before recording a release deadline.

## Backend and frontend

- Environment: Windows 11, JDK 21.0.7, Gradle Wrapper 8.10.
- Gradle Wrapper JAR SHA-256: `2DB75C40782F5E8BA1FC278A5574BAB070ADCCB2D21CA5A6E5ED840888448046`; the JAR contains `org/gradle/wrapper/GradleWrapperMain.class`.
- Root cause of the earlier `ClassNotFoundException`: launching Java from the project path containing Vietnamese accented characters failed path/classpath handling on this Windows setup. Running from the same checkout via temporary drive `H:` worked. No Wrapper or project source change was needed.
- Command: `gradlew.bat clean build --rerun-tasks --no-daemon --max-workers=1 --console=plain`; exit 0, 97/97 tasks executed, duration 8m48s.
- Backend JUnit XML totals recorded from the tested checkout: 1,180 unit tests + 497 integration tests = 1,677 total; 1,661 passed, 16 skipped, 0 failed, 0 errors. The XML files were not included in this receipt, so a reviewer cannot independently recompute the aggregate from this folder.
- An earlier parallel invocation had one `AuthPersistenceTest` PostgreSQL connection timeout. The test then passed alone, and the complete forced serial build also passed. The earlier timeout's exact cause was not proven; no source was changed to make it pass.
- Frontend at the same source SHA: `npm ci`, typecheck, production build, and 178 unit tests passed. `npm ci` reported one moderate advisory; dependencies were not changed to suppress it.
- Raw command logs are in `logs/backend/` and `logs/frontend/`. JUnit XML remains in each backend module's `build/test-results/{test,integrationTest}` directory in the tested checkout.
- The 15 raw log/capture payloads are preserved byte-for-byte: the receipt `.gitattributes` marks logs, JSON, text snapshots and screenshots as `-text -diff`. `test-summary.json` is a derived summary updated on 18/11 to add recorded run metadata and the official figure crosswalk; it is not an original byte-for-byte capture from 15/11. Its current committed bytes are versioned and hashed as the sixteenth manifest payload. `SHA256SUMS.txt` is explicitly `text eol=lf diff`, so reviewers can inspect it and compare it across platforms. Narrative Markdown and this README are excluded because they remain editable report text.

## Isolated rehearsal snapshot

- Compose project: `hk5-uat-tv5-20261115230000`.
- 15 containers were running; Gateway published only at `127.0.0.1:18081`, frontend only at `127.0.0.1:5174`.
- Project-only resources: network `hk5-uat-tv5-20261115230000_default`; volumes `hk5-uat-tv5-20261115230000-pgdemo` and `hk5-uat-tv5-20261115230000-productimages`.
- Eight application databases each had `flyway_schema_history`. The public `postgres` and `hk5_demo` databases are not counted as application databases.
- Fake seed: 3 open Stores, 6 ACTIVE products, 5 accounts. A read-only check found 0 cart rows at snapshot time. No checkout/order/COD/settlement operation was attempted.
- Both UAT flags remained OFF: `INVENTORY_ORDER_PAIR_ENABLED=false`, `HK5_FINANCE_POLICY_APPROVED=false`.
- Smoke script exited 0 and confirmed database bootstrap/Flyway history, fake seed, and public Catalog reads. Read-only HTTP probes returned 200 for Gateway health, categories, Catalog products, and frontend root.
- The per-project database snapshot and product/store rows are in `rehearsal-before-uat.json`, `products-db-readonly.txt`, and `stores-db-readonly.txt`. No credentials or `.env` files are included.

## B.1–B.5 and official Figures 6.4–6.6

The official submission mapping was fixed by TV2 in [Issue #162](https://github.com/duyxl56785/Project_HK5/issues/162#issuecomment-6907759709) and incorporated by merged [PR #210](https://github.com/duyxl56785/Project_HK5/pull/210): **Figure 6.4 = Admin approval/management of Sellers; Figure 6.5 = Cart with two Stores; Figure 6.6 = one root Order with two SellerOrders**. The 25/10 Word draft and earlier TV3 text that called Checkout Figure 6.4 are historical and superseded. The repository's historical `Task6.4` in `tests/integration/README.md` is a separate regression task.

| Item | State | Evidence and limit |
|---|---|---|
| B.1 Catalog | PRE-FINAL partial capture | `screenshots/B1-catalog-pre-final.png` is a real browser capture of the running frontend with seeded Catalog products. The database/API were checked separately. The screenshot does not show SKU or a search/filter action and is not final B.1 acceptance. |
| B.2 two-Store cart | NOT RUN | Cart had no seeded rows; no customer session was used. |
| B.3 root Order + SellerOrders | BLOCKED / NOT RUN | No checkout was attempted; Inventory pair gate was OFF. |
| B.4 Seller isolation/Finance | BLOCKED / NOT RUN | Authenticated cross-Store checks and Finance acceptance were not run; Finance gate was OFF. |
| B.5 Admin moderation/COD | BLOCKED / NOT RUN | Admin moderation and COD were not run; Finance gate was OFF. |
| Figure 6.4 Admin Seller approval | NOT RUN | This rehearsal did not capture Admin approval/management of a Seller. |
| Figure 6.5 two-Store Cart | BLOCKED / NOT RUN | No cart containing products from two Stores was created in this 15/11 rehearsal. A later TV2 rehearsal on 17/11 is separate evidence and is not part of this receipt. |
| Figure 6.6 root Order + two SellerOrders | BLOCKED / NOT RUN | No Checkout or Order was created in this 15/11 rehearsal. A later TV2 rehearsal on 17/11 is separate evidence and is not part of this receipt. |
| COD / Settlement | BLOCKED / NOT RUN | No business authorization was provided to enable the Finance policy for UAT. |

No production/shared flags were changed. A demo command with explicit opt-in flags does not itself authorize their use for UAT. Final UAT needs an owner-approved isolated runner/gates and the frozen main SHA described by Issues #158/#18. Cleanup of this historical rehearsal stack was recorded as **PENDING at capture time**; its current runtime state has not been rechecked.

## Evidence file integrity

- `SHA256SUMS.txt` records hashes for exactly 16 payloads: 15 raw logs/captured artifacts plus the derived `test-summary.json` updated on 18/11; it excludes itself, `.gitattributes` and editable Markdown.
- A separate TV5 working audit/handoff is retained locally outside this receipt PR and will be delivered separately after final UAT; it is not part of this historical capture receipt.
- No code files were modified by this run.
