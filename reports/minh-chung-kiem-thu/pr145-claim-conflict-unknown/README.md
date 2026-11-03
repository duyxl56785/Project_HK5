# PR #145 — claim-conflict UNKNOWN

**Trạng thái 03/11/2026: NOT_RUN / BLOCKED.** Source PR #145: `dc0b54c533659ef89c5dc77946ac874739eb4cb2`. Không có ảnh lỗi, ảnh sau sửa, log ca live hoặc receipt provider vì ca này **chưa được thực hiện**; không tạo minh chứng giả.

TV3 đã [chấp nhận thiết kế C06 và giao thức fixture có điều kiện](https://github.com/duyxl56785/Project_HK5/pull/69#issuecomment-6581361142): giữ nguyên checkoutAttemptId, rootOrderId, customerId, StoreId/SellerOrderId, reservationId, operationId, loại thao tác, SKU/số lượng và fingerprint; cạnh tranh phải là claim thật trên operationId gốc. Đó **không phải** giấy phép chạy. Hiện Order journal/wire chưa có đủ owner tuple và #69 V3 còn là candidate Java-only.

Điều kiện trước khi chạy: TV4 trình cơ chế claim thật và receipt có thể kiểm chứng; TV2 duyệt bằng văn bản exact source/fixture SHA, Compose project, cổng, volumes, nhãn, seed giả và teardown; TV5 kiểm tra riêng quyền truy cập, PII, Finance/QA và reproducibility. Docker Engine ở lượt này không khả dụng; [preflight FAIL và 42 unit PASS](../pr145-testcontainers-init-fail/README.md) không phải ca claim-conflict.

Khi được phép, lưu **response/receipt gốc không xóa**, SHA256 của receipt và snapshot read-only trước/sau cho Order attempt/draft/journal/outbox, Cart claim và Inventory operation/reservation/stock. Xác nhận một operationId, không thêm Order/stock movement, không auto retry, RELEASE, CONSUME hoặc settlement khi UNKNOWN. Chỉ đánh PASS sau khi có fault thật, log, ảnh thực tế nếu có, JUnit/SQL oracle và full SHA.
