# Soopi — Smart E-Commerce Marketplace V2

**Soopi** là đồ án sàn thương mại điện tử nhiều người bán (COD-only), có mã nguồn backend, frontend, cơ sở dữ liệu và môi trường demo Docker. Bản đã đóng băng để UAT ngày 21–22/11/2026 là [`eaf3bf270255b80db4525f836bfae7e495dc8651`](https://github.com/duyxl56785/Project_HK5/commit/eaf3bf270255b80db4525f836bfae7e495dc8651). `main` có thể có thêm commit tài liệu sau bản đóng băng; không đồng nhất hai SHA.

> **Phạm vi:** bản đồ án/demo trên máy local, dữ liệu giả; không phải nền tảng thương mại điện tử đã được duyệt cho production. Inventory/Finance/Voucher C2 có các runtime gate mặc định OFF và chỉ được opt-in đúng phạm vi demo được owner cho phép. Không dùng kết quả CI/UAT để suy ra production approval.

## Chức năng chính

- **Khách hàng:** đăng ký, đăng nhập, hồ sơ, địa chỉ; tìm kiếm/lọc/sắp xếp sản phẩm, giỏ hàng đa cửa hàng, Checkout COD; theo dõi/hủy đơn hợp lệ, đánh giá hàng đã giao.
- **Người bán:** đăng ký và quản lý cửa hàng/sản phẩm/SKU/ảnh; xử lý đơn con theo từng cửa hàng; tạo và quản lý voucher cửa hàng, tạo đợt xả kho theo quy trình duyệt. Flash Sale và Super Sale do Admin lập lịch.
- **Quản trị:** duyệt/khóa người bán, kiểm duyệt sản phẩm/danh mục/đánh giá; xét duyệt hạn mức voucher, quản lý lịch Flash Sale và Super Sale, duyệt đợt xả kho; xác nhận thu COD, xem số liệu tài chính và ghi nhận đối soát theo nghiệp vụ demo.
- **Tính đúng nghiệp vụ:** Checkout tạo một đơn tổng và các đơn con theo Store; idempotency, ràng buộc tồn kho, phân quyền trên backend và outbox/retry ở các luồng được triển khai.

Xem [bảng đối chiếu F01–F17](docs/project-report/REQUIREMENTS_TRACEABILITY.md) và [hướng dẫn demo](docs/RUN_LOCAL.md) để xác nhận phạm vi từng chức năng, không suy rằng mọi trường hợp ngoại lệ đã được nghiệm thu.

## Kiến trúc và công nghệ thực tế

```text
Browser
  └─ React 19 / TypeScript / Vite → Nginx (localhost:5173)
                                      └─ /api → API Gateway (127.0.0.1:18080)
                                                   ├─ auth-service, user-service
                                                   ├─ seller-service, product-service
                                                   ├─ cart-service, inventory-service
                                                   ├─ order-service, notification-service
                                                   └─ Config Server / Eureka
Services → PostgreSQL theo service (Flyway)
Order events → Kafka / outbox → consumer
Catalog public cache → Redis
```

- **Backend:** Java 21, Spring Boot 3.3, Spring Cloud, Gradle multi-project; 8 service nghiệp vụ + API Gateway, Eureka Discovery và Config Server.
- **Frontend:** React 19, TypeScript, Vite; Nginx phục vụ bản build và reverse proxy Gateway.
- **Dữ liệu và tích hợp:** PostgreSQL 16 (database-per-service, không FK xuyên DB), Flyway, Kafka/outbox cho các sự kiện đơn hàng được định nghĩa, Redis cho cache Catalog.
- **Triển khai và kiểm thử:** Docker Compose, Docker secrets, GitHub Actions, JUnit/Testcontainers, Playwright.

Nguồn vận hành: [`docs/RUN_LOCAL.md`](docs/RUN_LOCAL.md). [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) là tài liệu kiến trúc tham chiếu được lập từ giai đoạn scaffold; ưu tiên code và bằng chứng chạy tại SHA cụ thể khi có sai khác.

## Chạy bản demo trên máy local

Yêu cầu: **Docker Desktop**, **JDK 21**, **Node.js 22+**. Clone vào đường dẫn ngắn trên Windows hoặc bật Git long paths do một số evidence path dài.

```bash
git clone https://github.com/duyxl56785/Project_HK5.git
cd Project_HK5
# Chỉ dành cho demo cô lập, dữ liệu giả, khi owner đã cho phép các gate tương ứng:
node scripts/demo/up.mjs --approved-inventory-pair --approved-finance-policy
```

Mở **http://localhost:5173** sau khi script báo ready. Nếu không truyền các cờ opt-in, stack vẫn khởi động nhưng Checkout bị chặn; mặc định này là chủ đích bảo vệ. Voucher C2 chỉ bật với cờ `--approved-voucher-c2` **khi có đủ điều kiện demo local**. Xem hướng dẫn [khởi động, seed, flags, troubleshooting, test và dừng stack](docs/RUN_LOCAL.md); **không sử dụng `Start-IsolatedUat.ps1` thay cho `up.mjs` khi muốn demo đầy đủ** vì fixture UAT cách ly có phạm vi seed khác.

Không commit `.env.demo`, `docker/secrets/`, JWT, cookie, password hoặc dữ liệu cá nhân.

## Kiểm thử và bằng chứng

| Lớp kiểm tra | Bằng chứng bản chốt / giới hạn |
| --- | --- |
| Backend readiness | [Run #44493061575](https://github.com/duyxl56785/Project_HK5/actions/runs/44493061575) SUCCESS trên backend SHA `cee7c87`; TV2 xác nhận từ SHA này đến frozen `eaf3bf2` không đổi `backend/`. |
| Backend / integration suite (số liệu tổng hợp được giao) | [Issue #222](https://github.com/duyxl56785/Project_HK5/issues/222) ghi **1.169 PASS / 16 SKIP**, `integrationTest` **528**; đây là số liệu owner yêu cầu đưa vào tài liệu, **chưa được TV1 chạy lại trong PR docs** và không cộng như hai lượt độc lập. |
| Frontend và E2E trên frozen SHA | [Run #44746995567](https://github.com/duyxl56785/Project_HK5/actions/runs/44746995567) SUCCESS: frontend **187 unit PASS**, E2E C2 OFF **24 PASS**, E2E C2 ON **25 PASS**, 0 skip trong hai job E2E. |
| Node test demo (số liệu tổng hợp) | [Issue #222](https://github.com/duyxl56785/Project_HK5/issues/222) ghi **21**; không phải bằng chứng TV1 đã thực thi trong PR này. |
| Final UAT local (21–22/11, frozen `eaf3bf2`) | TV2 công bố **34 PASS, 1 PARTIAL, 2 BLOCKED, 0 FAIL** trên 37 ca, [biên bản #158](https://github.com/duyxl56785/Project_HK5/issues/158#issuecomment-7075353935). Raw receipt đang được review tại [PR #221](https://github.com/duyxl56785/Project_HK5/pull/221); không gọi đó là TV5 final sign-off. |

**PARTIAL:** PROMO-02 (Flash Sale trong đúng khung giờ chưa chạy vì Docker tắt; Super Sale đã PASS). **BLOCKED:** UAT-16 claim-conflict, UAT-17 lost-response/UNKNOWN (chưa có runner, provider receipt và phê duyệt riêng). Ngoài 37 ca, RET-05 tự hoàn tồn kho sau Return còn bị chặn. Không gộp `NOT_RUN`/`BLOCKED` thành PASS; CI thành công không thay thế UAT trên stack thực.

## Giới hạn đã biết

- Chưa có bằng chứng load test quy mô lớn; không có thanh toán online, eKYC/OTP, đối tác vận chuyển thật hoặc ứng dụng native.
- Kafka dùng cho các sự kiện Order đã định nghĩa; không phải mọi lời gọi liên service đều chuyển sang Kafka.
- Return COD demo chưa tự chuyển tiền hoàn và chưa xác nhận tính đúng một-lần của luồng restock RET-05.
- Các trường hợp UNKNOWN liên service chưa có live acceptance; khi outcome không chắc chắn, giữ `NEEDS_RECONCILIATION`, không tự hoàn kho/retry mù.
- Ranh giới khóa tài khoản / access token, HTTPS, cookie/proxy, service-pair credentials và runtime flags cần review riêng trước khi dùng ngoài local. [Xem hạn chế bảo mật cụ thể](docs/RUN_LOCAL.md#giới-hạn-của-bản-demo).

## Nhóm theo báo cáo nộp và quy trình GitHub

| Thành viên | Trách nhiệm/đóng góp |
| --- | --- |
| TV2 — Nguyễn Văn Duy (`duyxl56785`) | Seller/Store/Product/Catalog; từ 01/11 tiếp nhận Auth, User, Gateway, Docker/CI và frontend shared, tích hợp và chốt demo. |
| TV3 — Trần Văn Chính | Cart, Checkout/Order creation, idempotency, trả hàng phía khách; Voucher cửa hàng và Khuyến mãi. |
| TV4 — Phạm Thế Cường | Inventory, Fulfillment/COD/Notification. |
| TV5 — Nguyễn Minh Duy | Product Review, Finance/Settlement, QA, báo cáo/UAT. |

Quyền sở hữu và reviewer hiện hành xem [`docs/OWNERSHIP.md`](docs/OWNERSHIP.md) và [`CONTRIBUTING.md`](CONTRIBUTING.md); PR #141 đã ghi nhận bàn giao TV1→TV2. Lịch sử review/PR cũ không bị gán lại cho người khác.

Mỗi thay đổi phải có Issue, feature branch, PR được người khác review và bằng chứng kiểm thử đúng SHA; **không push trực tiếp vào `main`**.
