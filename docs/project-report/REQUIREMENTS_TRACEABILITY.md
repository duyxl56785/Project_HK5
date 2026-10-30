# Đối chiếu yêu cầu V2 với mã nguồn

Nguồn yêu cầu: `Bao_cao_phan_tich_thiet_ke_Smart_ECommerce_V2_FINAL.docx` (12/10/2026), Product Backlog §2.2, kiến trúc §6, bản đồ màn hình §10.1. File gốc không nằm trong nhánh này.

Trạng thái theo PR #135, xác minh ngày 30/10/2026 trên stack Docker thật qua gateway `127.0.0.1:18080`. Kết quả chi tiết ghi trong mô tả PR. Bảng này không thay cho review của owner và UAT sau khi merge.

- **PASS**: đã chạy thật qua HTTP, có test tự động cho phần lõi.
- **PARTIAL**: chức năng chạy được, còn khác thiết kế ở điểm được nêu.

| Mã | Tiêu chí chính | Trạng thái | Bằng chứng |
| --- | --- | --- | --- |
| F01 | Đăng ký, đăng nhập, refresh, logout; chặn token sai | PASS | Silent refresh, token giả trả 401, refresh tuần tự giữa các tab bằng Web Locks |
| F02 | Hồ sơ và địa chỉ; tối đa một mặc định | PASS | `AddressBookDatabaseTest` trên schema thật, unique index |
| F03 | Hồ sơ PENDING; REJECTED sửa và nộp lại; chống trùng | PASS | Smoke: thiếu trường 400, trùng 409, nộp lại với thông tin đã sửa |
| F04 | Duyệt, từ chối, khóa, mở khóa; có lý do; đổi role | PASS | Smoke: khóa làm ẩn cửa hàng và chặn ghi bằng token cũ, mở khóa khôi phục |
| F05 | Một cửa hàng mỗi người bán; slug duy nhất | PASS | Ràng buộc DB, sửa cửa hàng người khác trả 403 |
| F06 | SKU toàn sàn; 1–5 ảnh ≤ 2 MB; đủ điều kiện mới gửi duyệt | PASS | Trùng SKU trả 409, E2E tải ảnh |
| F07 | Kiểm duyệt sản phẩm; danh mục không chu trình | PASS | Xóa danh mục đang dùng trả 409 |
| F08 | Lọc, sắp xếp, phân trang ở backend; chỉ hàng bán được | PASS | Catalog lọc theo Product ACTIVE, Seller APPROVED, Store OPEN |
| F09 | Giỏ nhiều cửa hàng; revision; khóa khi checkout | PASS | E2E, revision cũ trả 409 |
| F10 | Một đơn tổng, N đơn con; idempotent | PASS | E2E 2 cửa hàng |
| F11 | Người bán xử lý đơn của mình | PASS | E2E; xem đơn cửa hàng khác trả 404 |
| F12 | Khách theo dõi, hủy đơn chờ; hoàn kho một lần | PASS | Smoke: hủy lần hai 409, tồn kho hoàn đúng một lần qua outbox |
| F13 | COD chỉ PAID sau DELIVERED; idempotent | PASS | E2E, tài chính khớp |
| F14 | Thông báo trong ứng dụng; outbox; có DLT | PARTIAL | Outbox và retry qua HTTP, hàng chờ lỗi xem ở Admin; chưa dùng Kafka |
| F15 | Đánh giá đã mua; ẩn/hiện; HIDDEN không tính điểm | PARTIAL | Chạy thật; quyền đánh giá kiểm tra trực tiếp ở Order thay vì qua event |
| F16 | Doanh thu, phí 5%, đối soát | PASS | E2E: thanh toán 142.500 ₫, tài chính sàn và cửa hàng khớp |
| F17 | Giao diện sàn, ảnh sản phẩm | PARTIAL | Có catalog, chi tiết, trang cửa hàng; chưa có banner, kệ sản phẩm, gợi ý từ khóa |

## Khác với thiết kế kiến trúc

| Thiết kế (§6, §9.3) | Hiện tại | Ảnh hưởng |
| --- | --- | --- |
| Kafka cho sự kiện | Outbox gọi HTTP nội bộ, có retry và bản ghi lỗi | Không mất sự kiện khi service đích tắt; không có topic `.dlt` thật |
| Eureka, Config Server | Có module, stack chạy không dùng; service gọi nhau theo tên Docker | Không ảnh hưởng chức năng |
| Redis cache sản phẩm | Không dùng | Catalog đọc trực tiếp DB và trạng thái Seller hiện tại |
| Nginx phục vụ frontend | Vite dev server | Chưa có image frontend |
| React Hook Form + Zod | Form React thuần | Lỗi hiện theo ràng buộc HTML và thông báo server |
| Playwright | E2E thủ công trên trình duyệt | Chưa có E2E tự động |
| `seller_status_history`, `reviewed_by` | Chỉ lưu lý do mới nhất | Không xem được lịch sử duyệt |
