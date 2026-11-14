# Contract C1 — Voucher Store và hạn mức FR-VC-05

**Trạng thái:** ĐÃ KÝ ngày 13/11/2026 bởi TV2 và TV3 (mục 11). Sửa đổi 14/11/2026: TV3 làm toàn bộ C1, TV2 chỉ review và merge (mục 2, 9, 10, 11). Chưa triển khai; file được commit vào repo ở PR đầu tiên của TV3.
**Nguồn:** SRS v2.1 ngày 13/11/2026 (mục 4.4, 4.5, test VL-01 đến VL-07), đối chiếu `main@478cfc0`.
**Thời điểm triển khai:** code trên branch từ 14/11/2026; merge vào `main` trước khi đóng băng hết ngày 19/11/2026. Không merge gì vào `main` trong giai đoạn đóng băng (20/11 đến khi nộp 21/11).
**File đích khi commit:** `docs/FR_VC05_VOUCHER_C1_CONTRACT.md`. File này được commit trong PR đầu tiên của TV3.

## 1. Phạm vi

**C1 gồm:**
- Seller tạo, sửa và chuyển trạng thái voucher của Store mình.
- Hạn mức mặc định 20 chương trình.
- Seller xin mở rộng hạn mức; Admin duyệt hoặc từ chối.
- Hết hạn grant, audit và UI cho Seller và Admin.

**C1 không gồm:**
- Áp voucher vào Cart hoặc Checkout.
- Trừ hoặc hoàn lượt dùng.
- Tiền trên SellerOrder và phí sàn. Các phần này thuộc C2, cần M01 mới và TV5 review.

**Không làm, ngoài mọi giai đoạn của bản đầu:**
- Voucher toàn sàn, miễn ship, combo, khách mới.
- Cộng dồn voucher với Flash Sale hoặc xả kho.
- Thông báo tự động.
- Thu hồi grant sớm.
- Chương trình khuyến khích hằng tháng (rule 9).
- Mọi nghĩa vụ "đủ 20 voucher mỗi tháng".

**Ghi chú phạm vi:** `docs/COD_MVP_21_DAY_PLAN.md` xếp voucher ngoài phạm vi MVP. C1 là phần mở rộng sau MVP. Nếu C1 merge trước khi đóng băng, báo cáo nộp 21/11 ghi C1 là phần đã làm; nếu không, chỉ ghi là hướng phát triển.

## 2. Vị trí và ownership

| Phần | Module | Người làm | Người review |
|---|---|---|---|
| Contract, `docs/OWNERSHIP.md` | `docs/` | TV3 | TV2 (owner) |
| Bảng hạn mức, yêu cầu mở rộng, API duyệt | `seller-service`, package `voucherlimit` | TV3 | TV2 (owner) |
| Bảng voucher, trạng thái, kiểm quota | `seller-service`, package `voucher` | TV3 | TV2 (owner) |
| Route gateway `/api/admin/voucher-limit-requests` | `api-gateway` | TV3 | TV2 (owner) |
| UI Seller (voucher, hạn mức, yêu cầu) và UI Admin (hàng đợi) | `frontend/src/features/voucher` | TV3 | TV2 (owner) |

**Quy tắc chung cho cả hai phần:**
- Cả hai phần chạy trong cùng DB và cùng transaction của `seller-service`. Không có outbox giữa voucher và hạn mức.
- Migration Flyway dành riêng: `V9` (hạn mức và yêu cầu) và `V10` (voucher), cả hai do TV3 làm. Số tiếp theo phải thống nhất trong PR. Không ai tự thêm `V9`/`V10` cho việc khác.
- `/api/stores/**` đã route sang `seller-service`. Chỉ route Admin mới cần thêm vào gateway.

## 3. Thời gian

- Lưu mọi mốc thời gian bằng `TIMESTAMPTZ`.
- API nhận và trả ISO-8601 có offset, ví dụ `2026-11-20T00:00:00+07:00`.
- UI hiển thị theo `Asia/Ho_Chi_Minh`.
- Mọi mốc thời gian được so với `now()` của server trong transaction. Bỏ qua giờ client.
- Không dùng cron để đổi trạng thái. SCHEDULED, ACTIVE và EXPIRED được suy ra lúc đọc và lúc ghi.

## 4. Voucher

### 4.1 Trường

| Trường | Ràng buộc |
|---|---|
| `id` | UUID |
| `storeId` | lấy từ JWT qua Store của Seller. Không nhận từ body |
| `code` | 4–20 ký tự `A-Z0-9`, server chuyển thành chữ hoa, duy nhất trong Store (kể cả CANCELLED) |
| `name` | 1–120 ký tự |
| `discountType` | `FIXED` hoặc `PERCENT` |
| `discountValue` | FIXED: số nguyên VND > 0. PERCENT: số nguyên 1–90 |
| `maxDiscount` | bắt buộc khi PERCENT, số nguyên VND > 0. Phải để trống khi FIXED |
| `minOrderAmount` | số nguyên VND ≥ 0. Với FIXED phải ≥ `discountValue` |
| `usageLimit` | số nguyên 1–100000. C1 chỉ lưu, chưa dùng |
| `startsAt`, `endsAt` | `endsAt > startsAt`. Thời lượng tối đa 90 ngày |
| `status` | xem 4.2 |
| `version` | dùng cho optimistic lock, gửi lại qua `expectedVersion` |

### 4.2 Trạng thái

**Trạng thái lưu trong DB:** `DRAFT`, `PUBLISHED`, `PAUSED`, `CANCELLED`.

**Trạng thái API trả về** (suy ra từ trạng thái lưu và `now`):

| Trạng thái lưu | Điều kiện | Trạng thái API |
|---|---|---|
| DRAFT | — | `DRAFT` |
| PUBLISHED | `now < startsAt` | `SCHEDULED` |
| PUBLISHED | `startsAt ≤ now < endsAt` | `ACTIVE` |
| PUBLISHED hoặc PAUSED | `now ≥ endsAt` | `EXPIRED` |
| PAUSED | `now < endsAt` | `PAUSED` |
| CANCELLED | — | `CANCELLED` |

Voucher không có REJECTED vì Admin không duyệt từng voucher.

### 4.3 Chuyển trạng thái

| Hành động | Từ (API) | Sang | Kiểm quota | Điều kiện Seller/Store |
|---|---|---|---|---|
| tạo | — | DRAFT | không | APPROVED + OPEN |
| sửa | DRAFT | DRAFT | không | APPROVED + OPEN |
| `publish` | DRAFT | SCHEDULED/ACTIVE | **có** | APPROVED + OPEN, `endsAt > now` |
| `pause` | SCHEDULED, ACTIVE | PAUSED | không | không chặn |
| `resume` | PAUSED | SCHEDULED/ACTIVE | **có** | APPROVED + OPEN, `endsAt > now` |
| `cancel` | DRAFT, SCHEDULED, ACTIVE, PAUSED | CANCELLED | không | không chặn |

- Chỉ DRAFT được sửa. Voucher đã publish không sửa nội dung trong C1.
- Voucher EXPIRED và CANCELLED là trạng thái cuối.
- Pause và cancel không bị chặn khi Seller SUSPENDED, để Seller luôn tự rút được ưu đãi.
- Mỗi lần chuyển trạng thái ghi một dòng `voucher_status_history`: `voucher_id`, `from_status`, `to_status`, `actor`, `created_at`.

## 5. Hạn mức

### 5.1 Đếm

- `used(now)` là số voucher của Store có trạng thái lưu `PUBLISHED` và `endsAt > now`, tức SCHEDULED và ACTIVE.
- DRAFT, PAUSED, EXPIRED và CANCELLED không chiếm slot.
- Mỗi chương trình tính 1 slot, không phụ thuộc `usageLimit`.

### 5.2 Grant

- Mỗi Store có tối đa một grant tại một thời điểm. Grant được lưu ở `store_voucher_limits`, khóa chính là `store_id`.
- Các trường: `granted_limit` (21–50), `valid_from`, `valid_until`, `source_request_id`, `decided_by`, `decided_at`, `version`.
- Grant **có hiệu lực** khi `valid_from ≤ now < valid_until`.
- Approve mới ghi đè grant cũ, không cộng dồn. Grant cũ được lưu lại qua lịch sử của yêu cầu.
- `effectiveLimit(now)` bằng `granted_limit` khi grant có hiệu lực, ngược lại bằng 20. Mức 20 là hằng số trong code, chưa có API để sửa.

### 5.3 Quy tắc publish và resume

Kiểm theo thứ tự sau, trong một transaction:

1. Khóa dòng `stores` của Store bằng `SELECT … FOR UPDATE`. Dòng này luôn tồn tại, nên cả publish, resume và approve đều khóa cùng một dòng.
2. Đếm `used(now)`, không tính voucher đang chuyển trạng thái.
3. Nếu `used + 1 ≤ 20`, cho phép.
4. Nếu không, cho phép chỉ khi đủ cả ba điều kiện:
   - grant đang có hiệu lực;
   - `used + 1 ≤ granted_limit`;
   - `startsAt < valid_until`.
   
   Theo SRS 4.5.3, không dùng slot mở rộng cho voucher bắt đầu sau khi grant hết hạn. `endsAt` được phép vượt `valid_until`.
5. Còn lại: trả `409 VOUCHER_QUOTA_EXCEEDED`, body có `used` và `limit`.

**Hệ quả** (VL-05):
- Khi grant hết hạn mà Store còn 24 voucher, cả 24 tiếp tục đến `endsAt` của chính chúng.
- Publish và resume mới bị chặn cho đến khi `used < 20`.
- Không có voucher nào bị xóa, hủy hoặc pause tự động.

**Grant tương lai:** grant có `valid_from > now` chưa được tính. Seller phải chờ đến `valid_from` mới publish được voucher thứ 21.

## 6. Yêu cầu mở rộng

### 6.1 Trường

| Trường | Ràng buộc |
|---|---|
| `requestedLimit` | số nguyên 21–50 |
| `reason` | 1–500 ký tự |
| `budgetNote` | tùy chọn, tối đa 500 ký tự |
| `requestedFrom`, `requestedUntil` | `requestedUntil > requestedFrom`, `requestedUntil > now`, thời lượng tối đa 30 ngày |
| `status` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `approvedLimit`, `approvedFrom`, `approvedUntil` | chỉ có khi APPROVED |
| `decidedBy`, `decidedAt`, `decisionReason` | khi APPROVED hoặc REJECTED |
| `version` | dùng cho optimistic lock |

C1 không có danh sách Product hoặc SKU có cấu trúc. Lý do: ownership của Product nằm ở `product-service`, nên phải gọi chéo service mới kiểm được Product có thuộc Store không. Seller ghi Product liên quan vào `reason`. Danh sách có cấu trúc để sau.

### 6.2 Ràng buộc

- Mỗi Store có tối đa 1 yêu cầu PENDING, đảm bảo bằng unique partial index `(store_id) WHERE status = 'PENDING'`.
  - Gửi yêu cầu thứ hai: `409 VOUCHER_LIMIT_REQUEST_PENDING_EXISTS`.
- Bắt buộc header `Idempotency-Key`, unique theo `(store_id, idempotency_key)`.
  - Cùng key, cùng payload: trả lại đúng yêu cầu cũ với `200`.
  - Cùng key, khác payload: `409 IDEMPOTENCY_KEY_REUSED`.
- Tạo yêu cầu chỉ được khi Seller APPROVED và Store OPEN.

### 6.3 Quyết định

| Hành động | Ai | Từ | Điều kiện |
|---|---|---|---|
| `cancel` | Seller của Store | PENDING | `expectedVersion` |
| `approve` | ADMIN | PENDING | xem bên dưới |
| `reject` | ADMIN | PENDING | `reason` 1–500 ký tự. Được phép cả khi Seller SUSPENDED |

**Điều kiện approve:**
- `21 ≤ approvedLimit ≤ requestedLimit`.
- `requestedFrom ≤ approvedFrom < approvedUntil ≤ requestedUntil`.
- `approvedUntil > now`.
- Bắt buộc có `reason` khi duyệt thấp hơn số xin hoặc rút ngắn thời hạn.
- Seller đang APPROVED và Store đang OPEN. Nếu không: `409 VOUCHER_LIMIT_REQUEST_INVALID_STATE`.
- Admin sở hữu chính Store đó không được tự duyệt: `403 SELLER_SELF_MODERATION`.
- Trong cùng transaction: khóa dòng `stores`, ghi đè `store_voucher_limits`, cập nhật yêu cầu, ghi lịch sử.

**Tranh chấp:**
- Cancel, approve và reject cùng dùng `expectedVersion`.
- Hai thao tác đồng thời: chỉ một thao tác thành công. Thao tác còn lại nhận `409 VOUCHER_LIMIT_REQUEST_VERSION_CONFLICT`, hoặc `INVALID_STATE` nếu yêu cầu đã rời khỏi PENDING.

**Lịch sử:** bảng `voucher_limit_request_history` theo mẫu `seller_status_history`: `request_id`, `from_status`, `to_status`, `reason`, `actor`, `created_at`.

## 7. API

Seller dùng role `SELLER`; Store lấy từ JWT, như `/api/stores/me` đang có. Admin dùng role `ADMIN` thật. Mọi body ghi đều dùng `expectedVersion` giống `SellerStoreJson`, và từ chối trường lạ.

**Seller:**

| Method và path | Mô tả |
|---|---|
| `GET /api/stores/me/vouchers?status=&page=&size=` | danh sách, `size` tối đa 50 |
| `POST /api/stores/me/vouchers` | tạo DRAFT, trả `201` |
| `GET /api/stores/me/vouchers/{id}` | chi tiết |
| `PUT /api/stores/me/vouchers/{id}` | sửa DRAFT |
| `POST /api/stores/me/vouchers/{id}/{action}` | `action` là `publish`, `pause`, `resume` hoặc `cancel` |
| `GET /api/stores/me/voucher-limit` | `{ used, limit, defaultLimit: 20, grant: {...} \| null }` |
| `GET /api/stores/me/voucher-limit-requests?page=&size=` | danh sách yêu cầu của Store, gồm lý do bị từ chối |
| `POST /api/stores/me/voucher-limit-requests` | tạo yêu cầu, cần `Idempotency-Key`, trả `201` |
| `POST /api/stores/me/voucher-limit-requests/{id}/cancel` | hủy PENDING |

**Admin:**

| Method và path | Mô tả |
|---|---|
| `GET /api/admin/voucher-limit-requests?status=PENDING&page=&size=` | hàng đợi, sắp xếp `created_at` tăng dần |
| `GET /api/admin/voucher-limit-requests/{id}` | chi tiết, kèm `used` hiện tại của Store |
| `POST /api/admin/voucher-limit-requests/{id}/{action}` | `action` là `approve` hoặc `reject` |

**Khác với SRS 4.5.4:** SRS đề xuất `/api/seller/voucher-limits/me`. Contract đổi sang `/api/stores/me/...` để khớp `seller-service` hiện tại, và không phải thêm route gateway cho phía Seller.

**Không có route `/internal/...` cho voucher trong C1.**

## 8. Mã lỗi

Định dạng lỗi giống `SellerWebConfig.error(...)`.

| HTTP | Code | Khi nào |
|---|---|---|
| 400 | `VALIDATION_FAILED` | sai trường, trường lạ, sai khoảng giá trị |
| 401 | `AUTH_ACCESS_REQUIRED` | thiếu hoặc sai token (lớp security sẵn có) |
| 403 | `AUTH_FORBIDDEN` | sai role |
| 403 | `SELLER_NOT_APPROVED` | Seller không ở APPROVED với hành động cần APPROVED |
| 403 | `SELLER_SELF_MODERATION` | Admin tự duyệt Store của mình |
| 404 | `STORE_NOT_FOUND` | Seller chưa có Store |
| 404 | `VOUCHER_NOT_FOUND` | không có, hoặc thuộc Store khác (không lộ là có tồn tại) |
| 404 | `VOUCHER_LIMIT_REQUEST_NOT_FOUND` | như trên |
| 409 | `STORE_CLOSED` | Store CLOSED với hành động cần OPEN |
| 409 | `VOUCHER_QUOTA_EXCEEDED` | vượt hạn mức, body có `used`, `limit` |
| 409 | `VOUCHER_INVALID_STATE` | chuyển trạng thái không hợp lệ hoặc `endsAt ≤ now` |
| 409 | `VOUCHER_CODE_EXISTS` | trùng `code` trong Store |
| 409 | `VOUCHER_VERSION_CONFLICT` | sai `expectedVersion` |
| 409 | `VOUCHER_LIMIT_REQUEST_PENDING_EXISTS` | đã có PENDING |
| 409 | `VOUCHER_LIMIT_REQUEST_INVALID_STATE` | yêu cầu không còn PENDING, hoặc Seller/Store không hợp lệ khi approve |
| 409 | `VOUCHER_LIMIT_REQUEST_VERSION_CONFLICT` | sai `expectedVersion` |
| 409 | `IDEMPOTENCY_KEY_REUSED` | cùng key, khác payload |

## 9. Test bắt buộc trước khi gọi C1 là DONE

Các test cần PostgreSQL 16 chạy bằng Testcontainers, cùng kiểu `seller-service:integrationTest`.

| ID | Nội dung | Người làm |
|---|---|---|
| VL-01 | 19/20: publish thành công; voucher thứ 21 nhận `VOUCHER_QUOTA_EXCEEDED`; DRAFT và PAUSED không chiếm slot | TV3 |
| VL-02 | 2 publish đồng thời ở 19/20: đúng 1 thành công, sau commit `used ≤ 20`. Lặp ≥ 20 lần | TV3 |
| VL-02b | publish đồng thời với Admin approve: không vượt mức của grant đã commit | TV3 |
| VL-03 | xin 30: PENDING không tăng mức; approve 25 trong thời hạn thì limit = 25, không sinh voucher | TV3 |
| VL-04 | reject, hoặc approve thấp hơn số xin, mà thiếu lý do: `400`; có lịch sử; Seller gọi API Admin: `403`; Seller Store khác: `404` | TV3 |
| VL-05 | grant hết hạn khi còn 24 voucher: cả 24 vẫn chạy; publish/resume mới bị chặn tới khi `used < 20`; voucher bắt đầu sau `valid_until` không được dùng slot mở rộng | TV3 |
| VL-06 | Seller SUSPENDED hoặc Store CLOSED: chặn tạo yêu cầu, tạo, publish, resume và approve; pause, cancel và reject vẫn được | TV3 |
| VL-07 | không có ràng buộc tối thiểu số voucher; cùng `Idempotency-Key` không tạo yêu cầu trùng; khác payload nhận `IDEMPOTENCY_KEY_REUSED` | TV3 |
| VL-08 | grant mới ghi đè grant cũ, không cộng dồn; grant tương lai chưa có hiệu lực | TV3 |
| VL-09 | resume PAUSED khi đã đủ hạn mức: `VOUCHER_QUOTA_EXCEEDED` | TV3 |
| VL-10 | Seller cancel đồng thời với Admin approve: đúng 1 thành công | TV3 |
| VL-11 | trạng thái suy ra đúng ở mốc `startsAt` và `endsAt` (dùng `Clock` cố định) | TV3 |

Kèm theo: unit test cho validation, test 401/403 cho từng route, test frontend cho trang Seller và trang Admin, và một kịch bản E2E (tạo voucher, xin mở rộng, Admin duyệt, publish voucher thứ 21).

## 10. Còn mở, cần chốt trước khi code

1. ~~Ownership~~ Đã chốt 14/11/2026: TV3 làm toàn bộ C1 (backend, gateway, UI, E2E). TV2 chỉ review và merge với vai trò owner `seller-service`.
2. `endsAt` của voucher dùng slot mở rộng được phép vượt `valid_until`, theo SRS 4.5.3 và VL-05. TV3 từng đề xuất bắt cả `endsAt` nằm trong grant. Contract chọn theo SRS.
3. Giới hạn số: tối đa 50 slot, yêu cầu tối đa 30 ngày, voucher tối đa 90 ngày, PERCENT 1–90. Đổi được trước khi code, không đổi sau khi merge `V9`/`V10`.
4. Tiền ở C2: M01 mới cần TV5 review. Không có gì trong C1 phụ thuộc mục này.
5. Seller truy cập voucher hoặc yêu cầu của Store khác nhận `404`, để không lộ là bản ghi có tồn tại. SRS VL-04 ghi `403`. Contract chọn `404`. Seller gọi API Admin vẫn nhận `403`.

## 11. Ký xác nhận

| Thành viên | Vai trò | Ngày | Kết quả |
|---|---|---|---|
| TV2 Nguyễn Văn Duy | owner `seller-service`, contract | 13/11/2026 | ACCEPTED (TV2, owner `seller-service`) |
| TV3 Trần Văn Chính | backend voucher và quota | 13/11/2026 | [TV3 ACCEPTED 13/11/2026](https://github.com/duyxl56785/Project_HK5/issues/199#issuecomment-6853440098) |
| Sửa đổi 14/11/2026: TV3 làm toàn bộ C1 | TV2 | 14/11/2026 | ACCEPTED (TV2) |
| Sửa đổi 14/11/2026: TV3 làm toàn bộ C1 | TV3 | | chờ TV3 xác nhận trên #199 |
