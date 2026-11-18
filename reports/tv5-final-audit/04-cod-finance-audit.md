# COD and Finance audit — current source `main@879143a...`

| Area | Status | Evidence / limit |
|---|---|---|
| MD04 / MD12 | DOCUMENTED | Ordinary Store fee 5%, HALF_UP per SellerOrder; promotion discount borne by Seller before Checkout snapshot |
| Checkout fee implementation | STATIC_REVIEWED / GATED | `CheckoutService.prepareOrder` calls `MoneyRules.fee`; config defaults `policy-approved=false` and requires explicit rate/scale/House exemption when enabled |
| Money snapshot | STATIC_REVIEWED | `SellerOrder` stores `gross` and `platform_fee`; `sellerNet` is derived; `FinanceReport` aggregates stored amounts for delivered orders |
| COD checkout | REHEARSAL_PASS | TV2 17/11 rehearsal at `240d028...`: one root COD Order, two SellerOrders, replay 200, shipping mismatch 409, RESERVE once |
| Admin COD collection | NOT_RUN_FINAL | Source requires ADMIN + DELIVERED and allows one PENDING→COLLECTED; rehearsal did not call Admin collection |
| Settlement | BLOCKED — FINAL RUNTIME | Source requires DELIVERED + COD COLLECTED + not settled; active Return blocks settlement under row lock; no final approved runtime receipt |
| Finance report/dashboard | NOT_RUN_FINAL | No frozen-SHA report/API amount reconciliation |
| Voucher C2 | OUT OF SCOPE | MD13–MD25 are future C2 policy, not 21/11 submission functionality |

Pure `FinanceRules` tests do not prove production fee configuration, authenticated Store scope, DB uniqueness or concurrent settlement. Keep Finance gate OFF outside an explicitly approved disposable run. No financial values are fabricated.
