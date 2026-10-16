# Hợp đồng API/Event — Placeholder cần đối chiếu

> Tài liệu **không thay thế** docs/agent/CONTRACTS.md của dự án V2 trước đây. Chỉ là nơi dành cho việc import, đối chiếu và chốt contract chung trước khi lập trình.

Các yêu cầu nền tảng theo báo cáo:
- Công khai: `/api/**` qua API Gateway; nội bộ `/internal/**` không định tuyến công khai, xác thực bằng internal token theo service pair.
- Checkout: `POST /api/orders` kèm `Idempotency-Key`, `expectedItems`; gọi lại cùng key không tạo đơn trùng.
- Outbox Kafka: `order.placed.v1`, `seller-order.status-changed.v1`; consumer xử lý lặp idempotent.
- Đối tượng quan trọng: `orders`, `seller_orders`, `inventory_checkout_operations`, `reviewable_items`, `settlements`.
- Không thay tên topic, field, lỗi/response, ownership hoặc migration trước khi đã đối chiếu code V2 và có review liên quan.

Tạo đề xuất thay đổi trong GitHub Issue; PR phải nêu tính tương thích và test 2 phía. **Chưa có endpoint hay Kafka event nào được triển khai trong scaffold.**

## Đề xuất TV1 đang chờ review

[Auth/JWT/Identity/Gateway Contract — draft v0.1](TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md) (Refs #1) ghi baseline, JWT/roles, endpoint/DTO, 401/403, refresh token, Gateway/internal boundary và contract consumer TV2–TV5. Các mục `OPEN DECISION` cần owner/consumer xác nhận; liên kết này không thay đổi contract đã thống nhất hoặc xác nhận chức năng đã triển khai.
