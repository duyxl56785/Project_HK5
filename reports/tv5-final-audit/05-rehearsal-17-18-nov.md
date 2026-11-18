# Rehearsal and runtime evidence — 17/11/2026

| Module | Owner | Result | Evidence / limit |
|---|---|---|---|
| Local Docker | TV5 workstation | PARTIAL_ENV | At 07:54 local time Docker Engine 29.8.1 became available; retained `hk5-uat-tv5-20261115230000` has 6 running / 9 exited; 8 app containers cannot mount missing temporary secret files; PostgreSQL exited 255 |
| Backend main CI | TV3 CI | PASS — scoped | `main@879143a...`, run #44052552623: Order PG16 140/140; BUILD SUCCESSFUL in 6m46s |
| Frontend main CI | TV3 CI | PASS — scoped | Same run: 178/178, typecheck/build PASS |
| Browser E2E main CI | TV3 CI | PASS — scoped | Initial run 23/24 harness failure; corrected exact-main diagnostic #44054213307 24/24, zero skipped/flaky |
| Cart/Checkout COD | TV2 owner rehearsal | PASS — rehearsal | Issue #160 comment 6940668725: app `240d028...`, Playwright 1/1, root 1 + SellerOrders 2, replay 200, mismatch 409, RESERVE once |
| Seller isolation | TV2 owner rehearsal | PASS — rehearsal | Anonymous 401; Seller own order 200; cross-Store order 404 |
| Admin COD collection / settlement | TV4 + TV5 | NOT_RUN / BLOCKED | Not exercised in the rehearsal |
| Final frozen-SHA UAT | TV2 + TV5 | NOT_RUN | Await frozen SHA and final owner receipt |

TV2 used a new isolated volume and explicit demo approvals for that rehearsal only. Rehearsal screenshots remain private and are not submission media. `stackBuildSha=NOT_ATTESTED` in the owner receipt.

The old 15/11 Compose project was inspected read-only after Docker became available. Its two named volumes and network remain present; no restart, prune, volume deletion or database change was performed. It is not ready for UAT until TV2 restores the approved secret files and database environment or designates a clean runner.
