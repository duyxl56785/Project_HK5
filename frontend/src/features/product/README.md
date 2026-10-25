# Quản lý Product — TV2

`ProductManagementPage` và `ProductForm` là component React tiếng Việt, không tự đăng ký route hoặc gọi API. Tái sử dụng Category tree, Store display type và bộ kiểm tra version của Seller. Chỉ dùng các trường đã có trong Product domain; có cả trạng thái `INACTIVE`.

- Truyền `state` từ host đã xác thực: loading/error/unauthorized/unavailable hoặc ready với Store, danh sách Product **của Store đó** và cây Category đầy đủ. Mặc định là unavailable; dữ liệu sai quan hệ/định dạng bị từ chối toàn bộ. Đây là kiểm tra tính nhất quán, không cấp quyền.
- Types là projection/commands nội bộ UI, **chưa phải HTTP contract được duyệt**. Price/currency/version là chuỗi chính xác; currency phải chọn tường minh. SKU không trim/normalize và không có trong update command. Không truyền role, ownerReference, status hoặc reviewReason từ form.
- `actions.create/update/submitForReview` chỉ được gắn bởi adapter tới protected API thật. Thiếu handler thì nút ghi tương ứng bị khóa. Không có dữ liệu mẫu, fetch, token hoặc fake authority trong feature entry.
- Adapter chỉ trả `confirmed` khi server xác nhận bền vững; không ánh xạ timeout, HTTP 202/pending hay kết quả không rõ thành confirmed. Sau confirmed/conflict/error/unavailable, thao tác ghi bị khóa cho tới khi một authoritative reload hợp lệ hoàn tất; UI không tự thêm Product, đổi trạng thái/version hoặc gửi lặp. Tạo array mới, thay version hoặc gọi callback không chứng minh reload đã thành công.
- Host truyền `sessionKey` không bí mật, chỉ nhận diện lifetime của phiên/access context; đổi key khi đổi account, phiên hoặc quyền. Thiếu key thì không ghi. Key/receipt là correlation của UI, **không phải** JWT, role hoặc bằng chứng ownership và không cấp quyền server. Khi mất quyền, host phải rời ready ngay, bỏ dữ liệu cũ; không đưa snapshot phiên trước vào phiên mới.
- `onReload(request)` trả `Promise<ProductReloadResult>`. Request do component tạo, bất biến, gồm `requestId` mới + `storeId` + `sessionKey`. Chỉ trusted host adapter sau khi nhận/kiểm tra response server thật, đúng Store và phiên vẫn hiện hành, mới trả `{ status: 'confirmed', receipt: { ...request }, snapshot: { store, products, categories } }`; đồng thời publish snapshot đó qua ready state. Đây là contract callback nội bộ UI, không phải wire contract mới.
- Chỉ mở khóa khi receipt khớp **yêu cầu mới nhất** và snapshot qua privacy/Store/domain validation, trùng nội dung normalized đang render. Thứ tự publish-ready và resolve Promise đều được hỗ trợ. Reload trả cùng Product version vẫn hợp lệ nếu server xác nhận đó là dữ liệu hiện tại; form giữ expectedVersion và backend kiểm lại. Version mới khiến form cũ tiếp tục bị khóa tới khi đóng/mở lại.
- Callback pending/throw/timeout/error/unavailable/conflict, thiếu/sai receipt hoặc snapshot không hợp lệ đều không mở khóa. Map HTTP401/403 thành unauthorized: ẩn dữ liệu ngay. Receipt cũ, request bị thay thế, Store/session đổi hoặc unmount đều vô hiệu; callback muộn không thay đổi context mới. Dữ liệu sai trong cùng Store/session không remount làm mất write lock. Không dùng cached/optimistic response, array identity hoặc client tự khai để tạo receipt. Backend vẫn phải kiểm JWT/current role/ownership/Store/Seller/state/version trong transaction cho mỗi request.
- Barrier cùng Store/session được giữ tại page qua loading/error/unauthorized → ready; dữ liệu ready khôi phục không tự mở khóa. Unmount workspace hủy request đang chờ, cần một request mới. Sau khi rời/mount lại toàn bộ page hoặc đổi scope, host phải nạp lại dữ liệu/quyền thật, không khôi phục snapshot cũ làm initial-ready; UI không thay được server idempotency/transaction khi kết quả ghi chưa rõ.
- Bộ lọc tên/SKU/trạng thái chỉ áp dụng **danh sách đã tải**, không phải Catalog search phía server hoặc pagination tổng. ACTIVE không được coi là bằng chứng public eligibility.

## Kiểm thử và preview độc lập

Từ `frontend`:

```powershell
npm run typecheck
node --test --test-concurrency=1 src/features/product/productManagementModel.test.mjs src/features/product/ProductManagementPage.test.mjs
# DOM/browser runner dùng happy-dom và Playwright từ tooling riêng, không thêm dependency production.
# TV2_DOM_MODULE: absolute path tới happy-dom/lib/index.js
# TV2_BROWSER_MODULE: absolute path tới playwright/index.mjs; Chrome đã cài trên máy.
node --test src/features/product/ProductManagement.dom.mjs
node --test src/features/product/ProductManagement.browser.mjs
npm run build
npx vite build --config src/features/product/vite.product.config.ts
```

Chạy Vite rồi mở `/src/features/product/preview/index.html` để kiểm tra responsive. Thư mục preview và `productTestFixtures.mjs` chỉ phục vụ test, không được export từ `index.ts`, import vào app hoặc dùng làm production fallback. Preview không gắn mutation handler và luôn ghi rõ dữ liệu mẫu. Không tính những bài này là protected API/PostgreSQL/E2E PASS.

## Phụ thuộc để nối thật

TV2 cần protected Store-scoped Product list/create/edit/submit, response price/currency/version chính xác, Category read và error mapping nhất quán (validation, quyền, conflict, unavailable). TV1 cần verified current account/roles, JWT/Gateway, session invalidation và Router/API client integration. Chưa tự chọn endpoint/path, phân trang hay wire DTO. Không thay đổi backend, PR #89/#90/#91/#94 hoặc các flags.
