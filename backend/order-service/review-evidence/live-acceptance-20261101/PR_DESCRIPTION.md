## Issue
Refs #3, #10, #16.

## Behavior / source scope
Checkout commits its immutable business draft and Inventory RESERVE T1 before HTTP; verified T2 is generation-fenced. UNKNOWN stays quarantined across the adapter/generic caller and atomic PostgreSQL PENDING claims. No automatic recovery, new retry/resolution endpoint, implicit RELEASE/CONSUME policy or production flag is added.

Application tested at **`bf8eb3a027c72ad9fde81d0aa5dabc4e73bebacf`**, including main `72a12d7536d2644a0f2fbae6dd9e07928162cb1d` by normal merge. No rebase/force push. Runtime Order/Inventory/Auth/frontend/demo source is identical to the TV2-reviewed `593ac8d`; backend JARs rebuilt from tested HEAD. Shipping #138 and frontend #139 remain intact; V1–V4 unchanged, V5 additive.

## Live acceptance — TV2-approved isolated DEMO only
Review authorization: https://github.com/duyxl56785/Project_HK5/pull/145#pullrequestreview-5728410521 (TV2 COMMENT, not APPROVE).

**3 PASS / 0 FAIL / 2 NOT_RUN–BLOCKED; 3 executed, 0 skipped.**

| Case | Result | Exit |
|---|---|---:|
| Real Chromium: two Stores, one Order/two SellerOrders, exact stock deltas, three same-key replays without new Order/reserve | PASS | 0 |
| Claim-conflict UNKNOWN | NOT_RUN / BLOCKED: missing TV4/TV5 approved real fault fixture/identity/receipt | N/A |
| Timeout UNKNOWN | NOT_RUN / BLOCKED: missing approved real timeout/lost-response fixture/receipt | N/A |
| Customer cancel + Seller reject: exact held lines RELEASE once, repeated terminal action creates no operation | PASS | 0 |
| DISPATCHED: CONSUME once before delivery; delivery creates no additional CONSUME | PASS | 0 |

Original five TypeScript files/assertions from `feat/tv3-two-store-acceptance-review@ecdb03c3` were used unchanged, in a separate test worktree. Real Auth/roles/Gateway/Catalog/Cart/Order/Inventory/PostgreSQL 16.15 and Chromium 154/Playwright. No HappyDOM, HTTP fixture/mock, fake JWT, fault endpoint or SQL mutation was used. Dedicated Compose project `tv3-live-145-20261101`; previous TV3 demo volumes preserved, no shared DB/volume overwritten.

Actual case commands, Codex Terminal Linux:
```
node /tmp/tv3-live-acceptance-145-20261101/run-live.mjs two-store
node /tmp/tv3-live-acceptance-145-20261101/run-live.mjs release
node /tmp/tv3-live-acceptance-145-20261101/run-live.mjs consume
```
The credential-safe runner invokes each existing Playwright project/filter separately. Runtime config only supplies system Chromium/report paths. Official demo startup/readiness/API seed completed exit 0 with the two approved opt-ins, using prebuilt artifacts and cloud-only build/runtime adaptations documented in evidence. No flags outside this isolated demo were changed.

## Fresh regression after main synchronization
```
./gradlew :order-service:test :order-service:postgresIntegrationTest :cart-service:test :cart-service:postgresIntegrationTest :inventory-service:test :inventory-service:postgresIntegrationTest bootJar --rerun-tasks --no-daemon --console=plain
```
Exit 0: **329 PASS, 0 FAIL/SKIP** (Order 156+103 PG; Cart 11+13 PG; Inventory 27+19 PG), backend JAR build PASS. `npm run typecheck` PASS, exit 0. This new regression execution is separate from the old candidate 329 and the three live cases. Require fresh CI at published PR HEAD; do not reuse old CI SUCCESS.

## GitHub-accessible evidence
[Execution report, environment/commands/limitations](https://github.com/duyxl56785/Project_HK5/blob/feat/tv3-checkout-durable-draft-journal/backend/order-service/review-evidence/live-acceptance-20261101/README.md)
[Results, Order/SellerOrder/operation IDs and exact balances](https://github.com/duyxl56785/Project_HK5/blob/feat/tv3-checkout-durable-draft-journal/backend/order-service/review-evidence/live-acceptance-20261101/results.json)
[Sanitized before/after snapshots](https://github.com/duyxl56785/Project_HK5/blob/feat/tv3-checkout-durable-draft-journal/backend/order-service/review-evidence/live-acceptance-20261101/cases/)
[Readonly frozen facts: IDs/SKU/quantity/money/shipping/hash/outbox all match](https://github.com/duyxl56785/Project_HK5/blob/feat/tv3-checkout-durable-draft-journal/backend/order-service/review-evidence/live-acceptance-20261101/frozen-facts-readonly.json)
[SHA256](https://github.com/duyxl56785/Project_HK5/blob/feat/tv3-checkout-durable-draft-journal/backend/order-service/review-evidence/live-acceptance-20261101/SHA256SUMS)

Logs/XML/readonly snapshots are sanitized; no passwords/JWTs, shipping PII, idempotency keys, raw draft/outbox payloads or DB dumps. Historical V4→V5 rehearsal and prior RED/GREEN evidence remain preserved in the earlier evidence package.

## Remaining owner decisions
- TV4/TV5: supply approved real fault fixtures, original operation/customer/key/body/lines, active fault state and receipt/evidence SHA256 for the two UNKNOWN cases. Do not fabricate a positive result.
- TV4/TV5: independent source/schema/Inventory/Finance/UAT review remains required. This is demo acceptance, not runtime/merge authorization.
- TV2 observation: successful drafts currently remain stored (three after these tests); no cleanup/retention policy was silently added. Inventory response exposes balances, not echoed request quantity; exact live stock deltas are verified without inventing a DTO field.
- TV1: future recovery must use fresh current-authority/write-fence. Revoke races and broader Auth acceptance are not proved here.

Draft review scope only. No PR merge, production deployment, complete Checkout DONE claim or action/review/confirmation on PR #144.
