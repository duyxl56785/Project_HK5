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

Truy cập `http://localhost:5173`. Chỉ gateway (`127.0.0.1:18080`) được mở ra máy; các service khác và PostgreSQL chỉ nằm trong mạng Docker.

## Tài khoản seed

| Vai trò | Email | Mật khẩu (trong `.env.demo`) |
| --- | --- | --- |
| Quản trị viên | `admin@hk5.local` | `ADMIN_BOOTSTRAP_PASSWORD` |
| Khách hàng | `customer@hk5.local` | `SEED_PASSWORD` |
| Người bán 1 (Tiệm Áo Sài Gòn) | `seller1@hk5.local` | `SEED_PASSWORD` |
| Người bán 2 (Nhà Xinh) | `seller2@hk5.local` | `SEED_PASSWORD` |

`.env.demo` đã được git bỏ qua và chỉ dùng cho máy local.

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

Test tích hợp `*PostgresIT` cần Docker.
