# TV3 — Checkout COD (component độc lập)

**Trạng thái:** SOURCE/UI CANDIDATE, CHƯA LIVE. Không có route/HTTP adapter/Inventory integration.

- `CheckoutCodView.tsx`: nhóm giỏ theo Store, thể hiện tổng tiền mặt hàng từ snapshot, COD-only, loading/empty/unauthorized/unavailable/UNKNOWN, thiếu adapter thì nút bị khóa.
- `checkoutModel.ts`: tạo expectedItems + expectedRevision từ snapshot đã kiểm chứng và **giữ nguyên Idempotency-Key do bên ngoài truyền**, không sinh key tự động. `createCheckoutOnce` chỉ cho phép một lệnh gửi; timeout/exception/mất kết quả → UNKNOWN, chặn retry tự động và không phát tín hiệu thành công.
- UI chỉ hiển thị orderId khi callback từ adapter được tin cậy trả `confirmed` + UUID hợp lệ. Callback hiện là proposal consumer interface, chưa phải V1/V2 HTTP contract được nhóm duyệt. Để bắt đầu attempt khác sau reconciliation, parent phải cấp state/instance đã xác minh mới; không reset UNKNOWN một cách tự động.
- Không tự đặt currency/fee/ship/delivery, không khẳng định COD_PAID, không tự quyết định trạng thái SellerOrder/C06.

**Isolated local preview:** Sau khi cài package theo `frontend/package-lock.json`, chạy tại thư mục `frontend`:

```powershell
npm install --no-package-lock
npm run dev
```

Mở `http://localhost:5173/src/features/checkout/preview/index.html` trên trình duyệt Windows (hoặc port thực tế Vite thông báo). Preview hiển thị cả Cart và COD Checkout với fixture hai Store; chỉnh số lượng/xóa **chỉ đổi state bộ nhớ**; nút Checkout không có adapter và bị vô hiệu hóa. Preview không sử dụng router/Auth/API của TV1, không gửi request hay ghi database.

**Chưa thực hiện:** Order History UI, router/shared API client (TV1), server-origin authentication/JWT, real Store/SKU/Inventory/COD; UI/API thật không được đánh dấu DONE.

**Kiểm thử đề nghị (trong `frontend/`, Node 22+, sau `npm install --no-package-lock`):**

```powershell
node --experimental-strip-types --test src/features/checkout/checkoutModel.test.mjs
node --test src/features/checkout/CheckoutCodView.test.mjs
npm run typecheck
npm run build
```

**Đã kiểm tra thực tế trên source blob GitHub tương ứng:** pure `checkoutModel.test.mjs` **5/5 PASS**, Node 22.16.0, source `checkoutModel.ts` blob SHA `4b4ed9dace331560f19c150cb31f168035060f1f`, test blob SHA `e5160841536655205f73430a1517a40a79a17508`. TS model/JSX isolated type/syntax check có React declaration tối giản PASS; **full npm typecheck/build/SSR/browser tests NOT_RUN** trong môi trường AI. 

**UAT**: xem `TV3_TWO_STORE_UAT.md`. Mọi test giao diện bằng fixture ≠ live Checkout E2E.

**Lý do lệnh cài package:** repository hiện chưa có `frontend/package-lock.json`; không dùng `npm ci` vì nó yêu cầu lockfile. `npm install --no-package-lock` chỉ chuẩn bị dependency trên worktree kiểm thử, không tạo lockfile mới trong shared frontend.
