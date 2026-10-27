# Smart E-Commerce Marketplace V2 — Project_HK5

> **Trạng thái: ĐANG PHÁT TRIỂN / PARTIAL** · Cập nhật: **27/10/2026**  
> Dự án nhóm 5 thành viên xây dựng sàn thương mại điện tử đa người bán, ưu tiên **thanh toán khi nhận hàng (COD)**. Nhiều thành phần đã được lập trình, kiểm thử và merge nhưng **toàn hệ thống chưa hoàn tất tích hợp, chưa được nghiệm thu E2E hoặc triển khai production**.

## 1. Giới thiệu

Hệ thống hướng tới ba nhóm người dùng:

- **Customer:** duyệt danh mục/sản phẩm, giỏ hàng, đặt hàng COD và theo dõi đơn.
- **Seller:** đăng ký hồ sơ người bán, quản lý Store, Product, ảnh và xử lý đơn của Store.
- **Admin:** kiểm duyệt Seller/Product, quản lý danh mục, xử lý nghiệp vụ và đối soát theo phân quyền. Theo báo cáo Use Case, **UC16 – Xác nhận đã thu COD** là chức năng Admin; xác nhận thu tiền và đối soát là hai bước khác nhau.

Đây là **phạm vi thiết kế**; không phải tất cả chức năng đã có endpoint hoặc giao diện chạy với backend thật.

## 2. Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.3.5, Spring Cloud 2023, Gradle multi-project |
| Dịch vụ nghiệp vụ | Auth, User, Seller, Product, Cart, Order, Inventory, Notification |
| Hạ tầng | API Gateway, Eureka Discovery, Config Server |
| Cơ sở dữ liệu | PostgreSQL, JPA/Hibernate, Flyway |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4 |
| Kiểm thử | JUnit, PostgreSQL/Testcontainers, kiểm thử frontend và browser theo từng PR, GitHub Actions |
| Thiết kế mở rộng | Redis, Kafka/outbox và tích hợp liên service; không mặc định đã chạy end-to-end |

Cấu trúc chính: `backend/`, `frontend/`, `docker/`, `docs/`, `tests/`, `reports/` và `.github/`.

Tham khảo [kiến trúc](docs/ARCHITECTURE.md), [phân quyền mã nguồn](docs/OWNERSHIP.md), [quy định làm việc](CONTRIBUTING.md).

## 3. Tiến độ 5 thành viên

| Thành viên | Phụ trách | Đã thực hiện | Đang hoàn thiện |
|---|---|---|---|
| **TV1 – Lê Xuân Bảo** | Auth/User, JWT, Gateway, Docker, frontend chung | Hạ tầng, kiểm thử Auth/Gateway, các nhánh Auth runtime và shared Router/API client | Xác thực/ủy quyền thật, kết nối Gateway và các frontend vào API |
| **TV2 – Nguyễn Văn Duy** | Seller, Store, Category, Product, Catalog, ảnh, kiểm duyệt và UI | Seller/Store persistence, Category, Product lifecycle/persistence, Catalog batch/search, handler nội bộ, các component giao diện và demo | Product image lifecycle, protected API, Catalog public và tích hợp UI |
| **TV3 – Trần Văn Chính** | Cart, Checkout COD, Order, idempotency | Logic Cart/Order, snapshot/kiểm tra giỏ nhiều Store, Catalog preflight, ánh xạ SKU cho Inventory, các bộ test | Ghép Checkout–Inventory thực tế và E2E hai Store |
| **TV4 – Phạm Thế Cường** | Inventory, fulfillment, COD, notification | Các nghiệp vụ và kiểm thử chống âm kho, đặt giữ/hoàn kho, quyền và khôi phục thao tác | Luồng giao hàng/COD, service-pair và notification end-to-end |
| **TV5 – Nguyễn Minh Duy** | Reviews, Finance, settlement, QA và báo cáo | Quy tắc Review/Finance, kiểm thử PostgreSQL/rollback/bảo mật, test matrix và đánh giá PR | Đối soát và nghiệm thu luồng thanh toán/đơn hàng thật |

**Một số phần TV2 đã merge vào `main`:** [Category #7](https://github.com/duyxl56785/Project_HK5/pull/7), [Seller DB #29](https://github.com/duyxl56785/Project_HK5/pull/29), [Store DB #52](https://github.com/duyxl56785/Project_HK5/pull/52), [Product Persistence #88](https://github.com/duyxl56785/Project_HK5/pull/88), [Lifecycle #89](https://github.com/duyxl56785/Project_HK5/pull/89), [Catalog Batch #90](https://github.com/duyxl56785/Project_HK5/pull/90), [Catalog Search #91](https://github.com/duyxl56785/Project_HK5/pull/91), [Seller/Store facts #94](https://github.com/duyxl56785/Project_HK5/pull/94) và [Product handlers #97](https://github.com/duyxl56785/Project_HK5/pull/97).

**Frontend TV2 đã merge:** [Category Browser #74](https://github.com/duyxl56785/Project_HK5/pull/74), [Seller/Store Overview #76](https://github.com/duyxl56785/Project_HK5/pull/76). Các component này **chưa được nối vào website production với API thật**.

### Các phần đang ở PR riêng (chưa có trong `main`)

| PR | Nội dung | Giới hạn hiện tại |
|---|---|---|
| [#87](https://github.com/duyxl56785/Project_HK5/pull/87) | Image Storage | Lưu file nội bộ; chưa phải API upload thật |
| [#95](https://github.com/duyxl56785/Project_HK5/pull/95) | Product Management UI | Component độc lập; chưa nối Router/API |
| [#108](https://github.com/duyxl56785/Project_HK5/pull/108) | Product Owner List | PostgreSQL query nội bộ, chưa có protected HTTP |
| [#109](https://github.com/duyxl56785/Project_HK5/pull/109) | Metadata và đặt chỗ ảnh | PostgreSQL/Flyway V3; chưa có file binding/READY/upload |
| [#110](https://github.com/duyxl56785/Project_HK5/pull/110) | **Bản demo UI/UX TV2** | Dữ liệu mô phỏng, chưa xác thực/kết nối backend |
| [#102](https://github.com/duyxl56785/Project_HK5/pull/102), [#105](https://github.com/duyxl56785/Project_HK5/pull/105) | Auth/Gateway và frontend chung TV1 | Đang chờ tích hợp thực tế |
| [#106](https://github.com/duyxl56785/Project_HK5/pull/106) | Product SKU → Inventory intent TV3 | Chưa phải Checkout/Inventory liên service |

**Trạng thái PR thay đổi theo thời gian:** kiểm tra trực tiếp review, CI, merge và commit HEAD trước khi lấy làm bằng chứng.

## 4. Xem demo giao diện Customer – Seller – Admin

Bản demo UI/UX độc lập ở **[PR #110](https://github.com/duyxl56785/Project_HK5/pull/110)**, có thao tác mô phỏng: Seller tạo Product → gửi duyệt → Admin duyệt → Customer tìm thấy sản phẩm; đồng thời có Seller/Store, Category và ảnh xem trước.

**Bản demo chưa merge vào `main` ở mốc cập nhật này**. Để chạy bằng Windows PowerShell:

```powershell
git clone https://github.com/duyxl56785/Project_HK5.git
cd Project_HK5
git fetch origin pull/110/head:tv2-demo-pr110
git switch tv2-demo-pr110
cd frontend
if (Test-Path package-lock.json) { npm.cmd ci } else { npm.cmd install }
npm.cmd run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Mở **http://127.0.0.1:5174/demo/** và chọn vai trò **Customer / Seller / Admin** trong màn hình demo. Xem hướng dẫn đầy đủ tại `frontend/demo/README.md` trên nhánh PR #110.

Theo báo cáo PR #110: **213 bài unit/DOM + 46 bài Chrome PASS** trong các lượt chạy local phù hợp; **không phải kiểm thử E2E với JWT/PostgreSQL/API thật**. Dữ liệu, chuyển vai trò và preview ảnh chỉ tồn tại trong môi trường demo.

## 5. Chạy project từ `main`

Yêu cầu: **Git, JDK 21, Node.js/npm**, Docker/Engine cho các bài kiểm thử có PostgreSQL Testcontainers.

```powershell
git clone https://github.com/duyxl56785/Project_HK5.git
cd Project_HK5

# Kiểm thử backend; trong một số môi trường cần Docker hoạt động
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC -Dapi.version=1.44'
.\backend\gradlew.bat -p backend build --console=plain
```

Chạy frontend từ **terminal khác**:

```powershell
cd Project_HK5\frontend
npm.cmd install
npm.cmd run dev
```

**Lưu ý:** frontend ở `main` hiện còn các trang khung/placeholder; chạy được frontend không có nghĩa Seller/Product/Auth/Checkout đã kết nối. Cấu hình PostgreSQL và cách khởi động service xem [hướng dẫn phát triển](docs/DEVELOPMENT.md) và [Docker runbook](docker/README.md). Không xóa volume/database hoặc chạy `flyway clean` để xử lý sự cố môi trường.

## 6. Việc còn thiếu để hoàn thành dự án

1. Auth/JWT/current roles, Seller/Store authority, API Gateway và các service-pair được kiểm thử end-to-end.
2. Protected Seller/Store/Product APIs, frontend Router/API client và public Catalog chỉ hiển thị Product đủ điều kiện.
3. Upload ảnh thật, liên kết Product–Image và cơ chế cleanup/recovery.
4. Checkout COD hai Store kết nối Inventory/Fulfillment/Finance thật; Admin xác nhận đã thu COD theo Use Case UC16.
5. E2E toàn bộ quy trình, các tình huống phân quyền, lỗi/timeout/retry, dữ liệu demo thực tế và nghiệm thu.

## 7. Theo dõi tiến độ

[TV1 – Issue #1](https://github.com/duyxl56785/Project_HK5/issues/1) · [TV2 – Issue #2](https://github.com/duyxl56785/Project_HK5/issues/2) · [TV3 – Issue #3](https://github.com/duyxl56785/Project_HK5/issues/3) · [TV4 – Issue #4](https://github.com/duyxl56785/Project_HK5/issues/4) · [TV5 – Issue #5](https://github.com/duyxl56785/Project_HK5/issues/5).

Mọi thay đổi đi qua **branch → Pull Request → review/test → merge**. Phân biệt rõ `PASS`, `FAIL`, `SKIPPED`, `NOT_RUN` theo **đúng PR/commit**; không cộng kết quả của nhiều lượt chạy thành tổng nghiệm thu. Không commit secret hoặc thay đổi/xóa dữ liệu môi trường có sẵn.

**Tình trạng nghiệm thu tổng: PARTIAL.** README ghi nhận tiến độ tại thời điểm cập nhật, không thay thế QA hoặc bằng chứng chức năng chạy thực tế.
