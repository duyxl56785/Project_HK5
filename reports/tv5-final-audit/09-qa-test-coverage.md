# QA test coverage — 18/11/2026

| Test layer / module | Source SHA / evidence | Result | Scope limit |
|---|---|---|---|
| Backend Order PostgreSQL16 | `main@879143a...`, run #44052552623 | 140/140 PASS; BUILD SUCCESSFUL | CI readiness suite, not final UAT |
| Frontend unit/typecheck/build | `main@879143a...`, run #44052552623 | 178/178; typecheck/build PASS | Hosted CI only |
| Browser E2E | `main@879143a...`, diagnostic #44054213307 | 24/24 PASS, no skip/flaky | Disposable CI stack; first attempt 23/24 due harness project name |
| TV2 Windows Checkout rehearsal | app `240d028...`, Issue #160 comment 6940668725 | Playwright 1/1; verifier PASS; root 1 / SellerOrders 2; replay and RESERVE assertions pass | Owner rehearsal, not frozen SHA; raw ZIP private |
| TV4 unit / PostgreSQL receipt | PR #207 head `b4a33be...` | Inventory 27, Order 178, Notification 7; Inventory PG 38; Fulfillment 24; journal tests 39 PASS | Combined PostgreSQL attempt exit 124 BLOCKED; scoped receipt only |
| Voucher C1 seed tests | PR #209 head `8caf5b1...`, CI #43867495388 | Frontend/E2E SUCCESS; native seed tests reported 13/13 | Demo seed only; not Checkout voucher application |
| Admin COD collection | Source reviewed | NOT_RUN_FINAL | No final Admin collect receipt |
| Finance settlement | Source + helper tests reviewed | BLOCKED final runtime | No approved exact-SHA settlement proof |
| RET-05 / UNKNOWN | Owner design and tests only | BLOCKED_DESIGN / NOT_RUN | No live operation approval/provider receipt |

Keep each result attached to its own SHA and environment. Do not add these counts into one release total.
