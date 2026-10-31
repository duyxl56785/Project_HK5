# Chạy toàn bộ dự án trên máy

Cần Docker Desktop đang chạy, JDK 21 và Node.js 22 trở lên.

## Khởi động

Từ thư mục gốc của repo:

```bash
node scripts/demo/up.mjs
```

Lệnh này tạo `.env.demo` với secret ngẫu nhiên nếu chưa có, build jar, chạy `docker/compose.demo.yml`, chờ gateway sẵn sàng rồi seed dữ liệu qua API công khai. Chạy lại an toàn; thêm `--skip-build` nếu backend không đổi.

Mở giao diện:

```bash
cd frontend
npm ci
npm run dev
```

Truy cập `http://localhost:5173`. Chỉ gateway (`127.0.0.1:18080`) được mở ra máy; các service khác, PostgreSQL, Kafka, Redis, Eureka và Config Server chỉ nằm trong mạng Docker.

## Tài khoản seed

| Vai trò | Email | Mật khẩu (trong `.env.demo`) |
| --- | --- | --- |
| Quản trị viên | `admin@hk5.local` | `ADMIN_BOOTSTRAP_PASSWORD` |
| Khách hàng | `customer@hk5.local` | `SEED_PASSWORD` |
| Người bán 1 (Tiệm Áo Sài Gòn) | `seller1@hk5.local` | `SEED_PASSWORD` |
| Người bán 2 (Nhà Xinh) | `seller2@hk5.local` | `SEED_PASSWORD` |

`.env.demo` đã được git bỏ qua và chỉ dùng cho máy local.

Năm cờ `INVENTORY_ORDER_PAIR_ENABLED` và `HK5_FINANCE_*` trong file này được duyệt cho môi trường local theo [M01_FINANCE_DECISION.md](project-report/M01_FINANCE_DECISION.md). Môi trường khác cần quyết định riêng.

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

Kiểm tra Config, Eureka, Kafka, Redis và các tình huống sự cố trên stack đang chạy (script sẽ tắt và bật lại container):

```bash
node scripts/demo/smoke-platform.mjs
```

## Hạ tầng

| Thành phần | Vai trò | Khi thành phần này tắt |
| --- | --- | --- |
| Config Server | Cấp cấu hình dùng chung từ `backend/config-server/src/main/resources/config/`; secret vẫn nằm trong biến môi trường | Service đang chạy không ảnh hưởng; service khởi động lại dùng cấu hình cục bộ, không có địa chỉ Kafka/Redis của Docker |
| Eureka | Gateway định tuyến `lb://`; lời gọi nội bộ chọn instance theo tên service | Gateway và lời gọi nội bộ dùng tên DNS của Docker |
| Kafka | `order.placed.v1`, `seller-order.status-changed.v1`; bản ghi lỗi sau 3 lần xử lý vào `<topic>.dlt` | Đặt hàng vẫn chạy, sự kiện chờ trong outbox và gửi lại khi Kafka hoạt động |
| Redis | Cache Catalog công khai: mới 10 giây, giữ tối đa 5 phút | Catalog đọc trực tiếp nguồn |

Catalog công khai có thể chậm tối đa 10 giây sau khi đổi giá, tồn kho hoặc khóa cửa hàng. Giỏ hàng, đặt hàng và mọi thao tác ghi không đọc cache.
