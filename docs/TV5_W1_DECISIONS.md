# TV5 — quyết định chờ xác nhận tuần 1

Refs #5/#20/#21. Đây là danh sách bàn giao D1–D7, **không phải quyết định đã được duyệt**. Nguồn V2 mô tả hệ thống trước đây; không tự áp dụng schema/API/security cũ vào repo scaffold mới. Không yêu cầu triển khai API/DB/UI/E2E trong lượt tuần 1 này.

| ID | Câu hỏi cần chốt và bằng chứng nguồn | Owner xác nhận | Đầu ra cần nhận |
|---|---|---|---|
| D01 | V2 §6.3 dùng HS256; draft trước nhắc JWKS. Giữ HS256 hay phương án khác, issuer/audience/subject/roles và internal token nào? | TV1 | Contract identity/security được review, ví dụ test không secret; JWKS không phải yêu cầu bắt buộc nếu dùng HS256 |
| D02 | V2 UC06 dùng 403 cho chưa nhận/không phải buyer, §3.3 dùng 404 để không lộ dữ liệu người khác. Mapping lỗi Review/Finance cụ thể là gì? | TV1 + TV2/TV3 + TV5 | Bảng actor/resource/error/HTTP status; chưa viết HTTP assertions theo mã suy đoán |
| D03 | V2 §7/8/9 mở quyền qua seller-order.status-changed.v1 DELIVERED → reviewable_items, không gọi Order đồng bộ. Có giữ mô hình này? | TV3/TV4 provider + TV2/TV5 consumer | Payload/version/dedup key, buyer/order/product/store references, replay/out-of-order và DLT; không thêm topic mới |
| D04 | Unique review (order_id,product_id) dùng order tổng hay đơn con? V2 A.7 liên kết reviewable_items; cần xác minh nguồn migration cũ. | TV3 + TV2 + TV5 | Key/identity và quan hệ local DB; không FK xuyên DB |
| D05 | UC17/A.7 yêu cầu hidden_reason ≤500 khi HIDDEN; comment nullable ≤1000; status VISIBLE/HIDDEN. Validation, audit actor/time và unhide lưu/xóa reason thế nào? | TV2 + TV1 + TV5 | DTO/schema/permission proposal được review; rules hiện tại chưa mô hình hóa reason/comment |
| D06 | §3.3/7.2 nói HALF_UP theo VND, A.6 NUMERIC(19,2); chốt scale tính phí/hiển thị và subtotal có shipping/discount không? | TV3 + TV4 + TV5 | Ví dụ biên được duyệt, currency/scale và monetary snapshots. Giới hạn 0..4 của helper chỉ là hiện trạng local |
| D07 | F16/PB16/UC12 nói bốn nhóm doanh thu nhưng không định nghĩa đầy đủ bốn tên/công thức/cutoff. Những nhóm đó là gì? | TV3/TV4 + TV5 | Bảng trạng thái→nhóm, gross/net/commission và khoảng thời gian; chưa viết test công thức giả |
| D08 | §7.2 UNIQUE settlement seller_order_id; ai settle, snapshot/paid_at/paid_by và transaction/race/replay/error semantics? | TV3 + TV4 + TV5 | Ownership Order/COD, đơn đủ điều kiện, transaction proposal; tuần 1 chỉ chốt thiết kế/test plan |
| D09 | Seller suspended có được đọc Finance không? Store mapping từ verified identity và chính sách Admin/House cụ thể? | TV1 + TV2 + TV3/TV5 | Ma trận role/status/ownership cho list/detail/aggregate; không dùng storeId client để cấp quyền |
| D10 | PR #19 P1 và báo cáo V2 SHOULD M6 có ảnh hưởng tiêu chí nghiệm thu học phần không? | Chủ dự án + TV1–TV5 | Xác nhận scope; không tự tích DONE F15/F16 |

TV5 có thể hoàn thành độc lập: rules/unit tests hiện hữu, kiểm tra tài liệu, QA matrix, UAT script, defect/traceability/evidence ledger. Các quyết định trên vẫn PENDING cho đến khi có link comment/review/commit chấp thuận của owner.
