# Blocker handoff — 18/11/2026

| Blocker | Owner | Waiting parties | Required action | Evidence required |
|---|---|---|---|---|
| PR #204 review corrections | TV5; TV2 reviewer | TV5 / release | Corrections are pushed at `cdd84618`; TV2 must re-review exact head and decide merge before 20/11 18:00 freeze (TV2's preferred review/push cutoff is 20/11 12:00) | Current head, blob-level 16/16 manifest, review result |
| Frozen SHA and clean clone | TV2 / TV1 | TV3, TV4, TV5 | Publish frozen main SHA and clean-clone plan | Exact SHA, clone receipt, CI URLs |
| Final two-Store UAT | TV2 with TV3 | TV5/report | Run approved isolated stack | Cart image, Order/2 SellerOrders receipt, idempotency, inventory and actor logs |
| Inventory lifecycle / RET-05 | TV4 with TV3 | TV5 | Confirm contract and run approved cases | RESERVE/CONSUME/RELEASE/INCREASE DB receipts and JUnit/logs |
| COD collection / settlement | TV4 + TV5 + TV2 | Report/Finance | Exercise Admin collect after DELIVERED and settlement only under approved scope | Actor, state, money snapshot, Store scope, duplicate/Return guard receipts |
| Finance business gate | TV2/business owner + TV5 | Release | Confirm demo policy/flags for final run | Written approval, fee/settlement receipt, SHA and hashes |
| Retained Docker stack incomplete | TV2 environment owner | TV5 | Restore missing temporary secret mounts/PostgreSQL or provide clean approved runner | Container state, secret mount presence without values, PostgreSQL and stack health |

Do not use the 17/11 rehearsal ZIP as report media. TV2 confirmed final figures are to be recaptured on frozen SHA.
