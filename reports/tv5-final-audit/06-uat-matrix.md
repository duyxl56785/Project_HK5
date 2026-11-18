# UAT matrix — 18/11/2026

| Test ID | Chức năng | Owner | Kết quả mong đợi | Evidence hiện có | Trạng thái final |
|---|---|---|---|---|---|
| U01 | Auth/RBAC/Gateway | TV2 | Actor and Store scope correct | Main CI; TV2 rehearsal anonymous/Seller checks | REHEARSAL_PASS; frozen NOT_RUN |
| U02 | Catalog/Admin approval | TV2 | Product/Store state and moderation correct | B.1 pre-final partial | PARTIAL; Figure 6.4 NOT_RUN |
| U03 | Cart two Store / Checkout COD | TV3, TV2 | One root Order, two SellerOrders | TV2 rehearsal 17/11 | REHEARSAL_PASS at `240d028`; frozen NOT_RUN |
| U04 | Idempotency / authorization | TV3, TV2 | Replay same Order; changed payload 409; cross-Store denied | TV2 rehearsal 17/11 | REHEARSAL_PASS; frozen NOT_RUN |
| U05 | Inventory lifecycle | TV4, TV3 | Reserve/consume/release correct and once | Reserve once rehearsal; TV4 PG receipt #207 | PARTIAL; final receipt pending |
| U06 | Admin COD collection | TV4, TV5 | DELIVERED, Admin collect once, duplicate denied | Source review only | NOT_RUN_FINAL |
| U07 | Finance snapshot / commission | TV5, TV2 | MD12 price snapshot, 5% HALF_UP, seller net | Decision docs and static review | BLOCKED final runtime/approval |
| U08 | Settlement / Store isolation | TV5, TV4 | COD paid, one settlement, correct Store, Return guard | Source and bounded TV4 PG tests | BLOCKED final acceptance |
| U09 | Return RET-01..04 | TV4 | Correct Store/actor transitions | PR #207 evidence | PARTIAL; final UAT pending |
| U10 | RET-05 restock exactly once | TV4, TV5 | Approved physical receipt and one INCREASE | No live receipt | BLOCKED_DESIGN / NOT_RUN |
| U11–U12 | UNKNOWN | TV2, TV3, TV4, TV5 | Approved tuple and provider reconciliation | Design notes only | BLOCKED_DESIGN |
| U13 | Clean clone on another machine | TV2 / TV1 | Exact frozen SHA starts and E2E passes | Same-machine historical receipts | NOT_RUN on another machine |
| U14 | Final report and deck | TV2, TV5 | Word/PPTX matches final evidence | TV3 source docs and rehearsal media | PARTIAL; final artifacts not accepted |

Only a completed case with evidence at the frozen SHA can be final PASS. CI and rehearsal results remain separately labelled.
