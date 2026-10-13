# Smart E-Commerce Marketplace V2 — Project_HK5

> **TRẠNG THÁI: BỘ KHUNG DỰ ÁN (SCAFFOLD), CHƯA PHẢI HỆ THỐNG V2 ĐÃ TRIỂN KHAI.**
> Repository được khởi tạo để 5 thành viên cùng phát triển theo phân công 3 tuần.
> Mã nguồn V2 cũ (nếu có) phải được đối chiếu trước khi chuyển vào; không ghi đè mất tính năng hiện có.

## Công nghệ và mô hình theo báo cáo V2

- Backend: Java 21, Spring Boot 3.3, Spring Cloud 2023, Gradle multi-project; 8 service nghiệp vụ.
- Infrastructure Java: API Gateway, Eureka Discovery Server, Config Server.
- Frontend: React 19, TypeScript, Vite, Tailwind CSS v4 (giao diện khởi tạo).
- Dữ liệu mục tiêu: PostgreSQL database-per-service, Flyway; Redis; Kafka/outbox. **Chưa có nghiệp vụ thực hoặc schema/migration ở bộ khung.**

## Bắt đầu

```bash
git clone https://github.com/duyxl56785/Project_HK5.git
cd Project_HK5
git switch main
git pull --ff-only origin main
```

Xem [quy chuẩn làm việc](CONTRIBUTING.md), [sơ đồ kiến trúc](docs/ARCHITECTURE.md), [vùng sở hữu](docs/OWNERSHIP.md) và [hướng dẫn chạy](docs/DEVELOPMENT.md).

Sau khi scaffold được merge vào `main`, mỗi thành viên tạo branch theo Issue:
```bash
git switch main
git pull --ff-only
git switch -c feat/tv2-product-catalog
```

**Không push trực tiếp lên main.** Tạo Pull Request và yêu cầu review của owner liên quan.

## Phân công

| TV | Thành viên | Phụ trách |
|---|---|---|
| 1 | Lê Xuân Bảo | Auth, User, Gateway, Eureka/Config, Docker, Frontend shared |
| 2 | Nguyễn Văn Duy | Seller/Store, Product catalog, category, images, moderation |
| 3 | Trần Văn Chính | Cart, checkout, order creation, idempotency |
| 4 | Phạm Thế Cường | Inventory, order fulfillment, COD, notifications |
| 5 | Nguyễn Minh Duy | Product reviews, order finance/settlement, QA/docs |

Issues tuần 1: [#1](../../issues/1), [#2](../../issues/2), [#3](../../issues/3), [#4](../../issues/4), [#5](../../issues/5).

## Tổ chức mã nguồn

```text
backend/
  auth-service/ user-service/ seller-service/ product-service/
  inventory-service/ cart-service/ order-service/ notification-service/
  api-gateway/ discovery-server/ config-server/
frontend/
  src/app/ src/shared/ src/features/
docker/
docs/
.github/
```

`backend` có bộ khởi tạo Spring Boot từng module, chưa bao gồm API, database, security hoặc nghiệp vụ. `frontend` là shell React; không được xem trang demo là chức năng thật. `docker/compose.dev.yml` dành cho hạ tầng phát triển cục bộ, không phải docker compose 16 service V2 đã nghiệm thu.

## Quy ước nghiệm thu

Mỗi Issue cần liên kết PR, kết quả kiểm thử có bằng chứng và cập nhật contract khi thay API/schema/event. Các trạng thái: `DONE`, `PARTIAL`, `NOT_VERIFIED`. Không báo PASS nếu chưa chạy test.
