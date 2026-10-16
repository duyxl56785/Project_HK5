# TV4 — Tuần 1: Baseline, ownership và contract draft

- **Owner:** Phạm Thế Cường (TV4)
- **Phạm vi:** Inventory, SellerOrder fulfillment/COD trong `order-service`, Notification/outbox consumer.
- **Trạng thái hiện tại:** `PARTIAL` — đã lập baseline và contract draft; chưa có nghiệp vụ V2 trong scaffold, chưa chạy được test vì repo không kèm Gradle Wrapper và môi trường hiện tại không có lệnh `gradle`.
- **Lưu ý:** Đây là tài liệu đề xuất cho review, không tuyên bố contract đã được nhóm phê duyệt. Những điểm liên quan checkout cần TV3 review; điểm COD/settlement cần TV5 review; security cần TV1 review.

## 1. Kiểm tra baseline từ source ZIP

| Hạng mục | Quan sát | Kết quả tuần 1 |
|---|---|---|
| Java | Máy kiểm tra có OpenJDK 21 | Có thể phù hợp toolchain dự kiến |
| Gradle | `gradle` không có trong PATH; repository không có `gradlew` | `NOT_RUN` — chưa thể chạy build/test |
| Inventory | Chỉ có application entry point, README và cấu hình tối thiểu | Chưa có API, persistence, migration, authentication hay nghiệp vụ |
| Notification | Chỉ có application entry point, README và cấu hình tối thiểu | Chưa có API, Kafka consumer, outbox hay nghiệp vụ |
| Order fulfillment/COD | Module `order-service` là scaffold | Chưa có seller-order, state machine, COD endpoint hoặc bù trừ kho |
| Docker | Có compose dev cho PostgreSQL, Redis, Kafka | Chưa khởi động và chưa xác minh health trong lượt kiểm tra này |

## 2. Phân ranh ownership

- **TV4:** tồn kho, fulfillment, COD, notification; migration thuộc service mình sở hữu.
- **TV3 + TV4:** thống nhất contract inventory consume/release và idempotency theo `orderId`; TV3 sở hữu checkout/order creation.
- **TV4 + TV3:** thống nhất trạng thái đơn con, các chuyển trạng thái, hủy/từ chối và hoàn kho.
- **TV4 + TV5:** thống nhất COD và tác động tới settlement; COD chỉ được ghi nhận sau khi đơn con `DELIVERED`.
- **TV1:** review authentication/authorization, internal service credential và boundary Gateway.

Không sửa enum, DTO hoặc contract dùng chung trước khi các owner liên quan đồng ý trên Issue/PR.

## 3. Contract draft cần review

### Inventory nội bộ

Đề xuất semantics, chưa phải endpoint đã triển khai:

- Một operation consume/release phải gắn với `orderId` ổn định và có idempotency.
- Consume phải nguyên tử: nếu bất kỳ item nào thiếu hàng, không trừ một phần.
- Không cho `available_quantity < 0`; cập nhật đồng thời phải tránh oversell.
- Release cùng operation/order không được cộng kho nhiều lần.
- Chỉ service/service-pair được cấp quyền mới gọi endpoint `/internal/**`; không mở thao tác chỉnh kho tùy ý cho client công khai.
- Cần TV3 xác nhận request/response, item identity, expected quantity, mã lỗi và semantics retry trước khi implement.

### SellerOrder fulfillment

Đề xuất quy tắc nghiệp vụ theo báo cáo thiết kế:

- Người bán chỉ thao tác trên đơn con thuộc cửa hàng của mình; kiểm ownership ở backend.
- Chỉ cho phép chuyển trạng thái theo state machine đã chốt; không nhảy trạng thái tùy ý.
- Từ chối đơn cần lý do.
- Khách chỉ hủy đơn còn `PENDING_CONFIRMATION` theo rule trong báo cáo; hủy/từ chối phải release kho đúng một lần.
- Cập nhật trạng thái, history và outbox cần được thiết kế nhất quán trong cùng transaction DB.
- Danh sách trạng thái/endpoint cuối cùng cần TV3 xác nhận vì scaffold chưa có enum hay controller.

### COD

- Admin là tác nhân xác nhận COD theo báo cáo; seller không tự xác nhận đã thu tiền.
- Chỉ xác nhận khi seller-order đã `DELIVERED`.
- Lần xác nhận lặp không tạo thay đổi hoặc lịch sử thanh toán lần hai.
- Lưu người xác nhận và thời điểm xác nhận; không log token/secret.
- TV5 cần review tác động tới finance/settlement trước khi chốt schema hoặc endpoint.

### Notification / Kafka

- Event status được báo cáo nêu là `seller-order.status-changed.v1`; phải giữ nguyên tên/shape sau khi TV3/TV4/TV5 review.
- Consumer phải idempotent, lưu dấu vết event đã xử lý và tránh gửi/lưu thông báo trùng.
- Notification chỉ được người nhận tương ứng đọc; recipient lấy từ danh tính đã xác thực, không tin recipient do client tùy ý truyền.
- Lỗi tạm thời có retry; lỗi không xử lý được cần dead-letter handling theo contract đã chốt.
- Không tự tạo topic hoặc payload mới trước khi đối chiếu contract V2 gốc.

## 4. Tiêu chí hoàn tất tuần 1

- [x] Xác nhận module và file thuộc ownership TV4.
- [x] Ghi nhận baseline thực tế từ source ZIP.
- [x] Tách rõ contract draft và chức năng chưa tồn tại.
- [x] Lập test baseline, chưa gán PASS khi chưa chạy.
- [ ] TV3 review contract inventory + order status.
- [ ] TV5 review COD / settlement impact.
- [ ] TV1 review internal auth and notification access.
- [ ] Có Gradle Wrapper hoặc Gradle cục bộ để build/test module.
- [ ] Chạy test khởi động service và lưu log thật.

## 5. Lệnh kiểm tra cần chạy trên máy có Gradle

Từ thư mục `backend/`:

```bash
gradle :inventory-service:test
gradle :notification-service:test
gradle :order-service:test
```

Nếu nhóm thêm Gradle Wrapper đã được review, dùng `./gradlew` (Linux/macOS) hoặc `gradlew.bat` (Windows) thay cho `gradle`. Không đánh dấu PASS cho tới khi lệnh thực sự chạy thành công.
