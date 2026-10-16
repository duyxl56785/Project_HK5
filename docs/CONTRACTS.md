# Hợp đồng API/Event — Placeholder cần đối chiếu

> Tài liệu **không thay thế** docs/agent/CONTRACTS.md của dự án V2 trước đây. Chỉ là nơi dành cho việc import, đối chiếu và chốt contract chung trước khi lập trình.

Các yêu cầu nền tảng theo báo cáo:
- Công khai: `/api/**` qua API Gateway; nội bộ `/internal/**` không định tuyến công khai, xác thực bằng internal token theo service pair.
- Checkout: `POST /api/orders` kèm `Idempotency-Key`, `expectedItems`; gọi lại cùng key không tạo đơn trùng.
- Outbox Kafka: `order.placed.v1`, `seller-order.status-changed.v1`; consumer xử lý lặp idempotent.
- Đối tượng quan trọng: `orders`, `seller_orders`, `inventory_checkout_operations`, `reviewable_items`, `settlements`.
- Không thay tên topic, field, lỗi/response, ownership hoặc migration trước khi đã đối chiếu code V2 và có review liên quan.

Tạo đề xuất thay đổi trong GitHub Issue; PR phải nêu tính tương thích và test 2 phía. **Chưa có endpoint hay Kafka event nào được triển khai trong scaffold.**

## Contract TV1 v1.0 — quyết định của Technical Lead

[Auth/JWT/Identity/Gateway Contract v1.0](TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md) (Refs #1): Technical Lead Lê Xuân Bảo chốt OD-01–OD-09 ngày 2026-10-16; HS256, identity UUID nội bộ/opaque string wire, API/roles, refresh, database ownership và internal security. [ADR-0001](adr/0001-tv1-mvp-auth-trust-boundaries.md) lưu đánh đổi; [roadmap](TV1_AUTH_IMPLEMENTATION_ROADMAP.md) ghi readiness gates và các PR tiếp theo. Lịch sử draft v0.1 và review được giữ trong contract. Không khẳng định consumer đã xác nhận từng chi tiết hoặc implementation đã hoàn thành; không thay contract nghiệp vụ TV2–TV5.
