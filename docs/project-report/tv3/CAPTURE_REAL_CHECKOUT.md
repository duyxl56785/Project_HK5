# TV3 — Thu ảnh Checkout COD thật để đưa vào báo cáo HK5

**Trạng thái: PREPARED, NOT_RUN / NOT_CAPTURED.** Tài liệu này là quy trình chuẩn bị an toàn; chưa có ảnh mới được tạo. Liên quan [PR #188](https://github.com/duyxl56785/Project_HK5/pull/188), [Issue #160](https://github.com/duyxl56785/Project_HK5/issues/160) và [TV5 UAT #18](https://github.com/duyxl56785/Project_HK5/issues/18).

## 1. Điểm đặt ảnh trong báo cáo nhóm

Bản Word `Bao_cao_Smart_ECommerce.docx` (nội dung tổng hợp đến 25/10/2026) có **mục 6 — KẾT QUẢ DEMO WEB/APP**, trang 10: **Hình 6.4. Giỏ hàng và checkout gồm sản phẩm từ hai Store**. Hiện là `[CHÈN ẢNH DEMO THỰC TẾ]`. Báo cáo chỉ có thể dùng ảnh runtime thật, không dùng preview fixture hoặc ảnh giỏ trống.

## 2. Nguồn test đã có (đối chiếu tại main@78de5c08205b41986a5204a8150a9ef810507847)

- `frontend/src/features/checkout/acceptance/two-store.live.spec.ts`: test real browser, Auth→Gateway→Cart→Catalog→Inventory→Order; xác minh 2 Store, đồng nhất Product/quantity, response Order ID, root/SellerOrder, stock delta và replay cùng key.
- `frontend/src/features/checkout/acceptance/live.config.ts`: **trace, screenshot, video đều OFF theo thiết kế** để tránh ghi JWT. Không tự bật trace/video hay public toàn bộ Playwright output.
- `frontend/src/features/checkout/acceptance/README.md`: yêu cầu `TV3_E2E_BASE_URL`, tài khoản Customer thử nghiệm, **hai Product thật thuộc hai Store**, PostgreSQL Docker read-only oracle và xác minh exact stack.
- Hosted [PR #187](https://github.com/duyxl56785/Project_HK5/pull/187) có Playwright 20/20 PASS tại branch SHA `bc824863`, **không xuất bản screenshot artifact** trong GitHub workflow runs đã rà. Đó không phải run final UAT trên main.

## 3. Gate trước khi chụp

1. TV2 xác nhận stack **disposable, dành riêng** để làm bằng chứng, đúng `main` SHA, các Store/Product và role của tài khoản giả. TV5 xác nhận giới hạn dữ liệu và ảnh có thể công bố. Không dùng `hk5-demo`, shared volumes, dữ liệu thật hoặc stack của người khác khi chưa cho phép.
2. Không bật `INVENTORY_ORDER_PAIR_ENABLED`, `HK5_FINANCE_POLICY_APPROVED` hoặc UNKNOWN fixture chỉ để chụp ảnh. Nếu stack đang OFF, đừng khẳng định checkout end-to-end thành công; xin owner cấu hình/cho phép case cụ thể hoặc chỉ dùng bằng chứng lịch sử có provenance phù hợp.
3. Ghi nguồn code: `git rev-parse HEAD`, `git status --porcelain`, Compose project ID, stack host/ports (chỉ host loopback), ngày UTC và tool version. Không ghi token/password/headers Authorization vào log hoặc README.
4. Chụp ảnh browser tại các điểm **sau khi assert đúng trạng thái**, không crop từ preview UI giả, không sửa DOM để thay đổi trạng thái. Chỉ ảnh đã xóa PII mới được commit.

## 4. Điểm chụp chính xác trong test two-store

Dùng **Codex Chat** đọc source test và tạo bản patch tạm **trong worktree riêng** (không thay đổi test gốc/PR khác). Trong `two-store.live.spec.ts`:

- **Ảnh A**: ngay sau `expect(cart.groups).toHaveLength(2)` và `await page.goto('/cart')`, thêm assert 2 Store thật trên DOM rồi `page.screenshot` → `checkout-01-cart-two-stores.png`. Màn hình phải hiện hai Store; ảnh giỏ trống không hợp lệ.
- **Ảnh B**: sau khi đi tới Checkout và điền thông tin giao hàng giả, **trước khi** bấm `Xác nhận đặt hàng COD`, chụp `checkout-02-cod-confirmation.png`. Dùng Playwright `mask` với các input Họ tên/SĐT/Địa chỉ để không ghi PII.
- **Ảnh C — Order detail:** sau response `POST /api/orders` là 200, `await expect(page).toHaveURL(/\/orders\/UUID/)` và `assertPlaced(page, token, id, lines)`, chụp `checkout-03-order-success.png`. Route thực tế `/orders/:id` hiển thị **chi tiết đơn**, không phải trang danh sách Order History; nếu UI chỉ hiển thị summary, ghi đúng phạm vi ảnh. Bằng chứng 2 SellerOrders đến từ `assertPlaced`/read-only SQL chỉ được mô tả riêng, không được suy ra là hiển thị trên ảnh.
- **Ảnh C2 — Order History:** sau khi đã xác nhận Order detail, điều hướng thực sự tới `/orders` (route danh sách dành cho Customer), chờ API/dữ liệu hiển thị, đối chiếu root Order ID vừa tạo với hàng tương ứng rồi chụp `checkout-03b-order-history.png` với PII masking. Nếu không thấy ID hoặc route lỗi thì ghi `NOT_CAPTURED`; không dùng ảnh `/orders/:id` để nhận là đã chụp Order History. Chỉ sử dụng tài khoản/UUID thử nghiệm được cho phép công bố.
- **Evidence D**: đối chiếu bằng các query chỉ đọc (Order root, SellerOrders, Item snapshots, Inventory operation/stock trước-sau) và viết bảng **đã redacted** với SHA/receipts; không lấy ảnh terminal có chứa secrets. Nếu chưa có quyền DB read-only, ghi `NOT_CAPTURED`.

Dùng `page.screenshot({ path: ..., fullPage: true, mask: [...] })` **chỉ trong private output directory ngoài repository**. Tuyệt đối không bật `trace: 'on'`/video trên test Auth đang giữ token. Giữ các assertion sẵn có và không bỏ kiểm tra stock/replay để đổi lấy ảnh đẹp.

## 5. Cách chạy khi stack đã được chủ dự án cho phép

**Codex Terminal Linux trong worktree riêng** (không thực thi nếu thiếu stack/credential đã được cấp):

~~~bash
git rev-parse HEAD
git status --porcelain
cd frontend
npx playwright test --config src/features/checkout/acceptance/live.config.ts --project two-store --list
# Khi TV2/TV5 đã duyệt exact stack/source/fixture và chuẩn bị env PRIVATELY:
npx playwright test --config src/features/checkout/acceptance/live.config.ts --project two-store
~~~

Trước đó Codex phải đối chiếu `frontend/src/features/checkout/acceptance/README.md` để cấu hình đầy đủ `TV3_E2E_*` và screenshot path trong copy test riêng; **không commit .env hoặc secret**. Bản test này **tạo Order và thay đổi tồn kho trên stack thử nghiệm**, không chỉ đọc: phải có quyền chạy rõ ràng. Không chạy nếu không có đúng 2 Product thuộc 2 Store.

Chỉ sau khi hoàn tất: lưu ảnh đã redacted và `SHA256SUMS` cùng `manifest.md` có test-result/exit code, commit hash và hình nào là bằng chứng của điều gì; TV5 duyệt. Sau đó TV3 mới được bổ sung ảnh hợp lệ vào [Hình 6.4] của bản báo cáo chính thức.

## 6. Trạng thái hiện tại

| Ảnh / chứng cứ | Trạng thái |
| --- | --- |
| `checkout-01-cart-two-stores.png` | NOT_CAPTURED |
| `checkout-02-cod-confirmation.png` | NOT_CAPTURED |
| `checkout-03-order-success.png` (Order detail) | NOT_CAPTURED |
| `checkout-03b-order-history.png` (Order History list) | NOT_CAPTURED |
| `checkout-04-db-receipt.png` | NOT_CAPTURED |
| Bản báo cáo Codex gốc 351 dòng đã được đối chiếu toàn văn | NOT_VERIFIED |
| PR #188 được ghép vào báo cáo nhóm | PENDING TV2 |

**Cảnh báo nguồn ảnh:** File cũ tên `cart-hai-store-quantity-remove-va-jwt-het-han-passed.png` thuộc suite 06/11 **thực tế hiển thị giỏ trống**. Không dùng file đó thay `checkout-01`. `reports/evidence/tv5-ui-20261028/cod-desktop.png` là TV5 DEMO DATA preview, không phải Checkout thật.
