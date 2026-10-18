# Bàn giao TV4 — Tuần 1

- **Thành viên:** Phạm Thế Cường (TV4)
- **Phạm vi:** Inventory, fulfillment, COD, notification.
- **Trạng thái:** `PARTIAL`; runtime/integration `NOT_VERIFIED`.

## Đầu ra

1. `WEEK1_BASELINE.md`: baseline, ownership, contract draft và trạng thái Gradle Wrapper.
2. `WEEK1_TEST_BASELINE.md`: test matrix gồm **21 tình huống**; tất cả đang `NOT_RUN`, chưa có ca nào được tính là PASS.

## Trạng thái kiểm tra và giới hạn

- Tài liệu này mô tả baseline/scope; không chứng minh các luồng nghiệp vụ đã được triển khai hoặc chạy thành công.
- Snapshot source ZIP ban đầu thiếu Gradle Wrapper, nhưng reviewer xác nhận `main` đã có Wrapper từ PR #22, commit `230d760`. Vì vậy, thiếu Wrapper không còn là blocker hiện tại.
- Các lệnh build/test của TV4 chưa được chạy trong lần cập nhật tài liệu này. Runtime, DB-backed integration, Kafka và E2E vẫn `NOT_RUN`; không báo PASS nếu chưa có log.
- Contract vẫn cần TV3 review phần checkout/inventory/order status, TV5 review COD/settlement và TV1 review authentication/authorization.

## Việc tiếp theo

1. Xin TV3 xác nhận inventory consume/release, operation ID/idempotency và state transition chung.
2. Xin TV5 review COD/settlement, điều kiện `DELIVERED` và chống ghi nhận lặp.
3. Xin TV1 review role/permission, internal auth và quyền đọc notification.
4. Chạy các task test qua `backend/gradlew.bat` (Windows) hoặc `backend/gradlew` (Linux/macOS); lưu command, exit code và log.
5. Triển khai/kiểm thử nghiệp vụ trong branch riêng; bổ sung test DB-backed/integration khi contract và schema đã được thống nhất.

## Trạng thái nghiệm thu

- **Tài liệu/baseline:** có.
- **Test plan:** 21 ca được xác định; toàn bộ `NOT_RUN`.
- **Code nghiệp vụ TV4:** chưa được chứng minh hoàn tất bởi PR tài liệu này.
- **Build/test runtime:** `NOT_RUN` trong lần cập nhật này.
- **Tổng trạng thái:** `PARTIAL` / `NOT_VERIFIED`; chưa đủ cơ sở đánh dấu Issue #4 `DONE`.
