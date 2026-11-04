# TV4 — Test baseline tuần 1

**Phạm vi:** Đây là test matrix TV4. Trạng thái dưới đây chỉ chuyển sang `PASS` khi có assertion/log chạy thật; một số ca Inventory đã có bằng chứng module-level. PASS ở đây không có nghĩa là liên service/E2E.

| ID | Khu vực | Kịch bản | Kết quả mong đợi | Trạng thái | Blocker/bằng chứng |
|---|---|---|---|---|---|
| TV4-INV-01 | Inventory | Consume lượng hàng còn đủ | Tồn giảm đúng số lượng một lần | PASS (module) | `InventoryReservationLedgerCandidatePostgresIT.twoStoresWithSameSkuKeepIndependentOwnerBudgetsAndLookupSurvivesLostResponse`; PostgreSQL, commit `ae9b1c0` |
| TV4-INV-02 | Inventory | Consume lượng lớn hơn tồn | Không trừ kho; trả lỗi thiếu hàng | NOT_RUN | Chưa có log kiểm thử nghiệp vụ |
| TV4-INV-03 | Inventory | Consume nhiều item, một item thiếu | Toàn operation rollback, không trừ một phần | NOT_RUN | Chưa có log kiểm thử transaction |
| TV4-INV-04 | Inventory | Consume lại cùng operation/order | Không trừ kho lần hai | PASS (module) | Exact replay được kiểm tra trong `InventoryReservationLedgerCandidatePostgresIT.twoStoresWithSameSkuKeepIndependentOwnerBudgetsAndLookupSurvivesLostResponse`; PostgreSQL, commit `ae9b1c0`; contract Order vẫn chờ TV3 |
| TV4-INV-05 | Inventory | Release lặp cùng operation | Không cộng kho lần hai | PASS (module) | `InventoryOperationPostgresIT.reserveConsumeAndReleaseRetriesDoNotApplyStockTwice` và `InventoryReservationLedgerCandidatePostgresIT.concurrentSameReleaseOperationIdReplaysWithoutReturningStockTwice`; PostgreSQL, commit `ae9b1c0` |
| TV4-INV-06 | Inventory | Hai request đồng thời tranh cùng tồn | Không oversell; tồn không âm | PASS (module) | `InventoryOperationPostgresIT.concurrentDifferentIdsCannotOversellSharedSkuAndLosingClaimRollsBack`; PostgreSQL, commit `ae9b1c0`; chưa phải hai Checkout service thật |
| TV4-FUL-01 | Fulfillment | Seller xem đơn của store mình | Chỉ trả đơn thuộc store đó | NOT_RUN | Chưa có log kiểm thử ownership |
| TV4-FUL-02 | Fulfillment | Seller thao tác đơn của store khác | Bị từ chối, không đổi trạng thái | NOT_RUN | Chưa có log kiểm thử authorization |
| TV4-FUL-03 | Fulfillment | Chuyển trạng thái theo thứ tự hợp lệ | Chỉ chuyển trạng thái được phép | NOT_RUN | Chưa có log kiểm thử state machine |
| TV4-FUL-04 | Fulfillment | Từ chối đơn không có lý do | Validation từ chối request | NOT_RUN | Chưa có log kiểm thử validation |
| TV4-FUL-05 | Fulfillment | Hủy đơn không còn trạng thái được phép hủy | Từ chối; không hoàn kho | NOT_RUN | Chưa có log kiểm thử trạng thái |
| TV4-FUL-06 | Fulfillment | Hủy/từ chối và retry | Hoàn kho đúng một lần | NOT_RUN | Chưa có log kiểm thử idempotency |
| TV4-COD-01 | COD | Admin xác nhận COD trước `DELIVERED` | Từ chối; không đánh dấu `PAID` | NOT_RUN | Chưa có log kiểm thử COD |
| TV4-COD-02 | COD | Admin xác nhận COD sau `DELIVERED` | Ghi nhận người và thời điểm xác nhận | NOT_RUN | Chưa có log kiểm thử COD |
| TV4-COD-03 | COD | Lặp lại xác nhận COD | Idempotent; không ghi nhận lần hai | NOT_RUN | Chưa có log kiểm thử idempotency |
| TV4-COD-04 | COD/Auth | Seller hoặc non-ADMIN gọi API xác nhận COD | Bị từ chối; không đổi trạng thái thanh toán | NOT_RUN | Chưa có log kiểm thử role/permission |
| TV4-NOT-01 | Notification | Nhận event status hợp lệ | Tạo thông báo cho recipient đúng | NOT_RUN | Chưa có log kiểm thử consumer |
| TV4-NOT-02 | Notification | Consumer nhận event trùng | Không tạo thông báo trùng | NOT_RUN | Chưa có log kiểm thử deduplication |
| TV4-NOT-03 | Notification | Người dùng đọc notification của người khác | Bị từ chối | NOT_RUN | Chưa có log kiểm thử authorization |
| TV4-NOT-04 | Notification | Kafka tạm dừng rồi hoạt động lại | Retry/recovery không mất hoặc nhân đôi xử lý | NOT_RUN | Chưa có log kiểm thử recovery |
| TV4-NOT-05 | Notification | Event không hợp lệ hoặc lỗi vĩnh viễn | Được ghi nhận và chuyển xử lý lỗi theo contract | NOT_RUN | Chưa có log kiểm thử error/DLT policy |

## Cách báo cáo kết quả

- `PASS`: đã chạy và có log/assertion chứng minh đạt.
- `FAIL`: đã chạy và kết quả thực tế khác mong đợi; đính kèm lỗi.
- `NOT_RUN`: chưa chạy hoặc bị chặn; ghi rõ lý do.
- Không chuyển trạng thái sang PASS chỉ vì đã viết test hoặc đọc code.
- Mock-only tests không được mô tả là DB-backed integration hay E2E.

## Bằng chứng chạy Inventory module — 2026-10-22

- Branch: `feat/tv4-reservation-ledger-candidate`; code commit: `ae9b1c0564cde4c00be0c5e457c10fc26376a851` (PR #69).
- Lệnh tại root repo trên Windows:

```powershell
.\backend\gradlew.bat -p backend :inventory-service:test :inventory-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain
```

- Kết quả: **BUILD SUCCESSFUL; 27 unit + 34 PostgreSQL/Testcontainers integration PASS, 0 failures/errors/skipped**. Unit và PostgreSQL là hai task riêng; các ca PostgreSQL dùng database thật của Testcontainers. Bộ test bao gồm default Flyway V1/V2 startup/JPA validation và candidate V3 được opt-in riêng.
- Các ca TV4-INV đã ghi `PASS (module)` chỉ chứng minh Inventory code trong tiến trình test. TV4-INV-02/03, toàn bộ Fulfillment/COD/Notification, JWT/Gateway và Checkout E2E vẫn `NOT_RUN` hoặc chờ contract/owner; không tính CI của service khác thay thế.
