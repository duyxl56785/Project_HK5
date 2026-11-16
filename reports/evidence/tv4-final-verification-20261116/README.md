# TV4 final verification receipt — 16/11/2026

## Source and environment

- Backend source: `main@b2bee1d13eef2d404bc79c917b36af9489983351` (PR #194 merge). `b2bee1d..27f46aa` changes no backend path, so the backend receipts remain bound to that source.
- Latest main observed: `27f46aadf5e98b2f1ae1969bc7b09fa1f1c8ab0f`. It changes frontend/demo/docs; therefore frontend was rerun after this main was merged into the evidence branch at `dc96af09c5ad9db3776358b93667ecfde8fbf596`.
- Checkout: short-path worktree `C:\tv4audit`; no tracked source change before this receipt. The raw receipts record the later untracked evidence directory in each command header.
- Host: Windows, Java 21.0.10, Gradle Wrapper 8.10, Docker Desktop 29.9.1, Node 24.22.0 and npm 11.19.1.
- Testcontainers used disposable PostgreSQL containers only. No shared database, volume, feature flag, seed or demo stack was changed.
- Both `INVENTORY_ORDER_PAIR_ENABLED` and `HK5_FINANCE_POLICY_APPROVED` remained at their default `false` value.

## Executed verification

| Scope | Exact command | Result |
| --- | --- | --- |
| Inventory, Order and Notification unit regression | `backend\gradlew.bat -p C:\tv4audit\backend :inventory-service:test :order-service:test :notification-service:test --rerun-tasks --no-daemon --console=plain` | PASS: Inventory 27/27, Order 178/178, Notification 7/7; 0 failure/error/skip. |
| Inventory PostgreSQL/Flyway/provider candidate | `backend\gradlew.bat -p C:\tv4audit\backend :inventory-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain` | PASS: 38/38; 0 failure/error/skip. |
| Fulfillment, cancel/refusal release, dispatch consume and COD guards | `backend\gradlew.bat -p C:\tv4audit\backend :order-service:postgresIntegrationTest --tests '*FulfillmentPostgres16IT' --rerun-tasks --no-daemon --console=plain` | PASS: 24/24; 0 failure/error/skip. |
| Checkout-to-Inventory journal | `backend\gradlew.bat -p C:\tv4audit\backend :order-service:postgresIntegrationTest --tests '*CheckoutInventoryJournalPostgresIT' --rerun-tasks --no-daemon --console=plain` | PASS: 25/25; 0 failure/error/skip. |
| Exact operation retry/journal fence | `backend\gradlew.bat -p C:\tv4audit\backend :order-service:postgresIntegrationTest --tests '*InventoryOperationJournalPostgres16IT' --rerun-tasks --no-daemon --console=plain` | PASS: 14/14; 0 failure/error/skip. |
| Frontend unit/DOM regression | `cd frontend && npm test` | PASS: 178/178; 0 failure/cancel/skip. |
| Frontend typecheck | `cd frontend && npm run typecheck` | PASS, exit 0. |
| Frontend production build | `cd frontend && npm run build` | PASS, exit 0. |
| Frontend regression after latest main | `cd frontend && npm test`; `npm run typecheck`; `npm run build` | PASS: 178/178, then typecheck/build exit 0 at merge commit `dc96af0` containing `main@27f46aa`. |

The focused PostgreSQL classes are intentionally reported separately. Their 63 tests are not added to the historical or full-suite counts.

## Raw receipts and integrity

- `raw/*-console.log` preserves console output. Each begins with the checked-out SHA and `git status --short`; the matching `*-exit.txt` is the process exit code.
- `raw/junit/**/TEST-*.xml` is the original JUnit XML copied immediately after the corresponding successful command. `raw/junit-summary.json` is derived from those files and lists every class and count.
- `raw/combined-postgres-console.log` and `raw/combined-postgres-exit.txt` preserve the bounded combined run. Exit `124` means it was stopped at the 240-second bound; it is not an assertion failure and does not turn focused PASS into full-suite PASS.
- `raw/frontend-npm-audit.json` is the unmodified audit response. It reports one moderate direct dependency advisory: `vite@6.4.4`, [GHSA-8g5g-mxqh-grgj](https://github.com/advisories/GHSA-8g5g-mxqh-grgj). No dependency or source was changed.
- `raw/run-metadata.json` binds the source and flags; `SHA256SUMS.txt` hashes raw receipts only, never this README.
- `raw/run-metadata-main27.json` binds the fresh frontend run to merged `main@27f46aa`; the three matching `raw/frontend-main27-*` receipts retain its exact console and exit codes.

## Matrix and limits

| Area | Observed result | Acceptance boundary |
| --- | --- | --- |
| RESERVE / no negative stock / replay | PASS within Inventory module and PostgreSQL provider candidate evidence. | Not a positive service-pair Checkout E2E. |
| CONSUME at `DISPATCHED` | PASS in `FulfillmentPostgres16IT`; replay/journal regression also PASS. | Does not prove a real Gateway/Seller actor flow. |
| Cancel/refusal `RELEASE` | PASS in targeted Fulfillment PostgreSQL regression. | No claim of owner-approved cross-service recovery. |
| COD | PASS only for `DELIVERED`/duplicate guard in Fulfillment scope. | Finance settlement/snapshots remain TV5-owned and are not approved by this receipt. |
| Notification | PASS for persistence/retry/DLT unit/Kafka scope. | No two-Store production notification UAT claim. |
| Seller Return | Existing receipt/settlement guards remain unchanged. | Positive RET-05 restock is `BLOCKED`: no caller allowlist, durable restock tuple/receipt lookup, approved recovery or TV5 Finance sign-off. |
| UNKNOWN lost-response / claim-conflict | `BLOCKED_DESIGN` / live `NOT_RUN`. | No feature flag, fault injection, synthetic commit or provider mutation was used to manufacture a PASS. |

## Non-passing / not-run work

- The bounded combined `:inventory-service:postgresIntegrationTest :order-service:postgresIntegrationTest` rerun exceeded 240 seconds while Order/Testcontainers was shutting down and trying to reconnect to its ephemeral PostgreSQL port. Its raw exit is `124`, and it is recorded **BLOCKED (test-run hang)**, not PASS or assertion FAIL. The focused Order PostgreSQL commands above completed and are the only Order integration results claimed here.
- Final two-Store live E2E, positive Checkout-to-Inventory provider wire, caller authorization, full COD/Finance settlement, UNKNOWN recovery and RET-05 exactly-once restock are **NOT_RUN/BLOCKED** under the owner gates recorded in Issue #4, #11, #17, #156 and #161.
- Frontend dependencies were absent in the new checkout. `npm ci` restored the lockfile dependencies before the reported frontend run. `npm audit` reported the moderate direct `vite@6.4.4` advisory `GHSA-8g5g-mxqh-grgj`; no `npm audit fix`, dependency or source change was made.

## Issue / PR references

- TV4 scope: #4, #11, #17, #156, #161.
- Relevant merged work: [#69](https://github.com/duyxl56785/Project_HK5/pull/69), [#168](https://github.com/duyxl56785/Project_HK5/pull/168), [#182](https://github.com/duyxl56785/Project_HK5/pull/182), [#191](https://github.com/duyxl56785/Project_HK5/pull/191).
- Main checkpoint: [#194](https://github.com/duyxl56785/Project_HK5/pull/194).

## Disposition for TV5

**PARTIAL / READY_FOR_REVIEW, not final UAT accepted.** This receipt is evidence for the executed module, database and frontend scopes only. It must not be read as approval to enable either gate, as a live E2E result, or as proof of UNKNOWN/RET-05 recovery.
