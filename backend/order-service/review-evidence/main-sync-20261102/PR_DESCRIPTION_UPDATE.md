## TV3 main sync — 2026-11-02

Merged main `c11044d693eedd9b1b1d7d12b19d7daa7f7781a4` normally into TV3 at `8aa7f045448c3e729a0dcb26cdc03e20d30f5d69`; zero conflicts, preserved a775773 Javadoc and Finance frontend #115. No backend source change from a775773. Tested tree `c4117c4a3a38553d886709507f219e0ac44f3c81`; subsequent evidence commit is documentation only.

Codex Terminal: Order 156 unit + 103 real PostgreSQL; Cart 11 + 13; Inventory 27 + 19 = **329 PASS, 0 FAIL, 0 SKIP** (included in full build). Full backend final report **1646 PASS / 0 FAIL or ERROR / 16 source-gated SKIP**, build exit 0. Frontend typecheck exit 0. Three earlier build attempts exit 1 (Auth disk, Auth copied HBA permissions, Kafka disk), recovered without changing source/assertions/flags; evidence preserves failures.

Evidence: [main-sync-20261102](backend/order-service/review-evidence/main-sync-20261102/README.md), including commands, final sanitized XML, failures and SHA256.

Historical **3 live PASS / 2 UNKNOWN BLOCKED** remain unchanged at bf8eb3a; not rerun at new merge. Approved demo's 15 running containers and mounts unchanged. UNKNOWN real faults still require TV4/TV5 fixtures/identity/receipts. No production rollout, automatic UNKNOWN retry/release, or merge authorization.
