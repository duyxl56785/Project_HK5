# TV2: clone sạch, E2E và R06 trên `main@3b4cf7d`

Source: `main@3b4cf7d6909ed19db209bf0eb3fb63d092352cdb` (clone mới, `git log -1` khớp).

## Môi trường

- Ngày chạy: 04/11/2026, máy Windows 11 của TV2, Docker Desktop (server 29.9.1), JDK 21.0.12, Node 24.21.0, Playwright 1.64.1.
- Clone mới từ GitHub vào `D:\hk5c` (`git -c core.longpaths=true clone`), không copy file nào từ checkout cũ.
- Project Compose riêng `COMPOSE_PROJECT_NAME=hk5-clean`, volume mới, secret mới do `gen-env.mjs` sinh. Không dùng stack/volume `hk5-demo` đang có trên máy.
- Lệnh theo `docs/RUN_LOCAL.md`: `node scripts/demo/up.mjs --approved-inventory-pair --approved-finance-policy` (hai cờ chỉ cho demo local, dữ liệu giả).

## Kết quả

| Bước | Lệnh | Exit | Kết quả | File |
|---|---|---|---|---|
| Dựng stack + seed | `up.mjs` như trên | 0 | `Ready: http://localhost:5173` | `up.log` |
| E2E Playwright | `cd frontend && npm ci && npx playwright install chromium && npm run e2e` | 0 | **PASS 17/17** | `e2e.log` |
| Smoke Seller bị đình chỉ | `node scripts/demo/smoke-suspended-seller.mjs` | 0 | **PASS**, `FAILS=0` | `smoke-suspended-seller.log` |
| Smoke token/quyền/proxy/restart | `node scripts/demo/smoke-review.mjs` | 0 | **PASS**, `FAILS=0` | `smoke-review.log` |
| R06 thu hồi giữa hai lần gọi | `node r06-revoke.mjs` | 0 | Ghi: **PASS**. Đọc: **FAIL** (xem dưới) | `r06-revoke-main.log`, `r06-revoke.mjs` |

## R06 trên main

Cùng một access token còn hạn, gọi trước và sau khi thu hồi:

- Admin: trạng thái `DISABLED` được đặt thẳng trong DB của stack giả, vì Auth không cho tắt ADMIN qua API. Seller: Admin tắt qua `POST /api/admin/accounts/{id}/disable`. Cả hai được bật lại sau khi đo.
- Ghi (`POST /api/categories`, `POST /api/seller/products`) và Auth (`GET /api/admin/accounts`): **401 `AUTH_ACCESS_INVALID`** ngay lần gọi sau. Đăng nhập lại: 401.
- Đọc ở service khác (`GET /api/admin/products`, `GET /api/seller/products`): **vẫn 200**. Đây là P1 đã báo ở #153; bản sửa nằm ở PR #176, chưa vào main.

Script đọc mật khẩu từ `.env.demo` của clone, không in secret. Đã quét các log: không chứa `ADMIN_BOOTSTRAP_PASSWORD`, `SEED_PASSWORD`, `POSTGRES_PASSWORD` hay khóa JWT.

## Giới hạn

- "Máy khác" trong #158 chưa đạt: đây là clone sạch trên cùng máy TV2.
- Không chạy bước `rehearse-v7-upgrade.mjs` của CI.
- Log giữ nguyên byte; SHA256 trong `SHA256SUMS`.

`*.log` là output gốc (CRLF, có khoảng trắng cuối dòng do Docker/script in ra), được đánh dấu `-text` để blob commit giữ nguyên byte; `git diff --check` báo trailing whitespace ở các file này là dự kiến, phần tài liệu đã kiểm riêng.
