# Bàn giao TV4 — Tuần 1

- **Thành viên:** Phạm Thế Cường (TV4)
- **Phạm vi:** Inventory, fulfillment, COD, notification.
- **Trạng thái:** `PARTIAL`; Inventory module có bằng chứng PostgreSQL local, còn liên service/E2E `NOT_VERIFIED`.

## Đầu ra

1. `WEEK1_BASELINE.md`: baseline, ownership, contract draft và trạng thái Gradle Wrapper.
2. `WEEK1_TEST_BASELINE.md`: test matrix gồm **21 tình huống**; một số ca Inventory đã PASS ở mức module, các ca chưa có bằng chứng và mọi luồng Fulfillment/COD/Notification vẫn `NOT_RUN`.

## Trạng thái kiểm tra và giới hạn

- Tài liệu này mô tả baseline/scope; không chứng minh các luồng nghiệp vụ đã được triển khai hoặc chạy thành công.
- Snapshot source ZIP ban đầu thiếu Gradle Wrapper, nhưng reviewer xác nhận `main` đã có Wrapper từ PR #22, commit `230d760`. Vì vậy, thiếu Wrapper không còn là blocker hiện tại.
- Trên branch `feat/tv4-reservation-ledger-candidate`, commit code `ae9b1c0564cde4c00be0c5e457c10fc26376a851`, đã chạy `:inventory-service:test` và `:inventory-service:postgresIntegrationTest`: **27 unit + 34 PostgreSQL integration PASS, 0 fail/error/skip**. Lệnh và phạm vi bằng chứng được ghi tại `WEEK1_TEST_BASELINE.md` và PR #69.
- Bằng chứng trên là test của Inventory module với PostgreSQL/Testcontainers; không chứng minh Order→Inventory HTTP, JWT/Gateway, Checkout E2E, SellerOrder fulfillment, COD/Finance hoặc Notification/Kafka. Các phần này vẫn `NOT_RUN`/chờ contract và owner.
- Contract vẫn cần TV3 review phần checkout/inventory/order status, TV5 review COD/settlement và TV1 review authentication/authorization.

## Việc tiếp theo

1. Xin TV3 xác nhận inventory consume/release, operation ID/idempotency và state transition chung.
2. Xin TV5 review COD/settlement, điều kiện `DELIVERED` và chống ghi nhận lặp.
3. Xin TV1 review role/permission, internal auth và quyền đọc notification.
4. Chờ TV1 re-review blocker Flyway/JPA; chờ TV2/TV3 xác nhận exact Product→SKU identity và case padded SKU/replay trên PR #69.
5. Triển khai/kiểm thử Fulfillment, COD và Notification khi contract/state/auth từ TV3/TV5/TV1 được chốt; bổ sung PostgreSQL/E2E bằng chứng riêng.

## Trạng thái nghiệm thu

- **Tài liệu/baseline:** có.
- **Test plan:** 21 ca; một số Inventory module PASS theo bằng chứng ghi tại `WEEK1_TEST_BASELINE.md`; phần còn lại `NOT_RUN`.
- **Code nghiệp vụ TV4:** có Inventory operation/ledger provider candidate, chưa có bằng chứng tích hợp live Checkout hoặc Fulfillment/COD/Notification hoàn chỉnh.
- **Build/test runtime:** Inventory module chạy PASS trên PostgreSQL/Testcontainers; chưa chạy xác minh liên service/E2E.
- **Tổng trạng thái:** `PARTIAL` / `NOT_VERIFIED`; chưa đủ cơ sở đánh dấu Issue #4 `DONE`.
