# Bàn giao TV4 — Tuần 1

- **Thành viên:** Phạm Thế Cường (TV4)
- **Phạm vi:** Inventory, fulfillment, COD, notification.
- **Trạng thái:** `PARTIAL` / `NOT_VERIFIED` cho runtime.

## Đầu ra

1. `WEEK1_BASELINE.md`: baseline repo, ownership và contract draft để xin review.
2. `WEEK1_TEST_BASELINE.md`: test matrix gồm 21 tình huống; tất cả đang `NOT_RUN`.

## Chưa thể xác nhận

- Chưa có API nghiệp vụ, schema/migration, Kafka consumer hoặc auth cho hai service Inventory/Notification.
- Chưa có Gradle Wrapper; môi trường kiểm tra không cài Gradle CLI, nên chưa chạy được test.
- Chưa xác minh Docker Compose health hoặc E2E live.
- Contract còn cần TV3, TV5 và TV1 review ở các ranh giới liên quan.

## Việc tiếp theo

1. Đưa hai tài liệu lên branch riêng, mở PR để review.
2. Xin TV3 chốt inventory consume/release và state transition chung.
3. Xin TV5 review COD/settlement; xin TV1 review security/internal auth.
4. Sau khi contract được chốt, triển khai service và test ở tuần 2.
