# TV3 Week 1 — Cart Backend (Issue #3)

**Owner:** Trần Văn Chính (`@tranvanchinhbg2006-alt`). **Status: PARTIAL — REVIEW REQUIRED**, mã nguồn xây trên ZIP ngày 16/10/2026, chưa merge GitHub.

## Source implementation

- REST: `GET /api/cart`, `POST /api/cart/items`, `PATCH /api/cart/items/{productId}`, `DELETE /api/cart/items/{productId}`.
- `Cart`, `CartItem`, `CartRepository`, Flyway `V1__cart_baseline.sql` (PostgreSQL riêng service).
- Revision optimistic locking + pessimistic locking for writes; `expectedRevision` required only when supplied by checkout client.
- Phân nhóm theo cặp `(sellerId, storeId)` được trả về `groups`. Seller/Store + price snapshot resolve **từ CatalogPort**; không nhận Seller/Store tự khai từ client.
- Internal route dự thảo **KHÔNG PUBLIC ROUTE**: `POST /internal/cart/{claim,release,finalize}` bắt buộc JWT customer đã verify *và* `X-Internal-Token` riêng checkout→cart. Chưa có Gateway route, phải được TV1/TV4 review.
- Mặc định `UnconfiguredCartCatalog` từ chối thao tác thêm hàng HTTP 503; không trả thông tin seller/store giả khi Product Service chưa có contract.
- Spring Security dùng JWT verified signature/issuer/audience + role `CUSTOMER`; không sử dụng header `X-Customer-Id` từ client.

## Running (pending TV1 auth and TV2 catalog)

Chuẩn bị JDK 21, Gradle 8.x, PostgreSQL. Tạo DB riêng trên PostgreSQL dev:

```sql
CREATE DATABASE hk5_cart OWNER hk5;
```

Tạo biến môi trường `HK5_CART_DB_PASSWORD` và nếu cần `HK5_CART_JDBC_URL`, `HK5_CART_DB_USER`. JWT phải được cấp từ TV1 (`JWT_ISSUER_URI`, `JWT_JWK_SET_URI`, `JWT_AUDIENCE`); nếu thiếu verifier, API từ chối bảo mật mặc định. Biến `APP_INTERNAL_CHECKOUT_TOKEN` phải được cấu hình để internal operation hoạt động, không đưa secret vào repo.

**Còn blocker TV2:** `CartCatalogPort.resolve(productId)` phải lấy và xác minh product/store/seller authoritative. Thay adapter placeholder bằng adapter gọi API đã duyệt rồi chạy integration tests. Không tự công bố endpoint TV2 giả định.

```bash
cd backend
gradle :cart-service:test
gradle :cart-service:bootRun
```

`GET /actuator/health` để healthcheck. Không khẳng định đã build/bootRun khi chưa có Gradle/Postgres/JWT thật.

## Test cases

| ID | Test | Expected |
|---|---|---|
| CART-01 | Add 2 units | same trusted product/store/seller; 2 units |
| CART-02 | Update to 5 units | 5 units |
| CART-03 | Remove item | cart empty |
| CART-04 | Claimed cart write | reject, unchanged |
| CART-05 | Wrong claim release | reject |
| CART-06 | Same-key claim retry | idempotent |
| CART-07 | Finalize matching claim | cart cleared |
| CART-08 | Missing JWT/internal token | deny access |
| CART-09 | Product service down | 503 fail-closed |

JUnit tests are included. They require Gradle dependencies and **are NOT_RUN** in this offline environment.

## Integration coordination

- TV1: approve JWT iss/aud, gateway allowlist; keep `/internal/**` off the public gateway.
- TV2: approve product details contract/active/seller/store ownership and DB identifier wire type.
- TV4: approve cart claim/release/finalize retry behavior and inventory flow.
- TV5: review order domain/migrations separately; this file does not authorize changing TV4/TV5 code.

**Important:** V2 source and its original contracts are absent from this ZIP; reconciling their semantics is mandatory before merge.
