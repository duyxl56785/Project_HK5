## TV3 current-main integration verification — 02/11/2026

Main advanced to d446577 (#144 + #140 + #143 + #148); branch145 synchronized with normal merges, no rebase/force. PR147 advanced to cb576a6 and already addresses items-out-of-scope by returning SKU; no obsolete productId patch reapplied.

Isolated combined candidate 3dd5980f71ccce5b05ab518e39bc1e661f956c45, tree849d06e63bf935df7f88a38ea086700e4b48f8bf:356 focused Order/Cart/Inventory PASS; full backend1725PASS/0FAIL/16 existing source-gated SKIP, exit0;4 additional Cart ownership/real-lock PostgreSQL probes PASS; frontend typecheck0. Branch145 main-sync source separately rerun:338 focused PASS/0FAIL/SKIP, typecheck0. Totals are separate; focused cases included in full build, not added twice.

[Source patch and reproducible evidence](backend/order-service/review-evidence/final-integration-20261102/README.md): three-file hardening patch for latest combined147+145, exact sources/commands/JUnit/hash. SKU/quantity bound to immutable journal, unique verified Product mapping, missing/nontextual response SKU fails closed. Source147 remains for TV2 to accept/integrate; this branch update publishes main sync plus evidence.

No merge authorization, no automated UNKNOWN recovery/release/new IDs or production flags.15 existing demo containers preserved. Historical3 livePASS/2UNKNOWN NOT_RUN-BLOCKED unchanged; fresh Inventory/Gateway/browser acceptance at integrated HEAD not executed. TV4 authority/ownership and TV2/TV5 retention/grants/real-fault gates remain.
