# TV5 — đề xuất Review/Finance tuần 1

Ngày 15/10/2026. Refs #5; đối chiếu PR #7 và #19. **DRAFT**, cần TV1/TV2/TV3/TV4 review trước khi triển khai HTTP/DB/event. Các lớp rules chỉ đánh giá dữ kiện trong bộ nhớ, không phải chức năng production hoặc contract liên service đã thống nhất.

## Review (F15)

- Trusted Order facts phải cung cấp buyer/order/product/store và trạng thái giao hàng, không nhận bằng chứng mua hàng hoặc owner từ client. V2 §7–9 mô tả event DELIVERED → reviewable_items trong product-service, không cần gọi Order đồng bộ. Giữ mô hình event/read model hay dùng lookup là quyết định D03 cần owner xác nhận; từ “adapter” không bắt buộc HTTP call. Identity local vẫn opaque, không tự suy ra UUID/JWT claim.
- Chỉ buyer của đơn DELIVERED, có sản phẩm trong đơn, được tạo review; stars nguyên 1–5. DELIVERED xét ở SellerOrder chứa sản phẩm; TV3/TV4 cần chốt quan hệ với MarketplaceOrder.
- Khóa nghiệp vụ là **(order_id, product_id)**, kể cả khi có nhiều line/SKU cùng product. TV3/TV5 phải chốt order_id là MarketplaceOrder hay SellerOrder trước migration; không chỉ unique(order_item_id) nếu mô hình cho phép nhiều line cùng product.
- Đề xuất dữ liệu trong product-service: review id, order reference, product reference, buyer reference, stars CHECK 1..5, hidden, created_at. FK Product chỉ trong DB product-service; order/buyer chỉ là tham chiếu logic, không FK xuyên DB.
- Khi có persistence, insert + unique DB là guard cuối cho retry/concurrency. `alreadyReviewed` trong rules chỉ là precheck, không chống race hoặc lưu dữ liệu.
- Admin verified được hide/unhide; giữ nguyên stars và lịch sử, thao tác lặp cùng trạng thái không thay đổi kết quả. `AdminAccess` là boundary bắt buộc, chưa có adapter JWT production.
- V2 UC17/A.7 yêu cầu lý do khi ẩn, hidden_reason tối đa 500, comment tùy chọn tối đa 1000 và status VISIBLE/HIDDEN. Rules hiện chưa có reason/comment/audit; đây là khoảng trống thiết kế cần D05 xác nhận trước model/API, không phải validation đã triển khai.
- Điểm công khai tính cả sum và count chỉ từ review visible; không có review công khai trả empty (mapping HTTP/null cần consumer chốt). Đề xuất hiển thị 2 chữ số HALF_UP; điểm gốc không làm tròn trước tổng hợp.
- Contract response lỗi/401/403/409, hide audit trail, phân trang và public visibility phải chốt cùng TV1/TV2; không dùng enum hay endpoint tự đặt trong draft này làm API chuẩn.

## Finance (F16)

- Phí sàn **5%**, House Store **0%**; `seller_net = subtotal - commission`. Dùng BigDecimal/numeric, HALF_UP cho commission, không float/double.
- TV3 chụp subtotal, fee rate, commission, seller_net, currency và currency scale tại tạo SellerOrder. House Store là cấu hình server đã xác minh; không nhận cờ miễn phí từ client. Settlement dùng snapshot đã lưu, không tính lại theo Store hiện tại.
- `FinanceRules.snapshot` là hàm hỗ trợ tạo snapshot. Currency scale phải do contract quyết định; ca test VND scale 0 và tiền scale 2 chỉ chứng minh phép tính. Hàm từ chối subtotal âm hoặc mất precision, không tự thêm shipping/discount/tax vào subtotal chưa được chốt.
- V2 §3.3/7.2 nói HALF_UP theo VND nhưng A.6 lưu NUMERIC(19,2); cần D06 làm rõ scale tính/hiển thị. Giới hạn scale 0..4 hiện tại chỉ thuộc helper local, chưa là contract được phê duyệt. Record Amounts là kết quả hàm, không phải DTO đã validation để nhận trực tiếp từ client.
- Chỉ settle SellerOrder **DELIVERED + COD PAID**, chưa settled. TV4 sở hữu xác nhận COD và người được phép xác nhận; TV5 không tự cập nhật COD/status.
- Đề xuất settlements nằm trong order-service DB: id, seller_order_id UNIQUE, store reference, tiền/currency snapshot, settled_at, actor reference, audit reference. Giao dịch phải đọc/khóa SellerOrder, xác minh điều kiện, insert settlement và lưu trạng thái atomically. Unique + transaction chống hai worker settle cùng lúc; boolean guard đơn lẻ không đủ.
- Seller lấy Store từ verified identity + server lookup; lọc store ở query và kiểm tra bản ghi detail/export/aggregate. Admin dùng permission riêng. Không cho client storeId làm căn cứ quyền; không trả tổng/số lượng của Store khác trong lỗi.
- Bốn nhóm doanh thu trong Issue #5 **chưa có định nghĩa trong source/tài liệu được cung cấp**. Cần TV3/TV4/TV5 xác nhận mapping trạng thái, gross/net, shipping/cancel và cutoff trước dashboard; không tự đặt bốn công thức.

## Bàn giao cần owner xác nhận

| Quyết định | Owner / consumer | Trạng thái |
|---|---|---|
| Verified identity, Admin moderation, Finance access policy | TV1 → TV5 | Chờ chốt |
| Product ID, Category FK ON DELETE RESTRICT, CATEGORY_IN_USE, public review fields | TV2 + TV5 | Chưa có Product; không sửa V1 Category của PR #7 |
| Order key, product lines, monetary snapshots/currency/scale | TV3 + TV5 | Chờ chốt |
| DELIVERED/COD PAID truth source, settlement transaction/locking | TV4 + TV3 + TV5 | Chờ chốt |
| P1 trong PR #19 so với nghiệm thu F15/F16 của báo cáo V2 | Cả nhóm / chủ dự án | Cần xác nhận phạm vi; không tự hạ tiêu chí |

Không phát hành migration/HTTP/event trong thay đổi TV5 này. Sau khi chốt, bổ sung adapter và contract tests cả provider/consumer, DB concurrent tests và UAT qua Gateway thật.

Đối chiếu nguồn mới: V2 §6.3 dùng **HS256**; hỏi JWKS không có nghĩa bắt buộc chuyển thuật toán. Xem [D01–D10](TV5_W1_DECISIONS.md) và [đối chiếu báo cáo theo chương/trang](../reports/TV5_V2_RECONCILIATION.md). Test local của helper không xác nhận endpoint, payload Kafka, DB schema hoặc HTTP status còn tranh luận.
