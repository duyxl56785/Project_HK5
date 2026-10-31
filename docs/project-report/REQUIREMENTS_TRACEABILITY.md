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
| F14 | Thông báo trong ứng dụng; outbox; có DLT | PASS | Outbox Order đẩy Kafka, Notification tiêu thụ idempotent, bản ghi lỗi vào `.dlt`. `OrderEventKafkaIT` và `smoke-platform.mjs`: Kafka tắt vẫn đặt được đơn, thông báo đến sau khi Kafka bật lại |
| F15 | Đánh giá đã mua; ẩn/hiện; HIDDEN không tính điểm | PASS | Quyền đánh giá mở từ event DELIVERED trên `seller-order.status-changed.v1`, lưu `reviewable_items`. Smoke: bị từ chối trước khi giao, mở sau khi giao |
| F16 | Doanh thu, phí 5%, đối soát | PASS | E2E: thanh toán 142.500 ₫, tài chính sàn và cửa hàng khớp |
| F17 | Giao diện sàn, ảnh sản phẩm | PARTIAL | Giao diện Soopi: header tìm kiếm có gợi ý từ khóa, banner, ô danh mục, kệ sản phẩm, trang cửa hàng, Mua ngay; seed 24 sản phẩm có ảnh, kiểm 375px. Ảnh seed là ảnh vẽ tạm. Playwright chạy trên stack thật |

## Khác với thiết kế kiến trúc

| Thiết kế (§6, §9.3) | Hiện tại | Ảnh hưởng |
| --- | --- | --- |
| Kafka cho sự kiện | Order dùng Kafka cho hai topic trong §9.3. Sự kiện của Seller và lệnh Inventory vẫn qua outbox HTTP vì báo cáo không định nghĩa topic cho chúng | Không mất sự kiện khi service đích tắt |
| Eureka, Config Server | Đã dùng trong stack. Eureka lỗi hoặc rỗng thì Gateway và lời gọi nội bộ rơi về DNS Docker | Eureka khởi động lại không gây 503 |
| Redis cache sản phẩm | Cache Catalog công khai 10 giây, dùng lại bản cũ tối đa 5 phút khi phụ thuộc lỗi | Catalog có thể chậm tối đa 10 giây; ghi và snapshot luôn đọc nguồn |
| Nginx phục vụ frontend | Đã có image Nginx trong Compose, cổng `5173` | Không khác |
| React Hook Form + Zod | Đã dùng cho các form nhập liệu; ô lý do và số lượng một trường vẫn là input thường | Lỗi hiện dưới từng trường, server vẫn kiểm lại |
| Playwright | 10 test trên stack thật trong `frontend/e2e`, gồm đơn hai Store; chạy trong `frontend-e2e.yml`; không có bộ E2E giả lập API | Chỉ chứng minh luồng đã viết, chưa phủ reconciliation UNKNOWN |
| `seller_status_history`, `reviewed_by` | Có bảng lịch sử, Admin xem được. `reviewed_by` nằm ở lịch sử thay vì `seller_profiles` | Không khác về chức năng |
| Docker secrets | Secret nạp qua Docker secrets, sinh bằng `gen-env.mjs` thay vì `init-dev-secrets.ps1` | `.env.demo` vẫn giữ bản gốc cho seed và smoke |

## Khoảng trống so với báo cáo, đối chiếu 01/11/2026

Đối chiếu main `5280ac7` với §3.3, §3.4, §7.2, §9.1, §10 của báo cáo. Bảng trạng thái ở trên giữ nguyên; các mục dưới đây là phần báo cáo mô tả nhưng main chưa làm hoặc làm khác.

| Mục báo cáo | Hiện tại | Kiểm chứng | Owner |
| --- | --- | --- | --- |
| §3.4 người bán bị khóa vẫn giao tiếp đơn đã xác nhận, đơn chờ xác nhận chỉ được từ chối | Sau khi khóa, mọi thao tác đơn trả 403; đơn ACCEPTED kẹt tới khi mở khóa, Admin không xử lý thay được | Chạy thật | TV4, TV2 |
| §3.4 overallStatus có PROCESSING, COMPLETED | Đơn tổng chỉ có PLACED, CANCELLED; đơn đã giao và đối soát vẫn là PLACED | Chạy thật | TV3 |
| §3.4 sửa sản phẩm đang bán: nội dung quay về chờ duyệt, giá áp dụng ngay | Chỉ sửa được ở DRAFT, REJECTED, INACTIVE; phải ngừng bán rồi gửi duyệt lại, kể cả khi chỉ đổi giá | Đọc mã | TV2 |
| §9.1 `/unsuspend`, `/activate` cho sản phẩm | Không có; sản phẩm SUSPENDED không có đường quay lại | Đọc mã | TV2 |
| §3.2 F13, F17 Admin xử lý đơn và gắn ảnh house store | Có phí 0% cho house store, nhưng seed không tạo house store, không có API Admin tương ứng | Đọc mã | TV2, TV4 |
| §3.4 đặt hàng lỗi phải liệt kê từng món | Trả một thông báo cho món lỗi đầu tiên | Chạy thật | TV3 |
| UC-17 ẩn đánh giá có lý do | API và giao diện không nhận lý do | Đọc mã | TV5 |
| §9.1, §10.2 xóa cả giỏ có xác nhận | Chỉ xóa từng món | Đọc mã | TV3 |
| §10.3 mỗi trang tải lười; §10.2 banner dừng khi rê chuột | Tải một bundle; banner chỉ dừng theo `prefers-reduced-motion` | Đọc mã | TV2 |
| §9.1 đường dẫn API | Khác tên ở nhiều nhóm: `/api/catalog/products`, `/api/cart`, `/api/inventory`, `/api/admin/store-orders`, hành động `accept/prepare/ready/dispatch`. Không thiếu chức năng; báo cáo cần sửa theo mã | Đọc mã | TV5 |
| §7.2 bảng `settlements` | Đối soát lưu ngay trên `seller_orders`; đối soát đồng thời vẫn chỉ ghi một lần | Chạy thật | Không cần sửa mã |

## Chạy thử UAT, 01/11/2026

Chạy thử bằng API qua Gateway trên clone sạch main `5280ac7`, hai gate bật cục bộ, DB mới, script thủ công ngoài repo. Đây là bằng chứng thử, không thay UAT chính thức #18.

**54 PASS, 5 FAIL trong lượt chính.** Sau khi xét từng FAIL: 2 FAIL là hai dòng đầu bảng trên, 1 FAIL là dòng overallStatus, 1 FAIL do #138 chưa merge, 1 FAIL do script đọc sai số đơn (`size=100` không trả danh sách) nên kiểm tra số đơn sau replay được chạy lại riêng.

| Tình huống | Kết quả |
| --- | --- |
| 1 giỏ 2 Store tạo 1 đơn, 2 đơn con; replay cùng key cùng đơn, kho trừ một lần | PASS |
| 5 request đồng thời cùng key | 1 đơn, 4 lần 409; số đơn tăng đúng 1 (chạy lại riêng) |
| 5 khách tranh 3 hàng tồn | 3 thắng, 2 nhận 409 `Not enough stock`, tồn cuối 0, không 5xx |
| Hủy, từ chối hoàn kho | Hoàn đúng một lần; hủy lần hai 409; từ chối thiếu lý do bị chặn; hủy sau khi nhận đơn 409 |
| Vượt quyền | Seller khác 404, khách vào đơn seller 403, seller gọi COD 403 |
| Sai thứ tự | `deliver` từ PLACED 409, COD trước khi giao 409, đối soát trước COD 409 |
| 4 request COD, 4 request đối soát đồng thời | Mỗi loại đúng 1 thành công, còn lại 409, không 5xx |
| Phí sàn | 5% của gross, `sellerNet = gross - fee` |
| Đánh giá | Người lạ bị chặn, người mua đánh giá sau khi giao, lần hai 409, đánh giá ẩn không công khai |
| Người bán bị khóa | Sản phẩm rời Catalog, giỏ không nhận thêm, không nhận đơn mới; mở khóa xong xử lý đơn tiếp được |

Chưa phân loại: log order-service có 11 dòng `ERROR` trong lượt này; stack đã dọn trước khi đọc nội dung. Chưa chạy: tải lớn, UI thủ công cho từng tình huống.
