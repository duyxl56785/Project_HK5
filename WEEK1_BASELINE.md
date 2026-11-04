# TV4 — Tuần 1: Baseline, ownership và contract draft

- **Owner:** Phạm Thế Cường (TV4)
- **Phạm vi:** Inventory, SellerOrder fulfillment/COD trong `order-service`, Notification/outbox consumer.
- **Trạng thái baseline ban đầu:** `PARTIAL` — tài liệu baseline và contract draft đã chuẩn bị; bảng quan sát bên dưới phản ánh lượt kiểm tra ban đầu.
- **Cập nhật tiến độ:** bằng chứng chạy module Inventory hiện tại được ghi trong `WEEK1_TEST_BASELINE.md` và `WEEK1_HANDOFF.md`; baseline này không thay thế trạng thái test mới.
- **Phạm vi kiểm tra:** snapshot source ZIP ban đầu và trạng thái repository hiện tại không được đánh đồng. Snapshot ban đầu thiếu Gradle Wrapper; reviewer xác nhận Wrapper đã có trên `main` từ PR #22, commit `230d760`. Vì vậy, thiếu Wrapper **không còn là blocker hiện tại**.
- **Giới hạn:** Chưa tuyên bố contract đã được nhóm phê duyệt. Những điểm liên quan checkout cần TV3 review; COD/settlement cần TV5 review; security cần TV1 review.

## 1. Baseline source và repository

| Hạng mục | Quan sát | Kết quả |
|---|---|---|
| Java | Baseline ban đầu ghi nhận OpenJDK 21 | Đây là thông tin của lượt kiểm tra trước, không phải xác nhận môi trường hiện tại |
| Gradle Wrapper | Snapshot source ZIP ban đầu không có Wrapper; reviewer xác nhận `main` đã có Wrapper từ PR #22, commit `230d760` | Dùng Wrapper trên branch hiện tại; runtime build/test của TV4 vẫn `NOT_RUN` cho đến khi có log chạy thật |
| Inventory | Baseline ban đầu chỉ có application entry point, README và cấu hình tối thiểu | Chưa có bằng chứng API, persistence, migration hoặc nghiệp vụ Inventory hoàn chỉnh trong baseline này |
| Notification | Baseline ban đầu chỉ có application entry point, README và cấu hình tối thiểu | Chưa có bằng chứng Kafka consumer/outbox nghiệp vụ hoàn chỉnh trong baseline này |
| Order fulfillment/COD | Baseline ban đầu mô tả `order-service` là scaffold | Chưa có bằng chứng luồng SellerOrder/COD end-to-end đã chạy |
| Docker/dependencies | Lượt baseline trước chưa xác minh health | `NOT_RUN`; không suy ra trạng thái runtime từ cấu hình |

## 2. Phân ranh ownership

- **TV4:** tồn kho, fulfillment, COD, notification; migration thuộc service mình sở hữu.
- **TV3 + TV4:** thống nhất contract inventory consume/release và idempotency theo operation/order; TV3 sở hữu checkout/order creation.
- **TV3 + TV4:** thống nhất trạng thái đơn con, chuyển trạng thái, hủy/từ chối và hoàn kho.
- **TV4 + TV5:** thống nhất COD và tác động tới settlement; COD chỉ được ghi nhận sau khi đơn con `DELIVERED`.
- **TV1:** review authentication/authorization, internal service credential và boundary Gateway.

Không sửa enum, DTO hoặc contract dùng chung trước khi các owner liên quan đồng ý trên Issue/PR.

## 3. Contract draft cần review

### Inventory nội bộ

Đề xuất semantics, chưa phải endpoint đã triển khai:

- Một operation consume/release phải gắn với operation ID ổn định và có idempotency.
- Consume phải nguyên tử: nếu bất kỳ item nào thiếu hàng, không trừ một phần.
- Không cho `available_quantity < 0`; cập nhật đồng thời phải tránh oversell.
- Release cùng operation/order không được cộng kho nhiều lần.
- Chỉ service/service-pair được cấp quyền mới gọi endpoint nội bộ; không mở thao tác chỉnh kho tùy ý cho client công khai.
- Cần TV3 xác nhận request/response, item identity, expected quantity, mã lỗi và semantics retry trước khi implement.

### SellerOrder fulfillment

- Người bán chỉ thao tác trên đơn con thuộc cửa hàng của mình; kiểm ownership ở backend.
- Chỉ cho phép chuyển trạng thái theo state machine đã chốt; không nhảy trạng thái tùy ý.
- Từ chối đơn cần lý do.
- Khách chỉ hủy đơn còn `PENDING_CONFIRMATION` theo rule trong báo cáo; hủy/từ chối phải release kho đúng một lần.
- Cập nhật trạng thái, history và outbox cần được thiết kế nhất quán trong cùng transaction DB.
- Danh sách trạng thái/endpoint cuối cùng cần TV3 xác nhận vì baseline ban đầu chưa có state machine hoàn chỉnh.

### COD

- Admin là tác nhân xác nhận COD theo báo cáo; Seller/non-Admin không được tự xác nhận đã thu tiền.
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
- [x] Ghi nhận baseline và giới hạn của lượt kiểm tra ban đầu.
- [x] Tách rõ contract draft và chức năng chưa được xác minh.
- [x] Lập test baseline; chưa gán PASS khi chưa chạy.
- [ ] TV3 review contract inventory + order status.
- [ ] TV5 review COD / settlement impact.
- [ ] TV1 review internal auth and notification access.
- [x] Xác nhận repository hiện tại có Gradle Wrapper theo review PR #22.
- [ ] Chạy build/test trên branch hiện tại và lưu log thật.
- [ ] Chạy test DB-backed / integration phù hợp; chưa có bằng chứng trong tài liệu này.

## 5. Lệnh kiểm tra runtime

Từ thư mục repository trên Windows:

```bat
cd backend
gradlew.bat :inventory-service:test
gradlew.bat :notification-service:test
gradlew.bat :order-service:test
```

Trên Linux/macOS:

```bash
cd backend
./gradlew :inventory-service:test
./gradlew :notification-service:test
./gradlew :order-service:test
```

Các task/module có thể cần điều chỉnh theo `backend/settings.gradle` thực tế. Chưa chạy các lệnh trên trong lần cập nhật tài liệu này; kết quả runtime tiếp tục là `NOT_RUN` cho tới khi có log thực tế.
