# Dự thảo chính sách giữ dữ liệu Checkout draft (V5)

**Trạng thái:** DỰ THẢO của TV2, chờ TV5, TV3 và người phụ trách DB duyệt. Chưa có hiệu lực, chưa có code. Dữ liệu giả của demo local không cần chính sách này. Refs #18.

## Hiện trạng

- `checkout_order_drafts` (migration V5) giữ cùng thông tin giao hàng với Order: tên, số điện thoại, địa chỉ, trong cột `payload`.
- `CheckoutDraftJournal` chỉ thêm và đọc. Không có job hay lệnh nào xóa draft, kể cả khi Checkout đã thành công.
- Draft có khóa ngoại tới `checkout_attempts`. `inventory_operation_journal` cũng tham chiếu `checkout_attempts`.
- TV4 chỉ xác nhận truy cập phục hồi phải theo Store hoặc đơn và không được thành đường vòng vào dữ liệu Finance.

Đề xuất dưới đây là con số để bàn. Không có quy định pháp lý nào đã được kiểm tra cho các con số này.

## Đề xuất

| Trạng thái attempt | Giữ draft bao lâu | Lý do |
|---|---|---|
| Thành công, đã có Order | 7 ngày kể từ khi thành công | Đủ để replay cùng key và điều tra. Thông tin giao hàng đã nằm trong Order |
| Thất bại chắc chắn, không giữ hàng | 7 ngày | Không còn gì để phục hồi |
| `PROCESSING` | Không xóa | Đang chạy |
| `NEEDS_RECONCILIATION` hoặc journal `UNKNOWN` | Không xóa cho tới khi vận hành đối soát xong, sau đó 30 ngày | Draft là bằng chứng để đối soát |

**Dọn draft.** Job định kỳ trong `order-service`, xóa từng lô nhỏ, chỉ xóa draft của attempt đã đến hạn. Ghi số dòng đã xóa vào log, không ghi nội dung.

**Thứ tự xóa.** Xóa dòng trong `checkout_order_drafts` trước. Giữ `checkout_attempts` và `inventory_operation_journal` vì hai bảng này không chứa thông tin giao hàng và là dấu vết chống trùng. Không bao giờ xóa khi journal còn `PENDING` hoặc `UNKNOWN`.

**Quyền truy cập.** Chỉ user DB của `order-service` đọc và ghi bảng này. Không cấp cho user báo cáo hay user chỉ đọc chung. Không API nào trả draft ra ngoài. Báo cáo Finance không join bảng này.

**Audit.** Người vận hành đọc draft khi đối soát phải có ghi nhận: ai, khi nào, attempt nào. Không ghi nội dung draft vào log.

**Backup và PITR.** Dữ liệu đã xóa vẫn còn trong backup tới khi backup hết hạn, nên thời gian giữ backup không được dài hơn 30 ngày. Sau khi restore hoặc PITR, chạy job dọn trước khi mở lại traffic để không làm sống lại draft đã hết hạn.

## Cần chốt

| Câu hỏi | Người chốt |
|---|---|
| Các con số 7 và 30 ngày | TV5, TV2 |
| Replay cùng key sau khi draft bị xóa có còn đúng không | TV3 |
| Trình tự xóa và đổi schema nếu có | TV3, người phụ trách DB |
| Quyền DB, audit, backup trên môi trường thật | Người phụ trách DB |

Sau khi duyệt, phần code là một PR riêng có test Postgres. Dự thảo này không tự mở gate production nào.
