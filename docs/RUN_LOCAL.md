# Chạy toàn bộ dự án trên máy

Cần Docker Desktop đang chạy, JDK 21 và Node.js 22 trở lên.

Trên Windows, clone ở đường dẫn ngắn hoặc bật `git config --global core.longpaths true`: một số file trong `reports/evidence/` có tên dài, clone vào thư mục quá sâu sẽ lỗi `Filename too long`.

## Khởi động

Từ thư mục gốc của repo:

```bash
node scripts/demo/up.mjs --approved-inventory-pair --approved-finance-policy
```

Lệnh này tạo `.env.demo` với secret ngẫu nhiên nếu chưa có, ghi từng secret vào `docker/secrets/`, build jar và image frontend, chạy `docker/compose.demo.yml`, chờ gateway sẵn sàng rồi seed dữ liệu qua API công khai. Chạy lại an toàn; thêm `--skip-build` nếu backend không đổi.

Truy cập `http://localhost:5173`. Nginx phục vụ bản build của frontend và chuyển `/api` tới gateway. Chỉ Nginx và gateway (`127.0.0.1:18080`) được mở ra máy; các service khác, PostgreSQL, Kafka, Redis, Eureka và Config Server chỉ nằm trong mạng Docker.

Khi sửa giao diện, dừng container `frontend` rồi dùng Vite trên cùng cổng:

```bash
docker compose --env-file .env.demo -f docker/compose.demo.yml stop frontend
```

```bash
cd frontend && npm ci && npm run dev
```

## Tài khoản seed

| Vai trò | Email | Mật khẩu (trong `.env.demo`) |
| --- | --- | --- |
| Quản trị viên | `admin@hk5.local` | `ADMIN_BOOTSTRAP_PASSWORD` |
| Khách hàng | `customer@hk5.local` | `SEED_PASSWORD` |
| Người bán 1 (Tiệm Áo Sài Gòn) | `seller1@hk5.local` | `SEED_PASSWORD` |
| Người bán 2 (Nhà Xinh) | `seller2@hk5.local` | `SEED_PASSWORD` |

`.env.demo` và `docker/secrets/` đã được git bỏ qua và chỉ dùng cho máy local. Compose nạp secret bằng Docker secrets, service đọc từ `/run/secrets` qua `configtree`, nên `docker inspect` không thấy mật khẩu hay token.

`INVENTORY_ORDER_PAIR_ENABLED` và `HK5_FINANCE_POLICY_APPROVED` mặc định là `false`. Không có hai cờ dưới đây thì stack vẫn chạy, seed và xem hàng được, nhưng Checkout bị từ chối. Mỗi lần chạy, `gen-env.mjs` đặt lại từng cổng theo cờ của lần đó, nên `.env.demo` cũ đang `true` sẽ về `false` nếu không truyền cờ.

| Cờ | Cổng | Tình trạng phê duyệt |
| --- | --- | --- |
| `--approved-finance-policy` | `HK5_FINANCE_POLICY_APPROVED` | MD09 ở [M01_FINANCE_DECISION.md](project-report/M01_FINANCE_DECISION.md), chỉ cho demo local. Chờ TV4, TV5 ký |
| `--approved-inventory-pair` | `INVENTORY_ORDER_PAIR_ENABLED` | Chưa có. Chờ TV1, TV3 duyệt ủy quyền pair và delegated provenance |

Cờ chỉ là thao tác bật cho demo local với dữ liệu giả, không phải bằng chứng owner đã duyệt. Kết quả CI chạy với cả hai cờ chỉ chứng minh đường opt-in của demo local, không chứng minh môi trường khác. Đường mặc định tắt được kiểm ở `scripts/demo/gen-env.test.mjs`. Môi trường khác cần quyết định riêng.

## Luồng nghiệp vụ chính

1. Khách hàng thêm sản phẩm của hai cửa hàng vào giỏ, thanh toán COD. Đơn tách theo từng cửa hàng.
2. Mỗi người bán xử lý đơn: Xác nhận, Chuẩn bị, Sẵn sàng bàn giao, Giao cho vận chuyển, Đã giao.
3. Khách hàng đánh giá sản phẩm sau khi đơn cửa hàng ở trạng thái Đã giao.
4. Quản trị viên vào COD: xác nhận đã thu, sau đó thanh toán cho người bán. Tài chính sàn và tài chính cửa hàng cập nhật theo.

## Dừng và xóa dữ liệu

```bash
docker compose --env-file .env.demo -f docker/compose.demo.yml down
```

Thêm `-v` vào lệnh trên để xóa cả volume database. Khi tạo lại `.env.demo` bằng `node scripts/demo/gen-env.mjs --force`, phải xóa volume vì mật khẩu database đã đổi.

## Kiểm thử

```bash
cd backend && ./gradlew test
```

```bash
cd frontend && npm test
```

Test tích hợp `*PostgresIT` và `OrderEventKafkaIT` cần Docker.

E2E trên stack đang chạy (cần `npx playwright install chromium` một lần):

```bash
cd frontend && npm run e2e
```

Kiểm tra Config, Eureka, Kafka, Redis và các tình huống sự cố trên stack đang chạy (script sẽ tắt và bật lại container):

```bash
node scripts/demo/smoke-platform.mjs
```

## Giới hạn của bản demo

Bản này chỉ dùng cho demo cô lập trên máy local với tài khoản và dữ liệu giả. Chưa được duyệt cho môi trường khác.

**Khóa tài khoản không thu hồi ngay mọi quyền.**

- Khóa chặn ngay refresh, đăng nhập và phép ghi có Bearer token dưới `/api/**`.
- Access token đã phát vẫn đọc được tới khi hết hạn, tối đa 900 giây cộng 60 giây lệch đồng hồ. `GET`, `HEAD`, `OPTIONS` không hỏi Auth. Việc này gồm cả dữ liệu riêng tư như Finance, Admin, Order, Notification.
- Mở khóa không hồi sinh refresh token cũ, nhưng access token cũ chưa hết hạn lại ghi được, vì Auth chỉ so trạng thái và role, không so thời điểm phát token. `anAccessTokenFromBeforeALockIsRefusedWhileLockedAndWorksAgainAfterUnlock` ghi lại hành vi này.
- Khóa tài khoản không khóa cửa hàng, nên sản phẩm của Seller bị khóa vẫn hiển thị.

**Phạm vi đã kiểm thử cho phép ghi đang chạy khi thu hồi quyền.**

| Service | Bằng chứng |
| --- | --- |
| Cart | HTTP thật với Auth thật |
| Order, Product | HTTP thật với PostgreSQL, Auth là stub |
| Seller | Smoke trên stack |
| User, Notification | NOT_RUN |

Chưa chứng minh được request đổi thread, chạy async hoặc nhiều transaction. Chưa kiểm thử xử lý khi `closeAll` timeout hoặc trả 503. Bảng `write_fence_abort` chưa được dọn.

**Trước khi dùng ngoài local cần:** owner chốt chính sách đọc dữ liệu nhạy cảm và kiểm tra thời điểm phát token; HTTPS, ranh giới cookie, CORS và proxy; kho secret khi chạy và rotation theo từng cặp service; không dùng lại key, token trong `.env.demo`; `HK5_FINANCE_POLICY_APPROVED`, `INVENTORY_ORDER_PAIR_ENABLED` giữ mặc định tắt cho tới khi owner quyết định triển khai. Quyết định M01 ở [M01_FINANCE_DECISION.md](project-report/M01_FINANCE_DECISION.md) chỉ áp dụng cho demo local.

## Hạ tầng

| Thành phần | Vai trò | Khi thành phần này tắt |
| --- | --- | --- |
| Config Server | Cấp cấu hình dùng chung từ `backend/config-server/src/main/resources/config/`; secret vẫn nằm trong biến môi trường | Service đang chạy không ảnh hưởng; service khởi động lại dùng cấu hình cục bộ, không có địa chỉ Kafka/Redis của Docker |
| Eureka | Gateway định tuyến `lb://`; lời gọi nội bộ chọn instance theo tên service | Gateway và lời gọi nội bộ dùng tên DNS của Docker |
| Kafka | `order.placed.v1`, `seller-order.status-changed.v1`; bản ghi lỗi sau 3 lần xử lý vào `<topic>.dlt` | Đặt hàng vẫn chạy, sự kiện chờ trong outbox và gửi lại khi Kafka hoạt động |
| Redis | Cache Catalog công khai: mới 10 giây, giữ tối đa 5 phút | Catalog đọc trực tiếp nguồn |

Catalog công khai có thể chậm tối đa 10 giây sau khi đổi giá, tồn kho hoặc khóa cửa hàng. Giỏ hàng, đặt hàng và mọi thao tác ghi không đọc cache.
