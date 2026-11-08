<!-- TV3-W5-CHECKOUT-REPORT-DRAFT-20261107 -->
## TV3 W5 — DỰ THẢO PHẦN CHECKOUT CHO BÁO CÁO ĐỒ ÁN (07/11/2026)

**Trạng thái:** `REPORT_SECTION_DRAFT_READY_FOR_TV2_EDIT`. Nội dung dưới đây đối chiếu source `main@618f9ea8f723d607454397012cd5613ca199f660` cùng evidence #145/#147; chưa tự ghi vào báo cáo chung, chưa tick checklist "Viết phần Checkout trong báo cáo" và không nhận là final release/UAT. Không có test mới được chạy ở checkpoint này.

### 1. Mục tiêu và phạm vi chức năng

Checkout được xây dựng cho sàn thương mại điện tử nhiều cửa hàng, hỗ trợ thanh toán **COD (Cash On Delivery)**. Một khách hàng có thể đưa sản phẩm từ nhiều Store vào cùng giỏ, kiểm tra lại giá/khả dụng và gửi **một** lệnh đặt hàng; hệ thống sinh một đơn tổng (`Order`/`MarketplaceOrder`) gắn nhiều đơn con (`SellerOrder`) theo Store. Mỗi đơn lưu snapshot sản phẩm, số lượng, đơn giá và khoản tiền tại thời điểm tạo. Website hiện chưa tích hợp thanh toán online, hoàn tiền tự động hay hợp đồng Finance production đã được phê duyệt.

### 2. Công nghệ và kiến trúc

Frontend React/TypeScript cung cấp Cart, Checkout COD và Order History. Backend gồm API Gateway, `cart-service`, `order-service`, Catalog/Product và Inventory tương tác qua adapter HTTP có kiểm tra authority/DTO; dữ liệu bền vững của giỏ/đơn/lệnh được lưu bằng PostgreSQL16, JPA/JDBC và Flyway. Java 21, Spring Boot, Gradle và Testcontainers dùng cho triển khai/kiểm thử. Luồng nghiệp vụ:

`React Cart → Gateway → Cart API (revision, items)`

`React Checkout → POST /api/orders → Idempotency/Order → Cart claim → Catalog verify → durable CheckoutDraft/Inventory intent → Inventory RESERVE → Order + SellerOrders + outbox commit → Cart finalize → Order History`

Đây là **luồng thiết kế và source thực tế**, không phải lời khẳng định các phụ thuộc bên ngoài đều chạy ổn định trong mọi lỗi mạng.

Nguồn:
- [CheckoutController](https://github.com/duyxl56785/Project_HK5/blob/618f9ea8f723d607454397012cd5613ca199f660/backend/order-service/src/main/java/com/projecthk5/orderservice/api/CheckoutController.java)
- [CheckoutService](https://github.com/duyxl56785/Project_HK5/blob/618f9ea8f723d607454397012cd5613ca199f660/backend/order-service/src/main/java/com/projecthk5/orderservice/service/CheckoutService.java)
- [CartController](https://github.com/duyxl56785/Project_HK5/blob/618f9ea8f723d607454397012cd5613ca199f660/backend/cart-service/src/main/java/com/projecthk5/cartservice/api/CartController.java)
- [CheckoutCodView React](https://github.com/duyxl56785/Project_HK5/blob/618f9ea8f723d607454397012cd5613ca199f660/frontend/src/features/checkout/CheckoutCodView.tsx)

### 3. Diễn giải luồng xử lý Checkout theo source

**Bước 1 — Quản lý giỏ và xác thực khách hàng.** `GET /api/cart`, `POST /api/cart/items`, `PATCH /api/cart/items/{productId}`, `DELETE /api/cart/items/{productId}`, `DELETE /api/cart` phục vụ xem/thêm/sửa/xóa/clear giỏ. Customer được xác định từ JWT đã xác thực; giỏ phân nhóm Store, duy trì revision để phát hiện bản cũ.

**Bước 2 — Gửi lệnh tạo đơn COD.** React gọi `POST /api/orders` kèm JWT, header `Idempotency-Key` và JSON có `expectedRevision`, `expectedItems` và `shipping`. Controller chuẩn hóa địa chỉ giao hàng rồi giao `CheckoutService` xử lý. Server kiểm tra key 1–128 ký tự và cấu trúc expectedItems, tính fingerprint để chống đổi payload khi replay.

**Bước 3 — Nhận quyền xử lý lần đặt hàng.** `IdempotencyCoordinator.begin(customerId,key,fingerprint)` tạo hoặc đọc CheckoutAttempt bền vững. Khi attempt đã `SUCCEEDED`, server đọc lại Order theo customer, so cả shipping, trả về Order cũ, không tạo Order/Inventory RESERVE mới; thay key/body không trở thành giao dịch mới một cách âm thầm. Trạng thái chưa xác định cần reconciliation, không coi là chắc chắn thất bại.

**Bước 4 — Đối chiếu giỏ với dữ liệu hiện hành.** `CartPort.claim` dùng key và revision để chống hai Checkout cùng giỏ. Server so `expectedItems` với snapshot Cart; qua Catalog kiểm tra Product, Seller→Store, SKU, giá, quantity, tính tiền bằng `BigDecimal` và kiểm tra giá/giới hạn lưu trữ. Nếu Product bị khóa/thiếu, Store không hợp lệ, stale revision hay giá thay đổi thì yêu cầu bị từ chối với lý do phù hợp, không lặng lẽ loại bỏ một món khỏi giỏ.

**Bước 5 — Chuẩn bị bản nháp bền vững và dự trữ tồn kho.** Order tổng, SellerOrders và items được gán ID ổn định, snapshot tiền, shipping và outbox payload trước lần gọi Inventory. `CheckoutDraftJournal` lưu graph qua transaction riêng; Inventory `RESERVE` được gửi dựa trên Product/SKU và quantity đã kiểm chứng, không sử dụng map Product↔SKU không đáng tin từ client.

**Bước 6 — Commit Order và xác nhận kết quả.** Khi Inventory trả kết quả thành công và Order writer commit, lưu đơn tổng/đơn con/outbox, thử finalize Cart và trả Order ID. Nếu kết quả trả về từ Cart/Inventory/DB không xác định vì timeout/lost response, attempt được giữ để đối soát (`CHECKOUT_OUTCOME_UNKNOWN_RECONCILIATION_REQUIRED`); không tạo operation ID mới, không tự RELEASE hoặc trừ kho thêm khi chưa chứng minh được tác động thật.

**Bước 7 — Tra cứu.** `GET /api/orders` và `GET /api/orders/{orderId}` chỉ cung cấp Order của khách tương ứng; giao diện Order History hiển thị đơn, các đơn con và trạng thái.

Nguồn bổ sung: [OrderController](https://github.com/duyxl56785/Project_HK5/blob/618f9ea8f723d607454397012cd5613ca199f660/backend/order-service/src/main/java/com/projecthk5/orderservice/api/OrderController.java), [CheckoutDraftJournal](https://github.com/duyxl56785/Project_HK5/blob/618f9ea8f723d607454397012cd5613ca199f660/backend/order-service/src/main/java/com/projecthk5/orderservice/service/CheckoutDraftJournal.java).

### 4. Các cơ chế bảo đảm tính đúng đắn

- **Idempotency:** `(customerId,Idempotency-Key)` chống trùng key; cùng key và payload hợp lệ trả kết quả đã commit, đổi payload/shipping trả lỗi, không tạo thêm đơn.
- **Consistency:** Cart revision + expectedItems + Catalog preflight tránh xác nhận nhầm sản phẩm/giá; Seller→Store phải nhất quán; snapshot của Order không được tự động cập nhật theo Catalog sau khi chốt.
- **Inventory safety:** có journal/intent bất biến trước external call; xử lý timeout theo `UNKNOWN/NEEDS_RECONCILIATION` thay vì bù trừ mù; không khẳng định đã nghiệm thu đầy đủ hai fixture fault thật.
- **Ownership:** chỉ Customer gốc được checkout/đọc Order theo JWT; fulfillment của từng SellerOrder vẫn theo Seller/Store owner và phân quyền Gateway/backend.
- **Tiền:** tính bằng `BigDecimal` và kiểm tra giới hạn lưu trữ, không dùng float tùy tiện; quy tắc chính sách tiền COD/Finance production (`M01`) vẫn là gate phê duyệt riêng.

### 5. Kiểm thử và kết quả đã có bằng chứng

| Nội dung | Kết quả | Phạm vi/chứng cứ |
| --- | --- | --- |
| Multi-Store COD Checkout | **PASS demo** | TV3 real 1 Customer/2 Store SQL + Inventory/Cart evidence tại [#145](https://github.com/duyxl56785/Project_HK5/pull/145); TV2 owner 10-case run [#147](https://github.com/duyxl56785/Project_HK5/pull/147) |
| One Order + 2 SellerOrders, snapshot tiền | **PASS demo đã ghi** | TV3 frozen SQL Order/SellerOrder/Item snapshots trên app `bf8eb3a`; không gộp với browser History từ HEAD khác thành một lượt UAT duy nhất |
| Same-key replay, thay key/body, giá đổi/hết hàng, revision/Product | **PASS trong các case đã chạy** | TV3 6/6 negative Gateway/API tại #145, TV2 owner [10-case final #147](https://github.com/duyxl56785/Project_HK5/pull/147); source integrated main |
| Order History | **PASS trong demo** | TV2 browser/HTTP History snapshot tại #147, có test UI tích hợp; không thay cho final UAT |
| Backend + frontend regression on merged source | **CI SUCCESS có điều kiện HEAD** | #145/#147 source và merge CI xanh; chỉ dẫn exact-run URL tại PR, không cộng các suite khác HEAD thành một release count |
| 2 fixture UNKNOWN thật (claim-conflict, timeout/lost-response) | **NOT_RUN / BLOCKED_DESIGN** | [UAT #18](https://github.com/duyxl56785/Project_HK5/issues/18) chưa có acceptance; không gọi mock/unit là live fault recovery |
| Final UAT 2 Seller / 2 Store / 1 Customer / 1 Admin, Finance production | **NOT ACCEPTED** | TV5 [#18](https://github.com/duyxl56785/Project_HK5/issues/18) OPEN; M01, grants/retention và các owner approvals còn độc lập |

**Nhận định:** TV3 đã hoàn thành mã nguồn Checkout COD/Cart/Order/Order History và kiểm chứng chức năng demo nhiều cửa hàng với evidence/CI đã ghi nhận. Chưa có cơ sở để tuyên bố full production readiness hoặc failover/UNKNOWN/Finance UAT cuối. Khi làm video demo cần quay màn hình giỏ 2 Store, xác nhận COD, Order ID + 2 SellerOrders, Order History và tình huống lỗi; dẫn evidence SQL/log thực thay vì số PASS ước lượng.

### 6. Công việc còn lại trước khi đưa vào báo cáo chính thức

- TV2 xác nhận mẫu/chương/mục đặt phần Checkout trong báo cáo chung và có cần hình sequence diagram hay screenshot mới không.
- TV3/Codex đưa phần nội dung trên vào **đúng file báo cáo hiện có** theo mẫu, không sửa phần của TV1/TV2/TV4/TV5; bổ sung hình/log từ artifact thực, không tự vẽ UI như evidence.
- Sau khi báo cáo được commit/merge vào nhánh báo cáo chính và owner review, mới tick `[x] Viết phần Checkout trong báo cáo` ở #160. Hiện `DRAFT ONLY`.

**Không làm thay các việc W5 sửa lỗi P0/P1** của TV3; P3 lỗi phân loại Return được Codex sửa local `81cc295` nhưng chưa có post-merge PR/CI. Seller Return #182/#183 và Inventory restock chính sách chặn #155/#156/#18 vẫn cần owner tích hợp.