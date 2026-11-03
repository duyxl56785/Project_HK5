# Lượt chạy UAT #18 của TV2 ngày 02/11/2026

TV2 chạy, **chờ TV5 nghiệm thu**. Đây không phải kết quả QA của TV5 và không dùng để tick checklist #18. Hai ca UNKNOWN không chạy.

## Nguồn, môi trường, gate

| Mục | Giá trị |
|---|---|
| Source và stack build | `main@052388c84f378ee90732de5b37f7c7bef6fcc64f` |
| Compose project | `tv3-live-uat18`, volume và network riêng, đã `down -v` |
| Cổng | `127.0.0.1:5173` (UI), `127.0.0.1:18080` (Gateway) |
| Máy | Windows 11, Docker Desktop 29.9.1, overlayfs, Node 24.21.0 |
| Khởi động | `COMPOSE_PROJECT_NAME=tv3-live-uat18 node scripts/demo/up.mjs --skip-build --approved-inventory-pair --approved-finance-policy`, exit 0 |
| Dữ liệu | Seed demo, tài khoản `@hk5.local` giả, thêm C2 đăng ký trong lượt chạy |
| Gate | Inventory và Finance ON, chỉ trong stack dùng một lần này, TV2 duyệt. Đây là phạm vi demo local dữ liệu giả, không phải duyệt cho production |

`Start-IsolatedUat.ps1` của #146 bắt buộc hai gate OFF nên không chạy được Checkout. Vì vậy lượt này không dùng script đó. `Stop-IsolatedUat.ps1` cũng không được chạy; dọn bằng `docker compose down -v` đúng project.

## Kết quả

Thứ tự trên cùng một stack: 3 bộ TV3, Order History, E2E, 2 smoke, gap script.

| Bộ | Kết quả | File |
|---|---|---|
| `two-store` | 1 PASS, exit 0 | `two-store.log` |
| `tv4-lifecycle` | 2 PASS, exit 0 | `tv4-lifecycle.log` |
| `negative` | 6 PASS, exit 0 | `negative.log`, `negative/` |
| Order History | PASS | `order-history.json` |
| E2E | 17 PASS, exit 0 | `e2e.log` |
| `smoke-suspended-seller` | FAILS=0 | `smoke-suspended-seller.log` |
| `smoke-review` | FAILS=0 | `smoke-review.log` |
| Gap script | 62 PASS, 1 FAIL, exit 1 | `gaps.log`, `gaps.json` |

## Theo ma trận `tests/TV5_UAT.md`

| ID | Kết quả | Ghi chú |
|---|---|---|
| R01 | PASS | 5 sao và 1 sao, rating count tăng đúng |
| R02 | PASS | Buyer khác 403, chưa giao 409, ngoài đơn 409, stars 0/6 400, DB không thêm dòng |
| R03 | PASS một phần | 2 request đồng thời: 201 và 409, DB một dòng. Phần "khác SKU/line" `NOT_RUN`, sản phẩm không có nhiều SKU |
| R04 | PASS | Seller, Customer 403. Admin hide, show. Replay hide không đổi count |
| R05 | PASS | Chưa có review count 0. Hide hết về 0, show khôi phục count 1, average 1 |
| R06 | **FAIL một phần** | Hide thiếu reason, reason 501 ký tự: 400. **Comment 1001 ký tự được nhận 201**, ma trận ghi giới hạn 1000, code ghi `@Size(max = 2000)`. Comment 2001: 400. Thu hồi quyền Admin giữa hai lần gọi `NOT_RUN` |
| R07 | `NOT_RUN` | Cần bơm event trùng và lỗi Kafka |
| F01 | PASS một phần | Store thường 100000: phí 5000, net 95000. House `NOT_RUN`, ngoài phạm vi MD11 |
| F02 | `NOT_RUN` | House ngoài phạm vi MD11 |
| F03 | PASS trên trạng thái đạt được | F/F/F, T/F/F, T/T/T bị từ chối, T/T/F settle được. COD trước giao bị từ chối nên F/T/* không đạt được qua API. Seller không ghi được COD, không settle được |
| F04 | PASS | 5 settle đồng thời: 1 thành công, 4 từ chối. Tiền cộng một lần. Restart `order-service`, retry bị từ chối, dữ liệu còn nguyên |
| F05 | PASS | Seller A không đọc, không thao tác đơn Store B (404). List và finance không có dòng Store B. Admin finance 403. Export chưa có |
| F06 | `NOT_RUN` | Chờ contract D06 |
| F07 | `NOT_RUN` | Chờ contract D07 |
| C01 | PASS | `two-store` và `negative`. C2 đọc đơn của C: 404 |
| C02 | PASS một phần | Tranh hàng cuối: 200 và 409, tồn 0, reserved 1. Timeout từng service `NOT_RUN` |
| C03 | PASS | Hủy và từ chối hoàn kho một lần, thao tác lặp 409. Đơn hủy không settle, không ghi COD được |
| A01 | PASS | Không token, token giả 401. Sai role 403. Gateway `/internal` 404. Site trả SPA shell, không có dữ liệu |

Bước 7 của kịch bản, ảnh UI: `NOT_RUN`. Hai ca UNKNOWN: `NOT_RUN`, cần TV3, TV4, TV5 duyệt.

## Phát hiện

**P3, R06, giới hạn comment.** `ReviewRequest.comment` là `@Size(max = 2000)`, `tests/TV5_UAT.md` ghi "comment quá 1000". Một trong hai phải đổi. TV5 hoặc TV2 chốt. TV2 không tự sửa trong lượt này.

## Hai lượt gap đầu

Hai file `gaps-run1-no-async-wait.log` và `gaps-run2-before-cache-wait.log` giữ nguyên để truy vết. Các FAIL ở đó do script, đã kiểm từng nguyên nhân:

- Quyền review đến qua Kafka, release và consume đi qua outbox. Script đọc ngay sau thao tác, chưa chờ.
- Catalog public cache khoảng 10 giây. Lượt 2 đọc rating ngay nên ca "hidden drops from count" PASS giả. Lượt cuối chờ đúng giá trị.
- Script kỳ vọng sai: Checkout trả 200 chứ không phải 201, `settledAt` nanosecond so với microsecond sau khi đọc từ DB. Chạy lại trên stack cũ làm reserved C02 là 2.
- `/internal` qua cổng 5173 trả 200 vì SPA fallback, không có dữ liệu. Gateway 18080 trả 404.

Lượt cuối chạy trên stack mới.

## Log service

`service-error-counts.txt`: order, inventory, cart, auth, user 0. Các dòng còn lại đã phân loại:

- `product-service` 2: duplicate key `product_reviews_order_product_uk` của R03, đúng thiết kế.
- `seller-service` 25: `smoke-review` restart PostgreSQL.
- `notification-service` 9: dòng INFO rejoin consumer group chứa chữ Exception.
- `api-gateway` 342: lúc khởi động và lúc restart service/PostgreSQL theo chủ đích, `Connection refused`. Quét theo mẫu, không đọc từng dòng.

## Hash và bảo mật

`SHA256SUMS` có hash từng file, trừ chính nó và README. Log giữ nguyên byte, có đường dẫn máy cục bộ và khoảng trắng cuối dòng. Đã quét toàn bộ file so với 23 giá trị trong `.env.demo` và mẫu JWT, Bearer: 0 khớp.

Hash tính trên byte Git blob. Kiểm bằng `git -c core.autocrlf=false archive` rồi `sha256sum -c`, không kiểm trên bản checkout Windows vì Git có thể đổi LF thành CRLF. Bản đầu (`3a5e438`) ghi nhầm hash bản CRLF của `tooling/gaps.mjs` (`bf8c71…`); TV5 phát hiện ở #165, đã sửa thành hash blob `97d148…`. Nội dung file không đổi.

`tooling/` là script tạm, không thuộc suite chính thức: `gaps.mjs`, `run.mjs`, `order-history.cjs`, `docker-shim.cjs`. Oracle của gap script là HTTP qua site và `select` trên PostgreSQL. Image: chỉ ghi frontend trong `images.txt`, jar Java bind-mount từ source trên, hash jar `NOT_RUN`.

Teardown: 0 container, 0 volume `uat18`, `hk5-demo_pgdemo` và `hk5-demo_productimages` còn nguyên.
