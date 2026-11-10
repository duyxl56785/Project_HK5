# PR #188 — Bảng nội dung giữ/sửa, 08/11/2026

**Giới hạn:** A — bản Codex gốc 351 dòng — không có trong filesystem hiện tại. Bảng này đối chiếu **B baseline `b218744` với source `main@78de5c0` và các nguồn thực sự đọc được**, không khẳng định đã đọc hay so wording của A. Không dựa số dòng để kết luận tương đương.

| Nội dung B | Giữ / sửa | Lý do và thay đổi trong lượt này |
|---|---|---|
| Mục tiêu COD, một Order/nhiều SellerOrders | GIỮ + làm rõ | Entity thực tên Order, MarketplaceOrder là thuật ngữ nghiệp vụ, không phải bảng/entity thứ hai |
| Luồng Cart/Checkout/Catalog/Inventory | GIỮ | Khớp CheckoutService trên main nền; không tự sửa production |
| Bảng route, JWT/Customer ownership | GIỮ | Route và identity lấy từ backend; không biến body/Store client thành authority |
| Sơ đồ SUCCEEDED / không SUCCEEDED | SỬA | Baseline vẽ mọi non-SUCCEEDED đi tiếp Checkout; source chặn attempt cũ bằng 409 reconciliation. Thêm guard attempt mới PROCESSING |
| Draft trước HTTP | GIỮ + BỔ SUNG | Tách T1 journal, atomic PENDING claim/send generation, T2; không mô tả một transaction xuyên mạng |
| Definitive rejection và UNKNOWN | BỔ SUNG | Giữ nhánh UNKNOWN; thêm nhánh rejection chắc chắn. Cart release khác Inventory RELEASE, không blind compensation |
| Catalog batch | LÀM RÕ | Đường hiện tại gọi verify từng Product; lớp batch cũ không được wire vào CheckoutService |
| Money/database | BỔ SUNG | Gross/platform_fee persisted, sellerNet derived; draft checksum không mã hóa; M01 demo khác Finance production |
| Evidence TV3 6 negative | GIỮ, dẫn raw | Dẫn results/HTTP/SQL đã có, gắn baseline 99979a75 và deployed app bf8eb3a; không nhận là run mới trên main |
| Cụm “10-case final #147” trong hàng PASS | SỬA | Row trước đã nói raw 10-case chưa đối chiếu; không dùng lại như nguồn PASS chắc chắn ở hàng sau |
| Order History “PASS trong demo” riêng của TV2 | THU HẸP | Source và CI không chứng minh một receipt riêng chưa đọc đủ; ghi rõ cần raw report/ảnh/SHA nếu dùng lượt đó |
| PR #187 hosted 140 PG / 166 frontend / 20 Playwright | GIỮ ĐÚNG PHẠM VI | Đây là kết quả hosted đã được trích ở checkpoint trước; lượt này không chạy lại, không cộng thành main final UAT và không suy có screenshot |
| Hai ảnh cũ bị loại | GIỮ | Không nhúng lại ảnh giỏ trống hay TV5 DEMO DATA. Lượt này không nhận đã mở lại pixel hoặc tạo media mới |
| Quy trình mask PII | BỔ SUNG | Mask cả Order detail/History và header, không chỉ form COD; xem ảnh trước công bố; không bật trace/video |
| Cách so A 351 dòng | BLOCKED, không sửa thành PASS | A mất khỏi filesystem, không tạo lại từ trí nhớ, không so file rỗng rồi gọi là diff 351 dòng |
| Năm slot evidence (4 ảnh browser + receipt) | NOT_CAPTURED / NOT_RUN | Docker hoạt động nhưng 0 container/0 volume, thiếu 8 fixture variables và approval capture xác định stack/source |
| Thuyết trình 5 phút và 10 câu phản biện | GIỮ | Có thể dùng khi nói đúng phạm vi bằng chứng; câu “có journal” không được đồng nghĩa recovery hoàn chỉnh |
| Ghép Word/Hình 6.4, tick Issue #160 | CHỜ OWNER | Vị trí theo hướng dẫn đã có; Word gốc không được mở trong lượt này. Không merge/tick hoặc ký nhận UAT thay owner |

Nguồn source: [CheckoutService](../../../../../backend/order-service/src/main/java/com/projecthk5/orderservice/service/CheckoutService.java), [Inventory adapter](../../../../../backend/order-service/src/main/java/com/projecthk5/orderservice/ports/InventoryHttpAdapter.java), [journal](../../../../../backend/order-service/src/main/java/com/projecthk5/orderservice/service/InventoryOperationJournal.java), [SellerOrder](../../../../../backend/order-service/src/main/java/com/projecthk5/orderservice/entity/SellerOrder.java), [draft journal](../../../../../backend/order-service/src/main/java/com/projecthk5/orderservice/service/CheckoutDraftJournal.java). Các file production này không thay đổi trong PR update.

**Delta owner đã giữ:** ba commit e3fa17a/b9e5759/b218744 tách `/orders/:id` khỏi `/orders`, thêm C2 và đối chiếu caption đúng route. Không dùng ảnh Order detail để tuyên bố đã chụp Order History.
