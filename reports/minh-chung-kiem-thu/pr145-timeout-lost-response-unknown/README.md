# PR #145 — timeout/lost-response UNKNOWN

**Trạng thái 03/11/2026: NOT_RUN / BLOCKED.** Source PR #145: `dc0b54c533659ef89c5dc77946ac874739eb4cb2`. Không có ảnh lỗi, ảnh sau sửa, log ca live hoặc receipt provider vì ca này **chưa được thực hiện**; không tạo minh chứng giả.

TV3 [chấp nhận giao thức fixture có điều kiện](https://github.com/duyxl56785/Project_HK5/pull/69#issuecomment-6581361142): provider phải **xác thực và commit request gốc trước**, sau đó chỉ mất response ở đường về caller. Không xóa response đã lưu, không dùng mock 503/409 hay sửa SQL để giả UNKNOWN. Lưu nguyên checkoutAttemptId/customer/Store/SellerOrder/reservation/operationId, loại thao tác, SKU/số lượng và fingerprint.

Cần TV4 chứng minh provider acceptance và cơ chế mất response an toàn; TV2 duyệt exact source/fixture SHA, Compose project, cổng, volumes, nhãn, dữ liệu giả và teardown; TV5 xác minh PII/Finance/QA. Docker Engine hiện không khả dụng. `CheckoutDraftPostgresIT.realHttpReadTimeoutRetainsUnknownDraftAndCannotSendOrReleaseOnReplay` trong source là regression HTTP cục bộ, **không phải** fixture provider-accepted live; lượt PostgreSQL mới [FAIL lúc Testcontainers khởi tạo](../pr145-testcontainers-init-fail/README.md).

Sau khi được phép, giữ receipt provider và client timeout nguyên vẹn, SHA256 của mỗi tệp, snapshot read-only trước/sau Order/Cart/Inventory. Chỉ PASS nếu UNKNOWN được giữ để hòa giải thủ công, không sinh operationId/order/stock movement trùng, không auto retry, RELEASE, CONSUME hoặc settlement. Hiện các kiểm tra live đó đều NOT_RUN.
