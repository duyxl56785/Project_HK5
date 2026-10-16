# TV4 — Test baseline tuần 1

**Repository baseline:** scaffold, chưa có API/nghiệp vụ TV4. Tất cả ca dưới đây là kế hoạch kiểm thử; chưa chạy, vì vậy trạng thái ban đầu là `NOT_RUN`.

| ID | Khu vực | Kịch bản | Kết quả mong đợi | Trạng thái | Blocker/bằng chứng |
|---|---|---|---|---|---|
| TV4-INV-01 | Inventory | Consume lượng hàng còn đủ | Tồn giảm đúng số lượng một lần | NOT_RUN | Chưa có API/service nghiệp vụ |
| TV4-INV-02 | Inventory | Consume lượng lớn hơn tồn | Không trừ kho; trả lỗi thiếu hàng | NOT_RUN | Chưa có API/service nghiệp vụ |
| TV4-INV-03 | Inventory | Consume nhiều item, một item thiếu | Toàn operation rollback, không trừ một phần | NOT_RUN | Chưa có transaction nghiệp vụ |
| TV4-INV-04 | Inventory | Consume lại cùng `orderId`/operation | Không trừ kho lần hai | NOT_RUN | Chưa chốt contract cùng TV3 |
| TV4-INV-05 | Inventory | Release lặp cùng operation | Không cộng kho lần hai | NOT_RUN | Chưa có operation ledger |
| TV4-INV-06 | Inventory | Hai request đồng thời tranh cùng tồn | Không oversell; tồn không âm | NOT_RUN | Chưa có persistence/locking |
| TV4-FUL-01 | Fulfillment | Seller xem đơn của store mình | Chỉ trả đơn thuộc store đó | NOT_RUN | Chưa có endpoint/ownership |
| TV4-FUL-02 | Fulfillment | Seller thao tác đơn của store khác | Bị từ chối, không đổi trạng thái | NOT_RUN | Chưa có auth/ownership |
| TV4-FUL-03 | Fulfillment | Chuyển trạng thái theo thứ tự hợp lệ | Chỉ chuyển trạng thái được phép | NOT_RUN | Chưa chốt state machine trong code |
| TV4-FUL-04 | Fulfillment | Từ chối đơn không có lý do | Validation từ chối request | NOT_RUN | Chưa có DTO/endpoint |
| TV4-FUL-05 | Fulfillment | Hủy đơn không còn trạng thái được phép hủy | Từ chối; không hoàn kho | NOT_RUN | Chưa có endpoint/state machine |
| TV4-FUL-06 | Fulfillment | Hủy/từ chối và retry | Hoàn kho đúng một lần | NOT_RUN | Chưa có saga/operation ledger |
| TV4-COD-01 | COD | Admin xác nhận COD trước `DELIVERED` | Từ chối; không đánh dấu `PAID` | NOT_RUN | Chưa có COD endpoint |
| TV4-COD-02 | COD | Admin xác nhận COD sau `DELIVERED` | Ghi nhận người và thời điểm xác nhận | NOT_RUN | Chưa có COD endpoint/schema |
| TV4-COD-03 | COD | Lặp lại xác nhận COD | Idempotent; không ghi nhận lần hai | NOT_RUN | Chưa có COD endpoint/schema |
| TV4-NOT-01 | Notification | Nhận event status hợp lệ | Tạo thông báo cho recipient đúng | NOT_RUN | Chưa có Kafka consumer |
| TV4-NOT-02 | Notification | Consumer nhận event trùng | Không tạo thông báo trùng | NOT_RUN | Chưa có deduplication store |
| TV4-NOT-03 | Notification | Người dùng đọc notification của người khác | Bị từ chối | NOT_RUN | Chưa có authentication/endpoint |
| TV4-NOT-04 | Notification | Kafka tạm dừng rồi hoạt động lại | Retry/recovery không mất hoặc nhân đôi xử lý | NOT_RUN | Chưa có consumer/retry/DLT |
| TV4-NOT-05 | Notification | Event không hợp lệ hoặc lỗi vĩnh viễn | Được ghi nhận và chuyển xử lý lỗi theo contract | NOT_RUN | Chưa chốt DLT policy |

## Cách báo cáo kết quả

- `PASS`: đã chạy và có log/assertion chứng minh đạt.
- `FAIL`: đã chạy và kết quả thực tế khác mong đợi; đính kèm lỗi.
- `NOT_RUN`: chưa chạy hoặc bị chặn; ghi rõ lý do.

Không chuyển trạng thái sang PASS chỉ vì đã viết test hoặc đọc code.
