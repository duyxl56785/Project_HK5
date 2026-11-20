# TV5 — Final UAT execution checklist, 21/11/2026

**Repository:** `duyxl56785/Project_HK5`
**Vai trò:** TV5 QA Lead
**Nguồn test:** `TV5_FINAL_UAT_W5_C2_MATRIX_20261119.md`
**Tổng số case:** 37 (17 baseline + 5 Voucher C1 + 4 Promotion + 11 Voucher C2)
**Trạng thái trước khi chạy:** `NOT RUN`; các case có gate/contract chưa đạt được ghi `BLOCKED`, không suy thành PASS.

## 0. Quy tắc bắt buộc

1. Chỉ chạy sau khi TV2 công bố bằng văn bản full 40-character frozen `main` SHA, thời điểm freeze, stack/project riêng, flags được phép và người vận hành. Ghi đúng SHA thực tế trên mọi receipt.
2. C2 mặc định **OFF**. Không thêm `--approved-voucher-c2` hoặc tự sửa `HK5_VOUCHER_C2_ENABLED`. M01 MD01–MD11 đã được TV2 phê duyệt cho bản nộp và demo local; production chưa được phê duyệt. `HK5_FINANCE_POLICY_APPROVED` vẫn mặc định OFF và chỉ TV2 được opt-in trong `.env.demo` local theo đúng M01. TV5 không tự bật bất kỳ flag nào.
3. PR #212 đã merge tại `4b5bcb4ceb5a6a636e4c943db2d41c8a4c1bb14f`; PR #215 decision records và PR #218 Vite 6.4.5 đã merge. Main hiện tại là `950279e25df2b658c39acfc3a3c37640bb8e6cac`. Frontend/E2E [run 44700827386](https://github.com/duyxl56785/Project_HK5/actions/runs/44700827386) SUCCESS đúng SHA: frontend, E2E C2 OFF và E2E C2 ON đều SUCCESS. Backend readiness gần nhất [run 44493061575](https://github.com/duyxl56785/Project_HK5/actions/runs/44493061575) gắn `cee7c87`; diff từ đó đến main hiện tại không đổi `backend/**`, `docker/**` hoặc workflow backend-readiness. Đây là CI, **không phải Final UAT** và chưa phải xác nhận Frozen SHA; TV2 phải xác nhận CI applicability cho bản freeze.
4. Mọi giao dịch dùng actor và dữ liệu giả. Không dùng tiền thật, không tuyên bố chuyển tiền thật, không tự phát fault injection UNKNOWN, không tạo/điều chỉnh tồn kho ngoài fixture đã được owner duyệt.
5. PASS/FAIL chỉ sau thao tác thực tế và có raw evidence. Chưa chạy ghi `NOT RUN`; thiếu điều kiện/owner/contract ghi `BLOCKED` kèm lý do. `N/A` chỉ dùng ở nhánh cấu hình mà ma trận gốc ghi rõ không áp dụng, không đổi thành PASS.
6. Raw logs/JSON/JUnit/DB receipts giữ nguyên byte. Chỉ tạo bản redacted để chia sẻ. Tuyệt đối không lưu password, token, JWT, `.env`, dữ liệu cá nhân hoặc secret trong evidence/PR.
7. Ảnh UI không thay thế HTTP/API, DB, Inventory, COD hoặc Finance oracle. CI PASS không thay thế vận hành trên frozen SHA.

## 1. Hồ sơ chung cho mỗi lượt chạy

Điền một hồ sơ run trước khi thực hiện case đầu tiên:

| Trường | Giá trị thực tế |
|---|---|
| Frozen `main` SHA (40 ký tự) | `PENDING — TV2 cung cấp; main hiện tại 950279e25df2b658c39acfc3a3c37640bb8e6cac chưa được xác nhận frozen` |
| Freeze timestamp (Asia/Ho_Chi_Minh) | `PENDING` |
| CI readiness/frontend/E2E URLs + run IDs và applicability trên frozen SHA | `PENDING — nếu freeze SHA chỉ khác CI-tested SHA ở docs, TV2 cung cấp diff chứng minh không đổi frontend/backend/docker/scripts/demo/.github/workflows và xác nhận run nào bao phủ code frozen; nếu các path này đổi thì cần CI phù hợp trên code SHA mới` |
| Clean-clone receipt / máy / OS / tool versions | `PENDING` |
| Clean-stack receipt / Compose project / image digests | `PENDING` |
| DB identity / schema migration version / volume names | `PENDING` |
| Ports / network / loopback binding | `PENDING` |
| Flags thực tế và approval link cho từng flag | `PENDING — default OFF; runtime NOT VERIFIED; M01 MD01–MD11 approved for local demo/submission only, production NOT APPROVED` |
| Run ID / start-end time | `PENDING` |
| TV2 operator / TV3 / TV4 / TV5 witness | `PENDING` |
| Seed dataset version / fake actor IDs | `PENDING` |
| Evidence root + manifest SHA256 | `PENDING` |

## 2. Chuẩn evidence tối thiểu

Mỗi case phải có một thư mục riêng `reports/minh-chung-kiem-thu/tv5-final-uat-20261121/<run-id>/<test-id>/` và manifest `manifest.json` (hoặc `.yaml`) ghi tối thiểu:

```json
{
  "repository": "duyxl56785/Project_HK5",
  "test_id": "UAT-01",
  "frozen_sha": "<40-character-main-sha>",
  "run_id": "<unique-run-id>",
  "started_at": "<ISO-8601 with +07:00>",
  "finished_at": "<ISO-8601 with +07:00>",
  "environment": {
    "os": "<OS/version>",
    "compose_project": "<isolated-project>",
    "image_digests": ["<image@sha256:...>"],
    "db_identity": "<non-secret identifier>",
    "ports": ["<loopback-bound ports>"],
    "flags": {
      "HK5_VOUCHER_C2_ENABLED": false,
      "HK5_FINANCE_POLICY_APPROVED": false
    }
  },
  "actor": "<fake actor ID/role>",
  "operator": "<GitHub/team identity>",
  "steps": ["<commands/actions actually performed>"],
  "actual_result": "<observed response/state, no interpretation-only text>",
  "status": "NOT RUN",
  "artifacts": [
    {"path": "response.json", "kind": "HTTP response", "sha256": "<hash>", "redacted_copy": "response.redacted.json"}
  ],
  "notes_or_blocker": "<reason, if any>"
}
```

**Không copy JSON mẫu với placeholder thành receipt.** Người gửi phải thay bằng giá trị thật, hash thật và đính kèm raw artifact. Nếu flag khác `false`, phải kèm approval link của TV2/business owner và runtime evidence do operator chụp; không nhận lời xác nhận miệng.

### Quy trình nhận evidence từ các owner

1. **TV2** gửi frozen SHA, freeze time, CI URLs/run IDs, clean-clone/clean-stack receipts, Compose project/image digests, flags thực tế, operator và quyết định P0/C2/Finance.
2. **TV3** gửi HTTP request/response đã redacted, Cart/Checkout/Order IDs giả, Idempotency-Key đã redacted/định danh hash, quote/price snapshots và Order/SellerOrder read-only receipts cho case Checkout/Cart/Voucher.
3. **TV4** gửi Inventory operation/tuple, stock before-after, SellerOrder state transition, COD collection receipt/audit, Return state và restock receipt nếu contract/implementation đã được duyệt. Mọi receipt phải gắn đúng frozen SHA và run ID.
4. **TV5** kiểm tra money oracle, fee/net/discount, report, settlement wording, manifest, SHA256, actor/time và liên kết case. TV5 không tự tạo dữ liệu owner khác để lấp chỗ trống.
5. TV5 trả lại receipt thiếu SHA, thời gian, actor, môi trường, test ID, raw log/hash hoặc có dữ liệu nhạy cảm. Không cập nhật trạng thái case cho tới khi receipt được xác minh.

## 3. Checklist 17 case nền hai Store

### UAT-01 — Auth / RBAC
- **Mục tiêu:** xác minh quyền dựa trên server identity, token/session và không có side effect khi unauthenticated/unauthorized.
- **Điều kiện trước:** frozen SHA + CI + clean stack; fake Customer, Seller A/B, Admin; TV2 xác nhận test credentials và token lifecycle.
- **Các bước:** gọi endpoint được phép khi chưa login; login từng role; gọi endpoint role khác; thử token hết hạn/revoke theo flow được hỗ trợ; đọc audit nếu có.
- **Kết quả mong đợi:** unauthenticated trả 401, sai quyền 403/404 theo contract; token revoke/hết hạn bị từ chối; không phát sinh thay đổi dữ liệu.
- **Bằng chứng:** HTTP request/response, auth/audit log redacted, actor-role matrix, DB before/after nếu endpoint có side effect.
- **Owner cung cấp:** TV2 (Auth/Gateway), TV5 witness/đối chiếu.
- **Trạng thái:** `NOT RUN`.

### UAT-02 — Catalog / Product / Store facts
- **Mục tiêu:** xác minh Product ACTIVE, SKU, Store/Seller identity và giá được Checkout sử dụng là nhất quán.
- **Điều kiện trước:** TV2 cung cấp hai Store giả, Product ACTIVE và Catalog API; TV3 xác nhận consumer contract.
- **Các bước:** đọc Product/Store facts của A và B; đối chiếu sellerId/storeId/SKU/price/status; thử tham chiếu Product không thuộc Store hoặc Product không ACTIVE bằng request không gây side effect.
- **Kết quả mong đợi:** dữ liệu Catalog nhất quán; Checkout chỉ chấp nhận facts xác minh được; không xác minh được thì fail closed.
- **Bằng chứng:** Catalog HTTP JSON, Product/Store IDs giả, DB/catalog read-only receipt, request bị từ chối.
- **Owner cung cấp:** TV2 (Catalog/Store), TV3 (Checkout consumer).
- **Trạng thái:** `NOT RUN`.

### UAT-03 — Cart hai Store
- **Mục tiêu:** xác minh một Customer cart có hai nhóm Store và tổng tiền được tính nhất quán.
- **Điều kiện trước:** UAT-01/02 đủ điều kiện; fake Customer; một Product ACTIVE từ mỗi Store; C2 không cần bật để kiểm Cart.
- **Các bước:** thêm Product A và B; thay quantity; reload Cart; đọc API Cart và UI; đối chiếu subtotal từng Store và tổng.
- **Kết quả mong đợi:** một Cart/session có đúng hai Store; quantity/subtotal/total khớp server response; không lẫn owner.
- **Bằng chứng:** Cart API request/response, screenshot Cart, Product/Store mapping, cart snapshot.
- **Owner cung cấp:** TV3; TV2 hỗ trợ Catalog nếu cần.
- **Trạng thái:** `NOT RUN`.

### UAT-04 — Checkout COD
- **Mục tiêu:** tạo một Checkout COD hai Store trên fixture giả, không gọi thanh toán online hoặc chuyển tiền thật.
- **Điều kiện trước:** UAT-03; TV2 xác nhận flags Inventory/Finance và scope demo; TV3 xác nhận quote/checkout contract; TV4 xác nhận Inventory test environment.
- **Các bước:** ghi quote; submit Checkout đúng payload/key; lưu HTTP response và Order ID; đối chiếu COD amount với order snapshot.
- **Kết quả mong đợi:** một request thành công; COD total khớp snapshot; không có payment capture/chuyển tiền thật.
- **Bằng chứng:** quote + Checkout HTTP JSON, Order ID, payment method/state, audit; không lưu PII.
- **Owner cung cấp:** TV3 (Checkout), TV4 (Inventory/COD side effects), TV5 đối chiếu tổng.
- **Trạng thái:** `NOT RUN`.

### UAT-05 — Parent Order / hai SellerOrders
- **Mục tiêu:** xác minh một Parent/Root Order tách thành đúng hai SellerOrders theo Store.
- **Điều kiện trước:** UAT-04 có Order ID; DB read-only access do owner cấp; mapping Store A/B.
- **Các bước:** mở Order detail; lấy Parent ID và hai child IDs; truy vấn DB read-only cùng run; đối chiếu seller/store ownership và money totals.
- **Kết quả mong đợi:** một Parent Order, hai SellerOrders khác ID thuộc đúng Store/Seller; parent total bằng tổng gross child theo contract.
- **Bằng chứng:** Order API JSON, hai child IDs, query/read-only receipt có timestamp và DB identity, screenshot detail.
- **Owner cung cấp:** TV3; TV5 kiểm tiền; TV4 xác nhận status nếu cần.
- **Trạng thái:** `NOT RUN`.

### UAT-06 — Idempotency replay
- **Mục tiêu:** xác minh replay cùng key và payload không tạo Order/SellerOrder/RESERVE trùng.
- **Điều kiện trước:** một Checkout đã thành công trên fixture; TV3 cung cấp cách replay an toàn; TV4 cấp Inventory read-only oracle.
- **Các bước:** gửi lại cùng Idempotency-Key và payload; so response/orderId; đọc Order count và Inventory operations trước/sau.
- **Kết quả mong đợi:** trả cùng kết quả/orderId theo contract; không tạo thêm Order, SellerOrder hoặc RESERVE.
- **Bằng chứng:** hai HTTP response, key fingerprint (không lộ secret), Order DB count, Inventory operation receipt.
- **Owner cung cấp:** TV3 (request/replay), TV4 (Inventory), TV5 witness.
- **Trạng thái:** `NOT RUN`.

### UAT-07 — Idempotency mismatch
- **Mục tiêu:** cùng key nhưng payload khác phải bị từ chối và không có side effect mới.
- **Điều kiện trước:** UAT-06 có key/attempt ban đầu; TV3 duyệt payload biến thể an toàn.
- **Các bước:** gửi cùng key nhưng thay items hoặc revision theo fixture; lưu response; đối chiếu Order/Inventory trước-sau.
- **Kết quả mong đợi:** HTTP 409 theo contract; không tạo đơn hoặc RESERVE thứ hai; order ban đầu không bị thay đổi.
- **Bằng chứng:** request/response redacted, original payload fingerprint, DB/Inventory before-after.
- **Owner cung cấp:** TV3; TV4 xác nhận không có RESERVE mới.
- **Trạng thái:** `NOT RUN`.

### UAT-08 — Shipping mismatch
- **Mục tiêu:** shipping khác quote/attempt đã xác nhận với cùng key không làm thay đổi Order.
- **Điều kiện trước:** quote/attempt hợp lệ; TV3 chỉ định field shipping có thể thay đổi trong test.
- **Các bước:** gửi request với shipping option/address fixture khác nhưng giữ cùng key; đọc response và Order snapshot.
- **Kết quả mong đợi:** HTTP 409 theo contract; Order hiện có không đổi; không phát sinh side effect mới.
- **Bằng chứng:** quote, request biến thể, response, Order before-after, Inventory before-after nếu liên quan.
- **Owner cung cấp:** TV3; TV4 xác nhận stock không đổi.
- **Trạng thái:** `NOT RUN`.

### UAT-09 — Inventory RESERVE
- **Mục tiêu:** xác minh một RESERVE đúng operation/tuple cho mỗi intent và không tăng khi replay.
- **Điều kiện trước:** TV4 xác nhận provider/oracle và cách đọc operation; UAT-04/06 có run IDs; không chạy UNKNOWN fixture.
- **Các bước:** đọc stock/operation ledger trước Checkout; thực hiện Checkout; đọc ledger và stock sau; replay theo UAT-06 rồi đọc lại.
- **Kết quả mong đợi:** RESERVE đúng số lượng/Store/operation; replay không gửi RESERVE lặp; tồn kho không âm.
- **Bằng chứng:** Inventory operation receipt, stock before-after, event/log correlation IDs, Checkout run ID.
- **Owner cung cấp:** TV4 (provider/Inventory), TV3 (checkout correlation).
- **Trạng thái:** `NOT RUN`.

### UAT-10 — Inventory lifecycle
- **Mục tiêu:** xác minh CONSUME/RELEASE gắn đúng transition và không lặp.
- **Điều kiện trước:** TV4 xác nhận contract/state machine và các trạng thái thao tác hợp lệ; fixture riêng; không ảnh hưởng dữ liệu dùng chung.
- **Các bước:** đi theo accept/prepare/ready/dispatch/deliver được hỗ trợ; tạo nhánh cancel/reject riêng chỉ tại state hợp lệ; đọc operation ledger sau mỗi transition.
- **Kết quả mong đợi:** CONSUME/RELEASE đúng operationId và transition; không double consume/release; tồn kho không âm; không suy kết quả từ UI.
- **Bằng chứng:** state transition receipts, Inventory operation IDs, stock before-after, audit/log theo timestamp.
- **Owner cung cấp:** TV4; TV3 xác nhận trạng thái Order liên quan.
- **Trạng thái:** `NOT RUN`.

### UAT-11 — Seller isolation
- **Mục tiêu:** xác minh Seller A/B chỉ đọc/thao tác dữ liệu thuộc Store mình.
- **Điều kiện trước:** hai Seller actor và Store A/B; có Order/Return/Finance fixture do owner cấp; TV2 xác nhận quyền/Auth.
- **Các bước:** Seller A gọi list/detail/return/finance cho Store A; thử IDs của Store B; lặp lại theo chiều ngược; không thử mutation phá dữ liệu.
- **Kết quả mong đợi:** chỉ thấy dữ liệu Store mình; truy cập chéo trả 403/404 đúng contract và không rò record.
- **Bằng chứng:** request/response redacted cho positive/negative, actor/store IDs giả, audit và screenshot nếu cần.
- **Owner cung cấp:** TV4 (fulfillment/return/finance routes), TV2 (Auth/Store scope).
- **Trạng thái:** `NOT RUN`.

### UAT-12 — Return
- **Mục tiêu:** xác minh Return COD theo policy, quyền Seller/Buyer, refundAmount và giới hạn restock.
- **Điều kiện trước:** COD Order DELIVERED, chưa settled; TV3/TV4 xác nhận state/contract; TV5 xác nhận oracle gross; RET-05 chỉ chạy nếu TV4 chứng minh implementation exactly-once đã được review.
- **Các bước:** Buyer tạo Return; Seller đúng Store approve/reject/receive theo flow; replay duplicate key an toàn; đọc Return state, SellerOrder gross/refund snapshot và Inventory operation nếu có.
- **Kết quả mong đợi:** quyền/state đúng; refundAmount bằng gross đã lưu; Return sau DELIVERED không tự hoàn lượt voucher; không tự restock nếu RET-05 chưa có contract/receipt.
- **Bằng chứng:** Return API/audit, SellerOrder snapshot, refund calculation receipt, Inventory restock receipt nếu được phép.
- **Owner cung cấp:** TV4 (Return/fulfillment/Inventory), TV3 (Order contract), TV5 (gross/refund oracle).
- **Trạng thái:** `BLOCKED` cho mọi tuyên bố restock RET-05; phần Return không-restock vẫn `NOT RUN` đến khi có frozen SHA và fixture.

### UAT-13 — COD collection
- **Mục tiêu:** xác minh Admin thu COD đúng thời điểm, đúng amount, đúng một lần.
- **Điều kiện trước:** Order DELIVERED, chưa COLLECTED; Admin actor giả; TV4 xác nhận COD route/receipt và test fixture.
- **Các bước:** thử thu trước DELIVERED; thu sau DELIVERED; thử thu lặp; đọc collection record/audit và amount.
- **Kết quả mong đợi:** chỉ thu sau DELIVERED; một collection duy nhất; amount khớp gross; lần lặp bị từ chối; không mô tả là tiền đã chuyển vào ngân hàng.
- **Bằng chứng:** HTTP responses, COD collection receipt, audit actor/time/amount/idempotency, SellerOrder state.
- **Owner cung cấp:** TV4 (COD), TV5 witness/đối chiếu amount.
- **Trạng thái:** `NOT RUN`.

### UAT-14 — Finance snapshot
- **Mục tiêu:** đối chiếu merchandise, discount, gross, platformFee và sellerNet theo policy có thẩm quyền.
- **Điều kiện trước:** frozen SHA; policy source `docs/project-report/M01_FINANCE_DECISION.md` (MD01–MD11: 5%, HALF_UP, scale 0, demo local/bản nộp) đã được TV2 chốt; TV2 cung cấp runtime/flag receipt trong môi trường demo được phép; TV4 xác nhận COD state. Không suy ra production approval.
- **Các bước:** đọc SellerOrder snapshot và Store/platform report; đối chiếu từng thành phần; xác minh voucherDiscount riêng nếu có; kiểm settlement wording.
- **Kết quả mong đợi:** fee/net theo policy đã ký; nếu policy 5%/scale 0 được phê duyệt thì fee tính trên gross, HALF_UP; sellerNet = gross − fee; `settledAt` không được diễn giải là chuyển tiền thật.
- **Bằng chứng:** M01 policy link, report/API JSON, DB read-only snapshot, calculation sheet với input/output và timestamp.
- **Owner cung cấp:** TV5 (Finance oracle/report), TV4 (COD source), TV2/business (policy approval).
- **Trạng thái:** `NOT RUN` — policy demo/local đã có; chờ frozen SHA, runtime/fixture và receipt thực tế. Không tuyên bố Finance production đã được phê duyệt.

### UAT-15 — Admin moderation / Seller approval
- **Mục tiêu:** xác minh Admin xem moderation, Seller approval và COD/Finance read-only đúng quyền.
- **Điều kiện trước:** Admin actor giả; TV2 cung cấp Seller pending/approved fixture và màn hình đúng; TV4 cung cấp COD read-only fixture.
- **Các bước:** Admin mở Seller approval/moderation; kiểm audit/actor; gọi cùng endpoint bằng Seller/Customer; đối chiếu đúng nội dung ảnh 6.4.
- **Kết quả mong đợi:** Admin được phép; role khác bị từ chối; audit actor đúng; ảnh chụp đúng màn Admin Seller approval, không thay bằng màn Checkout.
- **Bằng chứng:** screenshot UI, API responses, audit event và actor-role matrix.
- **Owner cung cấp:** TV2 (Seller/Auth/moderation), TV4 (COD read-only); TV5 kiểm ảnh.
- **Trạng thái:** `NOT RUN`.

### UAT-16 — UNKNOWN claim conflict
- **Mục tiêu:** chỉ xác minh cơ chế UNKNOWN/quarantine khi có fixture contention thật và provider oracle được duyệt.
- **Điều kiện trước:** TV2 duyệt runner/overlay; TV3 cung cấp immutable tuple; TV4 cung cấp provider oracle read-only; TV5 duyệt evidence plan và được cấp quyền chạy cụ thể.
- **Các bước:** **Không chạy trong checklist mặc định.** Chỉ khi đủ phê duyệt riêng, chạy đúng runner/version đã review và thu receipt provider/Cart/Order theo tuple đã khóa.
- **Kết quả mong đợi:** không giả lập 409; trạng thái UNKNOWN/quarantine đúng contract; không auto retry/RELEASE/CONSUME khi chưa có bằng chứng.
- **Bằng chứng:** approval link, runner hash, tuple fingerprint, provider receipt, Cart/Order before-after, full logs.
- **Owner cung cấp:** TV2 (runner/stack), TV3 (tuple/Cart), TV4 (provider oracle), TV5 (QA witness).
- **Trạng thái:** `BLOCKED` — thiếu runner, tuple, provider oracle và approval riêng.

### UAT-17 — UNKNOWN lost response
- **Mục tiêu:** chỉ xác minh xử lý mất response sau provider COMMIT khi có runner và receipt thật.
- **Điều kiện trước:** TV2 duyệt runner/overlay; TV3 chốt cùng key/body/operationId; TV4 chứng minh provider COMMIT; TV5 duyệt fixture và quyền thực thi.
- **Các bước:** **Không chạy trong checklist mặc định.** Không tự tạo timeout/fault. Chỉ chạy nếu có approval riêng và quy trình rollback/reconciliation đã review.
- **Kết quả mong đợi:** giữ cùng attempt/tuple; ghi UNKNOWN/NEEDS_RECONCILIATION theo contract; không tạo Order/stock effect mới và không compensation mù.
- **Bằng chứng:** approval, runner hash, request fingerprint, provider COMMIT receipt, Order/Cart/Inventory before-after, logs và reconciliation outcome.
- **Owner cung cấp:** TV2 (runner), TV3 (tuple/Order), TV4 (provider COMMIT/stock), TV5 (QA witness).
- **Trạng thái:** `BLOCKED` — thiếu runner/approval và provider COMMIT receipt.

## 4. Checklist 5 case Voucher C1

### C1-01 — Tạo voucher / Store ownership
- **Mục tiêu:** voucher thuộc Store của Seller theo JWT, không tin `storeId` từ client.
- **Điều kiện trước:** frozen SHA; Seller A/B; C1 API và Store mapping được TV2/TV3 xác nhận; C2 OFF vẫn chạy được C1 management.
- **Các bước:** Seller A tạo voucher; thử gửi storeId của B trong body; đọc voucher detail/DB owner; đối chiếu Seller A/B.
- **Kết quả mong đợi:** voucher DRAFT thuộc Store A; không thể giả mạo Store B.
- **Bằng chứng:** create/detail HTTP JSON, actor, store mapping, DB read-only row.
- **Owner cung cấp:** TV3 (voucher), TV2 (Auth/Store).
- **Trạng thái:** `NOT RUN`.

### C1-02 — Validation
- **Mục tiêu:** xác minh validation code, discount type/value, min order, quota và thời hạn.
- **Điều kiện trước:** C1-01; test data giả; TV3 xác nhận contract/validation bounds.
- **Các bước:** thử code 3/21 ký tự; FIXED không hợp lệ; PERCENT ngoài 1–90/thiếu maxDiscount; FIXED có maxDiscount; minOrderAmount sai; usageLimit ngoài 1–100000; endsAt sai hoặc quá 90 ngày.
- **Kết quả mong đợi:** input sai trả 400; code chuẩn hóa uppercase; FIXED không có maxDiscount; PERCENT bắt buộc maxDiscount; duration không quá 90 ngày.
- **Bằng chứng:** từng request/response validation, error code/field, không ghi raw secret.
- **Owner cung cấp:** TV3.
- **Trạng thái:** `NOT RUN`.

### C1-03 — Draft / edit / state transitions
- **Mục tiêu:** xác minh chỉ DRAFT được sửa và các state transition hợp lệ.
- **Điều kiện trước:** voucher fixture DRAFT; đồng hồ/server timezone được ghi; TV3 xác nhận transition matrix.
- **Các bước:** sửa DRAFT; publish; thử sửa sau publish; pause/resume/cancel theo trạng thái được phép; đọc history.
- **Kết quả mong đợi:** chỉ DRAFT editable; transition đúng; SCHEDULED/ACTIVE/EXPIRED theo server time; history có actor/transition.
- **Bằng chứng:** request/response từng transition, state history/audit, timestamp.
- **Owner cung cấp:** TV3.
- **Trạng thái:** `NOT RUN`.

### C1-04 — Quota
- **Mục tiêu:** xác minh giới hạn publish 20 voucher và lỗi vượt quota.
- **Điều kiện trước:** Store giả riêng; TV3 xác nhận quota mặc định và fixture cleanup; không dùng Store thật.
- **Các bước:** tạo/publish đến giới hạn; thử publish voucher thứ 21; kiểm tra DRAFT/PAUSED không chiếm slot.
- **Kết quả mong đợi:** 20 publish được; vượt giới hạn trả 409 `VOUCHER_QUOTA_EXCEEDED`; không tự hủy voucher đã publish.
- **Bằng chứng:** voucher IDs/states, HTTP response, quota read-only snapshot.
- **Owner cung cấp:** TV3 (logic), TV2 (nếu cần Admin/Store fixture).
- **Trạng thái:** `NOT RUN`.

### C1-05 — Limit request / Admin
- **Mục tiêu:** xác minh yêu cầu nâng quota, idempotency và Admin approval boundary.
- **Điều kiện trước:** Seller/Admin fake actors; TV3 xác nhận API; TV2 xác nhận Admin permission và self-moderation policy.
- **Các bước:** Seller gửi request; replay cùng key/body; gửi cùng key khác body; Admin approve/reject; thử Seller gọi Admin API và Admin tự duyệt Store mình.
- **Kết quả mong đợi:** PENDING không tăng quota; replay cùng key trả cùng request; payload khác 409; audit đúng; self-moderation 403.
- **Bằng chứng:** HTTP JSON, request key fingerprint, request state/quota before-after, audit actor.
- **Owner cung cấp:** TV3 (workflow), TV2 (Admin/Auth).
- **Trạng thái:** `NOT RUN`.

## 5. Checklist 4 case Promotion

### PROMO-01 — Campaign lifecycle
- **Mục tiêu:** xác minh Seller tạo/gửi duyệt và Admin approve/reject theo quyền.
- **Điều kiện trước:** TV2 cung cấp campaign fixtures và quyền Admin; server time ghi nhận.
- **Các bước:** Seller tạo campaign; submit; Admin xem và approve/reject; đọc state/audit; xác minh public Catalog.
- **Kết quả mong đợi:** state/audit đúng; chỉ campaign được duyệt có hiệu lực; mock UI không được tính là evidence.
- **Bằng chứng:** Seller/Admin API, audit, Catalog response và screenshot live.
- **Owner cung cấp:** TV2.
- **Trạng thái:** `NOT RUN`.

### PROMO-02 — Flash Sale / Super Sale
- **Mục tiêu:** xác minh riêng giá gốc/giá hiệu lực của Flash Sale và Super Sale.
- **Điều kiện trước:** TV2 cung cấp campaign/Product đã duyệt và thời gian hiệu lực; fake Customer.
- **Các bước:** đọc Catalog và Cart cho từng loại; so giá gốc, giá hiệu lực và Store; kiểm Seller chịu phần giảm theo MD12.
- **Kết quả mong đợi:** Catalog/Cart nhất quán; phân biệt Flash Sale và Super Sale; không suy kết quả loại này từ loại kia.
- **Bằng chứng:** campaign IDs/states, Catalog/Cart JSON, screenshots và price calculation.
- **Owner cung cấp:** TV2; TV3 hỗ trợ Cart/Checkout consumer nếu cần.
- **Trạng thái:** `NOT RUN`.

### PROMO-03 — Clearance
- **Mục tiêu:** xác minh Seller clearance, Admin duyệt campaign/Product và giá sale dùng trong Checkout COD.
- **Điều kiện trước:** campaign/Product giả; TV2 approve; TV3 Checkout; TV4 xác nhận Inventory fixture; C2 không cần bật cho case này.
- **Các bước:** Seller tạo clearance; Admin duyệt campaign và Product; Customer mở Product; Checkout COD không dùng voucher; đối chiếu price snapshot/quantity.
- **Kết quả mong đợi:** giá sale đúng snapshot; state/quantity đúng campaign; Checkout dùng giá hiệu lực; không áp voucher trong case độc lập.
- **Bằng chứng:** approval/audit, Catalog/Checkout JSON, Order snapshot, Inventory receipt và screenshots.
- **Owner cung cấp:** TV2 (campaign), TV3 (Checkout), TV4 (Inventory nếu phát sinh).
- **Trạng thái:** `NOT RUN`.

### PROMO-04 — Voucher conflict / MD17
- **Mục tiêu:** xác minh voucher Store bị từ chối khi sản phẩm đang hưởng Promotion.
- **Điều kiện trước:** promotion active + voucher hợp lệ; C2 ON chỉ khi G-04..G-08 đạt và TV2 cho phép; C2 OFF thì ghi đúng “không áp dụng, chưa bật”.
- **Các bước:** khi C2 ON, Customer nhập voucher Store tương ứng và gọi quote; đọc HTTP response, alert, Order/ledger before-after. Không thử bật cờ nếu chưa duyệt.
- **Kết quả mong đợi:** C2 ON trả 409 `VOUCHER_PROMOTION_CONFLICT`, không tạo Order/không trừ lượt; C2 OFF không chạy nhánh conflict.
- **Bằng chứng:** flags runtime do TV2 chụp, quote request/response, redemption ledger before-after, screenshot alert.
- **Owner cung cấp:** TV2 (Promotion), TV3 (Voucher/Checkout), TV5 witness.
- **Trạng thái:** `BLOCKED` cho nhánh C2 ON đến khi đủ gate; nhánh C2 OFF `N/A — không áp dụng, chưa bật`.

## 6. Checklist 11 case Voucher C2

**Gate:** #211/#212 đã merge và TV5 đã formal APPROVED phần tiền #211 tại exact HEAD `4f3152b`; PR #215 decision records và PR #218 dependency fix cũng đã merge. Main hiện tại là `950279e25df2b658c39acfc3a3c37640bb8e6cac`. Frontend/E2E [run 44700827386](https://github.com/duyxl56785/Project_HK5/actions/runs/44700827386) SUCCESS trên đúng SHA, gồm C2 OFF và C2 ON. Backend readiness gần nhất [run 44493061575](https://github.com/duyxl56785/Project_HK5/actions/runs/44493061575) gắn `cee7c87`; diff từ đó đến main hiện tại không đổi backend/docker/workflow backend-readiness paths, nhưng TV2 vẫn phải xác nhận applicability cho frozen SHA. C2 ON vẫn `BLOCKED` cho tới khi TV2 xác nhận Frozen SHA/CI applicability và TV2/TV3/TV4/TV5 triage xác nhận không còn P0 C2. Quyết định TV2 ngày 18/11 tại [PR #211](https://github.com/duyxl56785/Project_HK5/pull/211#issuecomment-6969662837) nêu đúng bốn điều kiện, không thêm một business approval riêng cho Checkout có voucher. M01 MD01–MD11 đã phê duyệt Finance 5%/HALF_UP cho demo local và bản nộp; production không được phê duyệt. Runtime flags chưa được xác minh. Nếu gate không đạt, chạy UAT với C2 OFF và ghi “đã code, đã test, chưa bật trong bản nộp”.

### C2-01 — Flag / capability
- **Mục tiêu:** xác minh capability ON/OFF và fail-closed khi OFF.
- **Điều kiện trước:** G-04..G-08 đủ; TV2 ghi flag runtime thật và approval; C2 OFF control case có thể chạy mà không đổi flag.
- **Các bước:** đọc `/api/orders/voucher-capability` bằng Customer ở cấu hình được TV2 cấp; gọi quote/checkout voucher trong cấu hình OFF chỉ với fixture an toàn.
- **Kết quả mong đợi:** ON `enabled=true`; OFF `enabled=false` và voucher request bị từ chối `VOUCHER_C2_DISABLED`; C1 management không bị ảnh hưởng.
- **Bằng chứng:** runtime env receipt do TV2 cung cấp, capability HTTP JSON, OFF rejection, run/SHA.
- **Owner cung cấp:** TV2 (flag/stack), TV3 (API/Checkout).
- **Trạng thái:** `BLOCKED` — chờ TV2 xác nhận Frozen SHA, CI applicability và P0 C2 triage; Frontend/E2E `44700827386` xanh trên main `950279e`. Runtime flags chưa xác minh.

### C2-02 — Per-Store voucher
- **Mục tiêu:** xác minh tối đa một voucher cho mỗi Store trong Cart nhiều Store.
- **Điều kiện trước:** C2 ON được duyệt; hai Store có voucher active; Customer/cart fixture.
- **Các bước:** áp hai mã thuộc hai Store khác nhau; thử hai mã cùng Store; thử mã của Store không có trong Cart.
- **Kết quả mong đợi:** mỗi Store dùng một mã riêng; trùng Store trả `VOUCHER_DUPLICATE_STORE`; Store ngoài Cart trả `VOUCHER_STORE_MISMATCH`.
- **Bằng chứng:** quote request/response, voucher/store mapping, cart snapshot, redemption ledger.
- **Owner cung cấp:** TV3; TV5 đối chiếu tiền.
- **Trạng thái:** `BLOCKED` — C2 chưa được phép bật.

### C2-03 — MD15 FIXED / minOrder
- **Mục tiêu:** xác minh FIXED discount đúng tại minOrder và bị từ chối dưới ngưỡng.
- **Điều kiện trước:** C2 ON được duyệt; voucher FIXED và Store fixture; minOrder đã ghi.
- **Các bước:** quote đúng minOrder; quote thấp hơn minOrder; so discount/gross/error.
- **Kết quả mong đợi:** đúng ngưỡng giảm đúng discountValue; thấp hơn trả `VOUCHER_MIN_ORDER_AMOUNT`.
- **Bằng chứng:** voucher definition, quote HTTP JSON, calculation inputs/outputs.
- **Owner cung cấp:** TV3; TV5 kiểm oracle tiền.
- **Trạng thái:** `BLOCKED` — C2 chưa được phép bật.

### C2-04 — MD15 PERCENT / HALF_UP / cap
- **Mục tiêu:** xác minh làm tròn phần trăm HALF_UP một lần rồi áp maxDiscount.
- **Điều kiện trước:** C2 ON được duyệt; voucher PERCENT có maxDiscount; TV3 xác nhận test amount.
- **Các bước:** quote base `199.999` với 15%; thử case chạm cap và vượt cap; đối chiếu server output.
- **Kết quả mong đợi:** `199.999 × 15% = 29.999,85`, HALF_UP thành `30.000`, sau đó lấy min với maxDiscount; không round lần hai.
- **Bằng chứng:** voucher config, quote JSON, bảng tính độc lập, DB snapshot nếu có.
- **Owner cung cấp:** TV3 (calculation), TV5 (money oracle).
- **Trạng thái:** `BLOCKED` — C2 chưa được phép bật.

### C2-05 — MD16 two-Store money oracle
- **Mục tiêu:** đối chiếu discount, gross, fee, net và Parent Order hai Store.
- **Điều kiện trước:** C2 ON gate đủ; M01 MD01–MD11 áp dụng cho demo local/bản nộp; TV2 cung cấp runtime receipt; TV3 cấp order fixture; TV5 xác nhận calculation oracle. Không suy production approval.
- **Các bước:** Store A `150.000 − 10.000`; Store B `280.000 − 5.000`; so quote, Checkout, Parent Order, hai SellerOrders và report.
- **Kết quả mong đợi:** A gross 140.000, fee 7.000; B gross 275.000, fee 13.750; customer total 415.000; sellerNet lần lượt 133.000 và 261.250 nếu rate 5%/scale 0 được duyệt.
- **Bằng chứng:** policy approval, quote/Order/SellerOrder JSON, DB read-only money snapshots, calculation sheet.
- **Owner cung cấp:** TV3 (Order), TV5 (Finance oracle), TV2/business (policy).
- **Trạng thái:** `BLOCKED` — phụ thuộc C2 ON gate/frozen-SHA receipt; Finance demo/local policy đã được phê duyệt, production không nằm trong phạm vi.

### C2-06 — MD19 failed Checkout
- **Mục tiêu:** xác minh Checkout thất bại không làm mất voucher use và cleanup idempotent.
- **Điều kiện trước:** C2 ON được duyệt; voucher hợp lệ; TV3 chỉ định failure point an toàn trước Order creation; TV4 xác nhận Inventory oracle.
- **Các bước:** thực hiện đúng failure scenario được owner duyệt; đọc redemption ledger, Order và Inventory before-after; không tự phát sinh fault khác.
- **Kết quả mong đợi:** failed Checkout không consume voucher; release/tombstone idempotent; không có Order/RESERVE ngoài dự kiến.
- **Bằng chứng:** approved scenario, HTTP response, redemption ledger, Order/Inventory before-after, correlation IDs.
- **Owner cung cấp:** TV3 (Checkout), TV4 (Inventory), TV5 witness.
- **Trạng thái:** `BLOCKED` — C2 và failure fixture chưa được duyệt.

### C2-07 — MD19 cancel / reject
- **Mục tiêu:** xác minh voucher use được hoàn đúng một lần khi cancel/reject ở state cho phép.
- **Điều kiện trước:** C2 ON được duyệt; Order có voucher; TV4 xác nhận state cho phép cancel/reject trước dispatch.
- **Các bước:** tạo order fixture; cancel hoặc reject theo flow owner chỉ định; replay recovery an toàn; đọc redemption ledger; không dùng Return sau DELIVERED làm trường hợp release.
- **Kết quả mong đợi:** lượt voucher hoàn đúng một lần; retry không release trùng; Return sau DELIVERED không hoàn lượt.
- **Bằng chứng:** Order state history, redemption ledger before-after, API/audit và idempotency correlation.
- **Owner cung cấp:** TV4 (fulfillment), TV3 (voucher/order), TV5 witness.
- **Trạng thái:** `BLOCKED` — C2 chưa được phép bật.

### C2-08 — MD20 Return gross
- **Mục tiêu:** xác minh refundAmount dùng SellerOrder gross sau giảm.
- **Điều kiện trước:** C2 ON gate đủ; M01 MD20 cho phép hoàn theo gross; COD Order có voucher, DELIVERED, chưa settled; TV4 xác nhận Return contract và scope thực thi. Không suy production Finance approval hoặc tự chạy restock.
- **Các bước:** đọc SellerOrder snapshot; tạo Return theo flow được phép; so refundAmount với gross đã lưu; không kích hoạt chuyển tiền thật.
- **Kết quả mong đợi:** refundAmount = SellerOrder.gross sau discount; ví dụ gross 140.000 thì amount 140.000; không hoàn theo merchandise trước giảm.
- **Bằng chứng:** Return response, SellerOrder voucher snapshot, refund calculation, policy/approval link.
- **Owner cung cấp:** TV4 (Return), TV5 (gross oracle), TV3 (snapshot source).
- **Trạng thái:** `BLOCKED` — C2 ON gate và TV4 Return contract/fixture còn thiếu; không phải do thiếu Finance approval cho demo/local. RET-05 restock vẫn ngoài case này và giữ BLOCKED riêng.

### C2-09 — MD21 immutable snapshot
- **Mục tiêu:** xác minh voucher snapshot không thể bị thay đổi sau INSERT và money CHECK invariant giữ nguyên.
- **Điều kiện trước:** frozen SHA có migration V10; DB disposable; TV3 cấp test DB path và quyền read-only; mutation probe chỉ trong integration-test DB do owner kiểm soát.
- **Các bước:** đọc snapshot; chạy test/regression đã được review để thử UPDATE/DELETE/attach snapshot trong test DB; đọc kết quả trigger/CHECK; không chạy SQL mutation trên DB UAT dùng chung.
- **Kết quả mong đợi:** DB từ chối thay đổi snapshot sau INSERT; CHECK giữ `gross = merchandiseAmount − discountAmount`.
- **Bằng chứng:** migration version, JUnit/PG test report, SQLSTATE/error, before-after row và DB identity.
- **Owner cung cấp:** TV3 (Order schema/IT), TV5 (money invariant).
- **Trạng thái:** `NOT RUN` trên frozen SHA; CI PR #211 PostgreSQL regression không thay thế Final UAT receipt.

### C2-10 — MD24 Finance report
- **Mục tiêu:** xác minh report tách voucherDiscount khỏi platformFee và dùng snapshot sau giảm.
- **Điều kiện trước:** M01 MD01–MD11 cho demo local/bản nộp; SellerOrders đủ điều kiện report; C2 ON gate đủ nếu kiểm voucher discount; TV5 có oracle và report access.
- **Các bước:** đọc report Store A/B và platform; đối chiếu gross, fee, net, voucherDiscount; so snapshot từng SellerOrder.
- **Kết quả mong đợi:** voucherDiscount hiển thị riêng; không cộng vào platformFee; gross/fee/net theo snapshot; không tuyên bố settlement nếu chỉ có report.
- **Bằng chứng:** policy approval, report JSON/export, SellerOrder snapshot, calculation sheet.
- **Owner cung cấp:** TV5 (Finance/report), TV4 (COD state), TV2/business (approval).
- **Trạng thái:** `BLOCKED` — nhánh C2 report phụ thuộc C2 ON gate; không phải do thiếu Finance approval cho demo/local. Finance production vẫn NOT APPROVED.

### C2-11 — MD25 zero gross
- **Mục tiêu:** xác minh voucher không tạo SellerOrder/COD gross bằng 0 hoặc âm.
- **Điều kiện trước:** C2 ON được duyệt; FIXED voucher; Product/Store fixture; TV3 xác nhận quote contract.
- **Các bước:** quote với discount bằng đúng merchandiseAmount; quote với discount cao hơn 1 đồng; đọc Order/SellerOrder/ledger before-after.
- **Kết quả mong đợi:** từ chối `VOUCHER_GROSS_TOO_LOW`; không tự giảm discount; không tạo SellerOrder hoặc COD 0 đồng.
- **Bằng chứng:** voucher config, quote response, Order/SellerOrder count before-after, ledger.
- **Owner cung cấp:** TV3; TV5 xác minh money invariant.
- **Trạng thái:** `BLOCKED` — C2 chưa được phép bật.

## 7. Tổng hợp trạng thái mặc định trước ngày chạy

| Nhóm | Số case | Trạng thái chuẩn bị | Điều kiện chuyển trạng thái |
|---|---:|---|---|
| Baseline UAT-01..15 | 15 | NOT RUN | Frozen SHA + stack/clone receipts + owner evidence; UAT-14 dùng M01 demo/local policy đã duyệt, cần runtime fixture/receipt |
| UNKNOWN UAT-16..17 | 2 | BLOCKED | Runner/tuple/provider oracle/approval riêng và TV5 witness |
| Voucher C1 | 5 | NOT RUN | Frozen SHA + fake actors + Voucher API/Store fixtures |
| Promotion | 3 | NOT RUN | Frozen SHA + campaign/Product fixtures + TV2 approval |
| PROMO-04 | 1 | BLOCKED ON; OFF N/A | C2 gate đủ cho ON; OFF ghi đúng “không áp dụng, chưa bật” |
| Voucher C2 | 11 | BLOCKED (C2 OFF) | G-04..G-08; #211/#212 merge và TV5 money review đã đạt; còn frozen-SHA CI + P0 C2 triage + TV2 runtime receipt |

**Không phải 37 PASS.** Đây là checklist chuẩn bị; Final UAT chỉ được kết luận sau khi owner thực thi và TV5 xác minh receipts trên đúng frozen SHA.

## 8. Checklist Final QA Sign-off

- [ ] TV2 xác nhận frozen SHA, freeze time, scope và operator bằng văn bản.
- [ ] Main push CI readiness/frontend/E2E xanh trên đúng frozen SHA; log run và job URLs đã lưu.
- [ ] Clean clone và clean stack receipts có command, exit code, machine, image digest, DB identity và timestamps.
- [ ] C2/Finance flags runtime được TV2 ghi nhận; mặc định OFF; mọi bật cờ có approval đúng thẩm quyền.
- [ ] 37 case được phân loại PASS/FAIL/BLOCKED/NOT RUN; mỗi PASS có manifest + raw evidence + SHA256.
- [ ] UNKNOWN UAT-16/17 và RET-05 được xử lý đúng: chạy chỉ khi có gate riêng, nếu không phải nêu BLOCKED/NOT RUN trong kết luận.
- [ ] P0 list được TV2/TV3/TV4 triage; mọi P0 đóng hoặc có waiver được chủ dự án ký, không tự hạ severity.
- [ ] TV4 cung cấp Inventory/Seller isolation/Return/COD receipts; TV3 cung cấp Checkout/Order/Voucher receipts.
- [ ] TV5 đối chiếu money oracle, report, gross/discount/fee/net và settlement wording.
- [ ] Hình 6.1–6.8 chụp lại trên frozen SHA, đúng actor/viewport và có hash; không dùng ảnh rehearsal/CI/mock.
- [ ] Official DOCX `Bao_cao_Smart_ECommerce_ban_nop.docx` do TV2 cung cấp và TV5 đối chiếu; không thay bằng draft.
- [ ] Manifest tổng và SHA256 được lưu; Issue #18/#157/#162 chỉ cập nhật theo quyền owner và đủ acceptance criteria.

**Disposition mặc định nếu còn thiếu bất kỳ P0/frozen-SHA/receipt bắt buộc:** `BLOCKED — FINAL QA SIGN-OFF NOT GRANTED`.
