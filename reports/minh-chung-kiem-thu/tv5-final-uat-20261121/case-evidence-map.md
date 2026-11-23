# Case → evidence → owner map, Final UAT W5

Checkpoint 22/11/2026: status dưới đây đã được đồng bộ từ `run-manifest.json` và 37 case manifests trong PR #221 trên Frozen SHA `eaf3bf270255b80db4525f836bfae7e495dc8651`. TV5 đã xác minh 98/98 checksum và metadata artifact; không sửa raw receipt. RET-05 restock là blocker riêng, không phải case UAT-12 return non-restock.

| Test ID | Evidence tối thiểu cần có | Owner gửi evidence | Status hiện tại |
|---|---|---|---|
| UAT-01 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-01/ | HTTP 401/403, auth/audit logs, actor-role matrix, DB before/after nếu có side effect | TV2; TV5 witness | PASS |
| UAT-02 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-02/ | Catalog API JSON, Product/Store/Seller IDs giả, Catalog DB read-only receipt, rejected invalid product | TV2 + TV3 | PASS |
| UAT-03 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-03/ | Cart API request/response, screenshot hai Store, cart snapshot/subtotals | TV3 | PASS |
| UAT-04 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-04/ | Quote + Checkout HTTP JSON, fake Order ID, COD method/state, audit; no payment capture | TV3 + TV4 | PASS |
| UAT-05 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-05/ | Parent Order JSON, 2 SellerOrder IDs, DB read-only rows, money total comparison | TV3; TV5 money check | PASS |
| UAT-06 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-06/ | Original/replay HTTP responses, key fingerprint, Order count, Inventory operation count | TV3 + TV4 | PASS |
| UAT-07 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-07/ | Same-key/different-payload HTTP 409, Order/Inventory before-after | TV3 + TV4 | PASS |
| UAT-08 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-08/ | Quote + changed shipping request, HTTP 409, Order snapshot and Inventory unchanged | TV3 + TV4 | PASS |
| UAT-09 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-09/ | RESERVE operation/tuple, provider receipt, stock before-after, replay correlation | TV4 + TV3 | PASS |
| UAT-10 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-10/ | State transition receipts, CONSUME/RELEASE operation IDs, stock before-after | TV4 + TV3 | PASS |
| UAT-11 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-11/ | Seller A/B positive and cross-Store negative HTTP responses, actor/store IDs, audit | TV4 + TV2 | PASS |
| UAT-12 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-12/ | Return API/state, SellerOrder gross snapshot, refund calculation, Inventory receipt only if separately authorized | TV4 + TV3; TV5 money check | PASS — Return non-restock; RET-05 restock remains BLOCKED, not included |
| UAT-13 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-13/ | COD collection HTTP receipt, audit actor/time/amount, duplicate rejection, SellerOrder state | TV4; TV5 amount check | PASS |
| UAT-14 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-14/ | M01 policy reference, Store/platform report JSON, DB snapshot, gross/fee/net calculation sheet | TV5 + TV4; TV2 confirms local runtime policy | PASS |
| UAT-15 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-15/ | Admin/Seller/Customer role requests, moderation/Seller approval screenshot, audit actor | TV2 + TV4 | PASS |
| UAT-16 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-16/ | Approved runner, immutable claim tuple, provider oracle/receipt, before-after and authorization | TV2 + TV3 + TV4; TV5 witness | BLOCKED_DESIGN / NOT RUN |
| UAT-17 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/UAT-17/ | Reviewed fault runner, same tuple/key/operation, provider COMMIT receipt, Cart/Order/stock before-after, authorization | TV2 + TV3 + TV4; TV5 witness | BLOCKED_OWNER / NOT RUN |
| C1-01 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C1-01/ | Voucher create HTTP request/response, JWT-derived Store ownership, DB row | TV3 + TV2 | PASS |
| C1-02 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C1-02/ | Validation request/response matrix for code/discount/limits/dates | TV3 | PASS |
| C1-03 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C1-03/ | DRAFT edit/publish/pause/resume/cancel API, state history and actor audit | TV3 | PASS |
| C1-04 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C1-04/ | 20 active quota rows, 21st publish 409 `VOUCHER_QUOTA_EXCEEDED`, DB state | TV3 + TV2 | PASS |
| C1-05 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C1-05/ | Limit request/replay/mismatch, Admin approve/reject, wrong-role/self-moderation response | TV3 + TV2 | PASS |
| PROMO-01 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/PROMO-01/ | Campaign create/review/approve/reject API, audit and state | TV2 | PASS |
| PROMO-02 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/PROMO-02/ | Flash/Super Sale Product/Catalog/Cart price facts, Seller reduction source | TV2 | PARTIAL — Super Sale in-window PASS; Flash Sale in-window NOT_RUN |
| PROMO-03 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/PROMO-03/ | Clearance campaign/Product approval, Catalog/Checkout snapshot, stock receipt if affected | TV2 + TV3 + TV4 | PASS |
| PROMO-04 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/PROMO-04/ | C2 ON: quote 409 `VOUCHER_PROMOTION_CONFLICT`, no Order/redemption; C2 OFF: N/A | TV2 + TV3 | PASS — C2 ON branch |
| C2-01 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-01/ | TV2 runtime flag receipt, capability ON/OFF HTTP JSON, OFF rejection `VOUCHER_C2_DISABLED` | TV2 + TV3 | PASS — C2 ON local UAT; OFF evidence is separate CI |
| C2-02 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-02/ | Two Store voucher mappings, quote response, duplicate/mismatch error, ledger | TV3; TV5 money check | PASS |
| C2-03 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-03/ | FIXED voucher config, minOrder boundary quote and `VOUCHER_MIN_ORDER_AMOUNT` response | TV3; TV5 money check | PASS |
| C2-04 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-04/ | PERCENT quote JSON, HALF_UP calculation sheet, cap boundary | TV3 + TV5 | PASS |
| C2-05 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-05/ | Store A/B quote, Parent/Child Order snapshots, gross/fee/net calculation | TV3 + TV5; TV2 local policy receipt | PASS |
| C2-06 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-06/ | Owner-approved failure point, HTTP result, voucher ledger + Order/Inventory before-after | TV3 + TV4; TV5 witness | PASS |
| C2-07 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-07/ | Cancel/reject state history, redemption ledger before-after, replay correlation | TV4 + TV3; TV5 witness | PASS |
| C2-08 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-08/ | Return response, voucher snapshot, refundAmount = gross, Return state | TV4 + TV3 + TV5 | PASS |
| C2-09 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-09/ | Migration V10, reviewed PostgreSQL regression XML/log, SQLSTATE and DB identity | TV3; TV5 invariant check | PASS |
| C2-10 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-10/ | Store/platform report JSON, voucherDiscount separated from fee, SellerOrder snapshots | TV5 + TV4 | PASS |
| C2-11 | reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/C2-11/ | FIXED quote at/above zero-gross boundary, `VOUCHER_GROSS_TOO_LOW`, no Order/ledger side effect | TV3 + TV5 | PASS |

## Report figures 6.1–6.8

| Figure | Required capture | Owner | Status |
|---|---|---|---|
| 6.1 | Homepage banner/category/product on frozen SHA | TV2 | NOT RUN |
| 6.2 | Homepage 390px mobile viewport | TV2 | NOT RUN |
| 6.3 | Seller product management | TV2 | NOT RUN |
| 6.4 | Admin Seller approval/moderation, not Checkout | TV2 | NOT RUN |
| 6.5 | Real Cart with exactly two Stores | TV3 | NOT RUN |
| 6.6 | One Parent Order and two distinct SellerOrders | TV3 | NOT RUN |
| 6.7 | Seller isolation for each Store | TV4 + TV2 | NOT RUN |
| 6.8 | Finance/COD reconciliation with caption “đã ghi nhận đối soát” | TV4 + TV5 | NOT RUN |
