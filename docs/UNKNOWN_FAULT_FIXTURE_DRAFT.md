# Dự thảo fixture hai ca UNKNOWN

**Trạng thái:** DỰ THẢO của TV2, chưa chạy. Cần TV3, TV4, TV5 duyệt từng ca trước khi làm. Dựa trên [đề xuất của TV4](https://github.com/duyxl56785/Project_HK5/pull/147#issuecomment-6580170816) và [handoff của TV3](https://github.com/duyxl56785/Project_HK5/issues/18#issuecomment-6581596647). Refs #18.

Mục tiêu là chứng minh đúng hành vi đã có: khi không biết Inventory đã xử lý hay chưa, Order giữ UNKNOWN, không tạo operation ID mới, không tự RELEASE, Cart vẫn bị khóa. Không dùng phản hồi giả, 409 giả, hay sửa DB để ép PASS. Nếu không tạo được lỗi thật thì ghi `BLOCKED_DESIGN`.

## Đề xuất stack

| Mục | Đề xuất |
|---|---|
| Compose project | `tv3-live-unknown-<ngày>`, volume riêng, `down -v` sau khi giữ evidence |
| Cổng | `127.0.0.1:5173`, `127.0.0.1:18080`, chỉ chạy khi không có stack nào khác |
| Source | SHA `main` tại lúc chạy, ghi vào evidence |
| Dữ liệu | Tài khoản `@hk5.local` riêng, không có dữ liệu thật |
| Finance | Không ghi COD, không settle |

## Điểm chèn lỗi

`order-service` gọi Inventory qua `INVENTORY_INTERNAL_URL`. Có thể đặt một proxy TCP giữa hai service bằng file compose override chỉ dùng cho fixture, trỏ biến này sang proxy. Không đổi code production, không thêm API lỗi. Proxy là phụ thuộc mới của fixture, TV4 cần duyệt.

## Ca 1: timeout, mất phản hồi

1. Bật proxy chế độ chuyển request tới Inventory, nhưng bỏ phản hồi về.
2. Customer checkout. Inventory nhận và commit RESERVE, Order timeout.
3. Kiểm tra: journal `UNKNOWN`, attempt chờ đối soát, tồn đã giữ đúng một lần, Cart còn khóa, không có RELEASE.
4. Giữ lỗi đang bật, replay cùng key và body. Kiểm tra ID, tồn, Cart không đổi.

Receipt chấp nhận của provider là dòng `operation_id` trong `inventory_db`, đọc bằng giao dịch chỉ đọc.

## Ca 2: claim-conflict

`InventoryOperationTransport` xếp 409 claim-conflict vào UNKNOWN. Phía Inventory, `InventoryOperationService` chỉ trả `IDEMPOTENCY_CLAIM_CONFLICT` khi `claimOperationId` trả 0 mà `findById` không thấy dòng nào. Nếu thấy dòng thì đi nhánh replay hoặc từ chối.

**Chưa có cách tạo thật, đề xuất ghi `BLOCKED_DESIGN` cho tới khi TV4 có cơ chế.** TV3 yêu cầu claim cạnh tranh thật, không sửa DB. Khoảng thời gian gây ra nhánh này rất hẹp, và request phải khóa dòng hàng trước khi claim. Proxy không chạm được vào nhánh đó. TV4 cần xác nhận có tạo được không mà không thêm code lỗi vào runtime.

## Evidence

Cho từng ca, theo yêu cầu của `frontend/src/features/checkout/acceptance/README.md`:

- File case riêng cho `TV3_E2E_CLAIM_CASE_FILE`, `TV3_E2E_TIMEOUT_CASE_FILE`, gồm `kind`, `attemptId`, `operationId`, `key`, `body`, `lines`, `evidenceFile`, `evidenceSha256`, `faultStillActive`, `settledBeforeReplay`. File này không commit nếu chứa thông tin bí mật.
- Trước và sau: attempt, draft, journal, outbox của Order. Operations, reservation, available và reserved của Inventory. Cart và fingerprint.
- SHA source, SHA fixture, SHA256 đầy đủ. Bỏ PII, key, secret.

## Cần duyệt

| Việc | Người duyệt | Hiện trạng |
|---|---|---|
| Duyệt QA từng fixture | TV5 | Chờ |
| Cơ chế proxy và claim thật | TV4 | Chờ |
| Stack, cổng, runner, source đúng SHA | TV2 | Chờ bản cuối của tài liệu này |
| Giao thức | TV3 | Đã chấp nhận giao thức, chưa duyệt chạy |

Nhóm cũng cần chốt hai ca này là blocker của release hay backlog công khai cho bản demo.
