## TV3 negative Checkout live API acceptance — 2026-11-02

Source baseline `99979a75`; unchanged approved disposable stack application `bf8eb3a` (only OrderEvents Javadoc differs in Order/Cart/Inventory source). New test/config hashes recorded in evidence; no redeploy/full backend rebuild.

**6 executed, 6 PASS, 0 FAIL, 0 SKIP; Playwright exit0**: same-key changed body; same-key changed shipping; expectedItems/Cart mismatch; current ACTIVE Catalog price changed after Cart; insufficient second-Store stock; inactive Product after Cart. All HTTP409, no additional Order/SellerOrder, no stock change during rejection. Preflight adds no Inventory operation/journal; insufficient-stock journal is definitive REJECTED and healthy Store stock rolls back.

Setup creates only two new synthetic Products and one disclosed baseline Order/two SellerOrders; existing seed Products untouched.15 demo containers and mount sets/lifecycle preserved. Outer local wrapper exit1 due positional Mounts array order; saved snapshots reverified equal after normalization. Actual acceptance assertions/reporters remain PASS.

[Evidence](backend/order-service/review-evidence/negative-checkout-20261102/README.md): individual HTTP/error/revision/count/stock/operation snapshots, raw JUnit/Playwright reports, commands/exits and SHA256. These are real Gateway HTTP+PostgreSQL API acceptance, not browser E2E.

Original **3 live PASS / 2 UNKNOWN NOT_RUN-BLOCKED** unchanged. The six new negative cases do not close claim-conflict/timeout gates; real owner fault fixtures/identity/receipts still required from TV4/TV5. No automatic UNKNOWN retry/release, production flags, merge or full Checkout DONE claim.
