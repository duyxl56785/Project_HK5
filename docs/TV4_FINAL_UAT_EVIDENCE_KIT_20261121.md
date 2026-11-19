# TV4 — bộ chuẩn bị evidence Final UAT, 21/11/2026

**Owner:** Phạm Thế Cường (TV4)  
**Trạng thái tài liệu:** `PREPARATION_ONLY` — đây là runbook, **không** là
receipt kết quả và không xác nhận bất kỳ ca nào đã PASS.  
**Checkpoint nguồn:** `main@4b5bcb4ceb5a6a636e4c943db2d41c8a4c1bb14f`
(19/11/2026), trước khi TV2 công bố frozen SHA.

Tài liệu này áp dụng cùng bộ 37 ca Final UAT của TV5 trong PR #214. Nó chỉ
chuẩn bị phần TV4: Inventory, Fulfillment, Seller isolation, Return và COD.
Không sửa nội dung/mẫu của PR #214 và không thay thế quyết định của TV2, TV3
hoặc TV5.

## 1. Gate trước khi tạo bất kỳ receipt nào

Không tạo `manifest.json`, JUnit, log hay ảnh dưới danh nghĩa Final UAT cho
đến khi TV2 cung cấp đồng thời:

1. frozen `main` SHA đủ 40 ký tự, timestamp freeze và CI receipts đúng SHA;
2. quyết định P0/P1 và người vận hành;
3. Compose project/DB/volume/network cô lập, dữ liệu/tài khoản giả và thông
   tin image digest; và
4. flags thực chạy cùng link approval. Các cờ `INVENTORY_ORDER_PAIR_ENABLED`,
   `HK5_FINANCE_POLICY_APPROVED` và Voucher C2 vẫn OFF nếu không có approval
   riêng, có thể kiểm chứng.

Nếu thiếu bất kỳ mục nào, trạng thái mọi ca runtime bên dưới là
`WAITING_FROZEN_SHA` hoặc `BLOCKED`, không phải `PASS`/`FAIL`.

Sau gate, dùng đường dẫn do PR #214 quy định:

```text
reports/minh-chung-kiem-thu/tv5-final-uat-20261121/evidence/<run-id>/<test-id>/
```

Mỗi thư mục chỉ được tạo sau lần chạy thật và phải chứa raw artifact không sửa
byte, bản redacted nếu cần, cùng hash SHA-256. Không copy manifest mẫu có
placeholder thành receipt.

## 2. Danh mục TV4 và evidence bắt buộc

| Case | Kết quả cần kiểm chứng thực tế | Receipt TV4 cần bàn giao | Gate/owner phối hợp | Trạng thái trước UAT |
| --- | --- | --- | --- | --- |
| UAT-09 | RESERVE đúng SKU/Store/quantity; replay không tạo RESERVE thứ hai; stock không âm | operation tuple/receipt, stock before-after, correlation với Checkout/replay | TV3 cung cấp Checkout correlation | `WAITING_FROZEN_SHA` |
| UAT-10 | DISPATCHED tạo CONSUME một lần; cancel/reject hợp lệ tạo RELEASE một lần; không suy từ UI | state history, operation IDs, stock before-after, audit/log timestamp | TV3 xác nhận lifecycle/fixture | `WAITING_FROZEN_SHA` |
| UAT-11 | Seller A/B chỉ đọc/thao tác Store của mình; cross-Store không lộ record và không có side effect | positive + negative HTTP receipts, actor/store mapping giả, audit, ảnh UI tùy chọn | TV2 xác nhận Auth/Store facts | `WAITING_FROZEN_SHA` |
| UAT-12 | Return đúng quyền/trạng thái; `refundAmount` khớp gross snapshot; không hoàn voucher sau giao | Return API/audit, SellerOrder gross snapshot, phép đối chiếu refund | TV3 snapshot, TV5 money oracle | `WAITING_FROZEN_SHA`; REST-05 restock `BLOCKED` |
| UAT-13 | Admin thu COD sau DELIVERED, amount khớp gross, một collection duy nhất | HTTP positive/negative/duplicate, collection/audit actor-time-amount, SellerOrder state | TV5 đối chiếu amount | `WAITING_FROZEN_SHA` |
| UAT-14 (hỗ trợ) | COD source đồng nhất với Finance snapshot, không diễn giải settled là chuyển tiền thật | COD state/collection read-only receipt | TV5/TV2 sở hữu money policy | `WAITING_FROZEN_SHA` |
| UAT-15 (hỗ trợ) | COD read-only không lộ cho Seller/Customer; UI Admin/moderation đúng role | actor-role responses + audit; ảnh do owner chụp | TV2 owns Seller approval/moderation | `WAITING_FROZEN_SHA` |
| UAT-04/06/07/08 (hỗ trợ) | Checkout/replay/mismatch không tạo Inventory side effect ngoài dự kiến | operation ledger + stock before-after paired with TV3 HTTP receipt | TV3 | `WAITING_FROZEN_SHA` |
| PROMO-03 (hỗ trợ) | Checkout COD clearance dùng price snapshot; Inventory only confirms receipt if side effect phát sinh | operation/stock receipt, không đổi campaign evidence của TV2 | TV2 + TV3 | `WAITING_FROZEN_SHA` |
| C2-06 | Checkout failure không để voucher/Order/RESERVE treo; tombstone/release idempotent | Inventory before-after + operation receipt paired with redemption ledger | C2 ON + failure fixture được TV2/TV3 duyệt | `BLOCKED` |
| C2-07 | cancel/reject trước dispatch hoàn voucher đúng một lần; Return sau DELIVERED không hoàn lượt | state history, replay correlation, paired redemption ledger | C2 ON do TV2 duyệt; TV3 ledger | `BLOCKED` |
| C2-08 | Return COD dùng `SellerOrder.gross` sau giảm; không tự restock/chuyển tiền | Return response, voucher snapshot, gross/refund comparison | C2 ON + TV3 snapshot + TV5 oracle | `BLOCKED` |

## 3. Cách thu raw receipt cho một ca

1. Ghi run header một lần: frozen SHA, run ID, timezone `+07:00`, operator,
   Compose project, DB identity (không secret), image digest, loopback ports,
   fake actor IDs và flags thực tế.
2. Chụp/ghi `before` trước action: SellerOrder/Return/COD state và Inventory
   stock/operation ledger ở mức read-only.
3. Thực hiện đúng action của case; giữ request/response, correlation ID và
   timestamp. Không thao tác direct DB để tạo kết quả.
4. Ghi `after`, so với `before`, tính SHA-256 cho mỗi raw file và chỉ khi đó
   mới ghi `actual_result` cùng `PASS` hoặc `FAIL` vào manifest của case.
5. Khi thiếu provider receipt, quyền hoặc precondition thì tạo **blocker note**
   với dữ kiện thực tế, không tạo receipt PASS và không thử lại bằng
   operationId mới.

Mọi secret, JWT, password, địa chỉ/PII phải được bỏ khỏi raw artifact trước
khi đưa vào Git; giữ reference/redacted copy theo mẫu evidence intake TV5.

## 4. Ranh giới bắt buộc

- `UNKNOWN` claim-conflict và lost-response: `BLOCKED_DESIGN` / live
  `NOT_RUN`; không chạy fault injection hoặc fake provider receipt.
- RET-05 restock exactly-once: `BLOCKED`. `RECEIVED` không tự `INCREASE`,
  refund, đảo COD hay settlement. Cần caller credential, durable restock
  journal, provider receipt/lookup/recovery và Finance sign-off.
- C2-06/07/08 chỉ chạy khi C2 có đủ gate được phê duyệt; C2 OFF là “không áp
  dụng, chưa bật”, không phải PASS.
- Không dùng module/CI PASS hoặc ảnh UI để thay thế receipt runtime trên
  frozen SHA.

## 5. Known evidence (không phải Final UAT)

- PR #207: raw evidence TV4 có 66/66 hash khớp; focused JUnit scopes 313 test
  units, không failures/errors trong các scope được ghi nhận.
- PR #211: Backend readiness có Order PostgreSQL `156/156` PASS tại reviewed
  head `4f3152b`; PR #212: C2-on Playwright `25/25`, C2-off `24/24`, frontend
  `186/186` tại head `3483486`.

Các số trên là evidence theo đúng head/scope của chúng; chúng không được ghi
thành Final UAT PASS cho frozen main.

## 6. Handoff khi gate mở

TV4 cung cấp tuple/stock/operation/state/COD receipts theo bảng trên cho TV5;
TV5 kiểm manifest/hash/money oracle. TV3 cung cấp correlation Checkout/Order
và snapshot; TV2 cung cấp frozen environment, Auth/Store facts và quyết định
P0/flags. Thiếu bất kỳ owner receipt nào thì case ở trạng thái `BLOCKED` hoặc
`NOT_RUN` với lý do rõ ràng.
