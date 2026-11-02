# Live acceptance #147, lượt chạy của TV2 ngày 02/11/2026

Bằng chứng cho [báo cáo trên #147](https://github.com/duyxl56785/Project_HK5/pull/147#issuecomment-6579840719). Kết quả: 10 PASS, 0 FAIL, 0 SKIP. Hai ca UNKNOWN `NOT_RUN`. Đây không phải UAT #18 và chưa được TV3 hay TV5 xác nhận độc lập.

## Nguồn và môi trường

| Mục | Giá trị |
|---|---|
| Source và stack build | `b4e869dace9d03d4fbb27331d6194b11bb083bc9` |
| Tree | `a7b3c9cc7c1acc38348bb4ad635f121508a95162` |
| Compose project | `tv3-live-147`, volume `tv3-live-147_pgdemo`, `tv3-live-147_productimages`, network `tv3-live-147_default` |
| Cổng | `127.0.0.1:5173` (UI), `127.0.0.1:18080` (Gateway) |
| Máy | Windows 11, Docker Desktop 29.9.1, storage driver `overlayfs`, Playwright 1.64.1, Node 24.21.0 |
| Khởi động | `COMPOSE_PROJECT_NAME=tv3-live-147 node scripts/demo/up.mjs --approved-inventory-pair --approved-finance-policy`, exit 0, xem `stack-up.log` |
| Cấu hình | `.env.demo` và `docker/secrets` sinh mới trong worktree riêng, không dùng chung với checkout khác |
| Contract | `TV3_NEG_REFUSAL_CONTRACT=itemized147` |

TV2 xác nhận tên project `tv3-live-147` thay cho `hk5-tv3-147` đã duyệt ban đầu. Lý do: `negative.live.spec.mjs` chỉ nhận `^tv3-live-[a-z0-9-]+$`.

Image: chỉ ghi được frontend `sha256:8cc8036f3077abe84805bb6d4706303ab2bef9c631d13d45ee05d98db097eec1`. Service Java dùng jar bind-mount build từ source trên, không ghi riêng hash jar hay ID image nền của lượt chạy. Mục này `NOT_RUN`.

## 10 ca

| # | Ca | Kết quả | File |
|---|---|---|---|
| 1 | real browser: two Stores, exact stock delta and same-key replay | PASS | `two-store.log` |
| 2 | real HTTP: customer cancellation and Seller rejection each release exact held lines once | PASS | `tv4-lifecycle.log` |
| 3 | TV4 contract gate: CONSUME follows committed DISPATCHED, never waits for DELIVERED/COD | PASS | `tv4-lifecycle.log` |
| 4 | replay-body same key with changed expectedItems rejects without effects | PASS | `negative/replay-body/` |
| 5 | replay-shipping same key with changed shipping rejects without effects | PASS | `negative/replay-shipping/` |
| 6 | expected-items mismatch rejects before Inventory | PASS | `negative/expected-items/` |
| 7 | catalog-price changed since Cart rejects before Inventory | PASS | `negative/catalog-price/` |
| 8 | insufficient-stock two Store batch rejects without partial reservation | PASS | `negative/insufficient-stock/` |
| 9 | product-inactive one unsellable Store line rejects the whole Checkout | PASS | `negative/product-inactive/` |
| 10 | Order History trên browser | PASS | `order-history.json` |

Exit code: `two-store` 0, `tv4-lifecycle` 0, `negative` 0 (`summary.json`), Order History 0. `negative/junit.xml` và `negative/playwright.json` là báo cáo gốc của ca 4–9.

## Oracle DB

- Ca 4–9: snapshot trước/sau của Order, SellerOrder count, journal, draft, Inventory, Cart nằm trong `before.json`, `after.json`, `comparison.json` của từng thư mục. Thư mục `negative/setup/` là fixture và đơn baseline. Cột dữ liệu lấy bằng `BEGIN READ ONLY`.
- Ca 1–3: test có assert qua oracle chỉ đọc, nhưng giá trị trước/sau **không được lưu**. Oracle dạng file của ba ca này `NOT_RUN`, không dựng lại.
- Ca 10: không truy vấn DB.

## Công cụ cục bộ

Không có trong commit của #147, đặt ở `tooling/`:

- `docker-shim.cjs`: preload chỉ bỏ tham số `--host=unix:///var/run/docker.sock`, vì socket này không có trên Windows. Không đổi spec.
- `run.mjs`: nạp cấu hình từ `.env.demo`, không in credential, chạy lần lượt `two-store`, `tv4-lifecycle`, `negative`.
- `order-history.cjs`: script tạm cho ca 10, vì repo chưa có spec. Yếu hơn 9 ca còn lại.

`TV3_TV4_CONTRACT_REFERENCE` đặt theo README acceptance (`#135 3ec0b340`), TV4 chưa xác nhận giá trị này cho lượt chạy. `customer@hk5.local` dùng chung cho cả ba bộ, chạy lần lượt, không đồng thời.

## Lỗi lúc khởi động và dọn dẹp

- `order-service`, `inventory-service`, `cart-service`: 0 dòng `ERROR` hoặc `Exception` trong 15 phút quanh lượt chạy. Không quét service khác.
- Gateway: 45 dòng `ERROR`, `Connection refused` tới service chưa sẵn sàng, từ 14:32:29 UTC đến 14:33:19 UTC. Test bắt đầu sau đó. Log container không còn sau teardown, số liệu lấy từ lệnh lúc chạy.
- Teardown: `docker compose --env-file .env.demo -f docker/compose.demo.yml down -v` với `COMPOSE_PROJECT_NAME=tv3-live-147`. Hai volume và network của project đã xóa, sau đó 0 container chạy, 0 volume `tv3-live-147`, `hk5-demo_pgdemo` và `hk5-demo_productimages` còn nguyên.

## Hash và bảo mật

`SHA256SUMS` có full SHA256 từng file, trừ chính nó và README. File giữ nguyên byte, gồm đường dẫn máy cục bộ trong `negative/playwright.json` và khoảng trắng cuối dòng trong log.

Đã quét toàn bộ file so với 23 giá trị bí mật trong `.env.demo` và mẫu JWT, Bearer: 0 khớp. Dữ liệu là tài khoản `@hk5.local` và sản phẩm giả.

Hai ca UNKNOWN không chạy: không có fault injection, cần TV3, TV4, TV5 duyệt và receipt thật.
