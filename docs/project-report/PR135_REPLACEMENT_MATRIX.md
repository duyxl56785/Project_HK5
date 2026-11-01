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
| #129 | CLOSED | Không dùng snapshot 5 giây để cho phép ghi | Mọi phép ghi đặt reservation ở Auth, Auth kiểm tra quyền hiện tại | Đã thay ở tầng token. Trạng thái Seller/Store được kiểm tra trong nghiệp vụ | TV2 |
| #130, #131 | CLOSED | Reservation bền vững và RPC có xác thực giữa Auth, Seller, Product | Bảng `write_reservations` ở Auth, `WriteFence` ở 6 service, RPC dùng pair token | Đã thay ở tầng token bằng thiết kế gọn hơn. Không có reservation riêng cho trạng thái Seller/Store | TV1, TV2 |
| #132, #133 | CLOSED | Khôi phục reservation sau crash | `closeAbandoned` đóng reservation bị bỏ lại, relay thử lại khi service không truy cập được | Đã thay. Không tự thả theo thời gian | TV1, TV2 |
| #134 | CLOSED | Hàng đợi fencing cho thao tác Auth và Seller | Có fencing revision cho role sync. Không có hàng đợi mutation | Một phần | TV1 |

## Reservation trên #135

PR #135 không mang code của #130 đến #134 sang. Stack cũ dài khoảng 4.200 dòng và vẫn BLOCKED ở tích hợp nghiệp vụ. Bản này giữ nguyên tắc của stack cũ: không thả theo thời gian, phép ghi chỉ dừng khi service xác nhận.

1. Trước khi ghi, `CurrentAuthorityFilter` gọi `POST /internal/accounts/{id}/write-reservations`. Auth giữ row lock của tài khoản, kiểm tra trạng thái và role, rồi ghi một dòng reservation.
2. Mỗi lần commit của phép ghi đó, `WriteFence` giữ shared advisory lock và kiểm tra tombstone trong `write_fence_abort`. Có tombstone thì transaction rollback, client nhận 401.
3. Khi thu hồi SELLER, Auth commit trước, rồi gọi `POST /internal/write-fences/{id}/close` tới service cho từng reservation còn lại. Close giữ exclusive lock và ghi tombstone, nên khi trả về thì phép ghi đã commit xong hoặc không bao giờ commit được.
4. Auth chỉ trả thành công cho Seller sau khi đóng hết. Service không truy cập được thì Auth trả 503, reservation được giữ và relay của Seller thử lại.
5. Phép ghi kết thúc thì filter xóa reservation. Reservation quá 60 giây do crash được `closeAbandoned` đóng qua service. Thời gian chỉ quyết định khi nào đóng, không quyết định việc thả.

## Rủi ro còn lại

- Khoảng hở giữa kiểm tra và ghi đã đóng cho token và role SELLER. Sau khi Auth xác nhận thu hồi, không phép ghi nào dưới role cũ commit được nữa.
- Khoảng trễ của relay vẫn còn: từ lúc Seller bị khóa tới khi Auth nhận sự kiện, phép ghi vẫn được chấp nhận. Trạng thái Seller/Store chỉ được kiểm tra trong nghiệp vụ, không có fence riêng.
- Một request có nhiều transaction thì các transaction đã commit trước khi bị đóng vẫn còn. Phần sau bị từ chối như một lỗi giữa chừng của luồng đó.
- `WriteFence` chỉ bảo vệ transaction trên thread của request.
- Tombstone chưa được dọn. Bảng chỉ tăng khi có thu hồi trúng phép ghi đang chạy hoặc crash.
- Mỗi phép ghi tốn thêm một lượt gọi Auth để xóa reservation.

TV1 cần xem thứ tự khóa, quyền của pair token và endpoint `/internal/write-fences/*`.

## Ghi chú sửa thông tin

Bình luận khi đóng #119 nói CI xanh mà không cần `api.version`. Thông tin đó không đúng: `api.version` đã có sẵn trong #135 từ 9073ad3, nên #119 đóng vì nội dung đã nằm trong #135.
