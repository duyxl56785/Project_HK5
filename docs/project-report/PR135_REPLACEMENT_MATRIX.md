# Đối chiếu PR đã đóng với PR #135

Ma trận này trả lời yêu cầu trong review của TV1 trên PR #135: mỗi PR liên quan được thay bằng gì, phần nào chưa được thay và ai quyết định.

Kiểm tra ngày 30/10/2026 tại head `feat/tv2-demo-mvp`. Các commit của #122 đến #134 không nằm trong lịch sử của #135 (`git cherry` báo 50 đến 69 patch chưa có). PR #135 làm lại chức năng theo cách đơn giản hơn, không gộp code cũ. Vì vậy "Đã thay" dưới đây nghĩa là cùng mục tiêu chức năng, không phải cùng cách làm.

Các nhánh của PR đã đóng vẫn còn trên remote, có thể mở lại.

| PR | Trạng thái | Nội dung gốc | Trong #135 | Kết luận | Owner quyết định |
|---|---|---|---|---|---|
| #80 | OPEN, TV1 | Nền tảng JWT HS256 và current authority | Có `JwtResourceServer`, `CurrentAuthorityFilter` theo cách khác | Chồng lấn, chưa xử lý. PR vẫn mở | TV1 |
| #102 | OPEN, TV1 | Luồng HTTP Auth theo giao dịch | Có Auth HTTP từ 9073ad3 | Chồng lấn, chưa xử lý. PR vẫn mở | TV1 |
| #119 | CLOSED | Cấu hình `api.version` cho Testcontainers | `backend/build.gradle` có sẵn cấu hình này | Đã thay | TV2 |
| #122 | CLOSED | Nhận role sync từ Seller qua `POST /internal/auth/seller-role-sync`, mặc định tắt | `PUT /internal/accounts/{id}/roles/SELLER`, chặn sự kiện cũ theo revision (d7d52c6) | Đã thay chức năng. Không có credential hiện tại/trước đó và kiểm tra mã hóa chặt như #122 | TV1 |
| #125 | CLOSED | `POST /internal/seller/store-facts/batch` có giới hạn phạm vi | `POST /internal/stores/facts` dùng `X-Internal-Token` | Đã thay chức năng. Chưa có phân quyền theo từng cặp dịch vụ và scope như #125 | TV2 |
| #128 | CLOSED | Đọc của chủ Seller/Store kiểm tra authority hiện tại | `/api/sellers/me`, `/api/stores/me` theo chủ sở hữu trong DB | Một phần. Phép đọc vẫn tin token cho tới khi token hết hạn | TV2 |
| #129 | CLOSED | Không dùng snapshot 5 giây để cho phép ghi | `CurrentAuthorityFilter` hỏi Auth trước mọi phép ghi (eacc2c0) | Đã thay ở tầng token. Trạng thái Seller/Store được kiểm tra trong nghiệp vụ | TV2 |
| #130, #131 | CLOSED | Reservation bền vững và RPC có xác thực giữa Auth, Seller, Product | Không có | **Chưa thay** | TV1, TV2 |
| #132, #133 | CLOSED | Khôi phục reservation sau crash | Không có | **Chưa thay** | TV1, TV2 |
| #134 | CLOSED | Hàng đợi fencing cho thao tác Auth và Seller | Có fencing revision cho role sync. Không có hàng đợi mutation | Một phần | TV1 |

## Rủi ro còn lại

`CurrentAuthorityFilter` kiểm tra rồi mới ghi. Nếu quyền bị thu hồi đúng lúc giữa hai bước, phép ghi đó vẫn có thể hoàn tất. Reservation ở #130 đến #134 nhằm đóng khoảng hở này nhưng đã không được đưa vào.

Hai lựa chọn để TV1 và TV2 quyết định:

1. Chấp nhận khoảng hở vài mili giây cho phạm vi đồ án và ghi rõ giới hạn.
2. Mở lại #130 đến #134 và làm lại trên nền #135. Lựa chọn này tốn nhiều công vì lịch sử đã tách xa.

PR #135 không tự chọn thay các owner.

## Ghi chú sửa thông tin

Bình luận khi đóng #119 nói CI xanh mà không cần `api.version`. Thông tin đó không đúng: `api.version` đã có sẵn trong #135 từ 9073ad3, nên #119 đóng vì nội dung đã nằm trong #135.
