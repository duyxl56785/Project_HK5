# Quản lý Product — TV2

`ProductManagementPage` và `ProductForm` là component React tiếng Việt, không tự đăng ký route hoặc gọi API. Tái sử dụng Category tree, Store display type và bộ kiểm tra version của Seller. Chỉ dùng các trường đã có trong Product domain; có cả trạng thái `INACTIVE`.

- Truyền `state` từ host đã xác thực: loading/error/unauthorized/unavailable hoặc ready với Store, danh sách Product **của Store đó** và cây Category đầy đủ. Mặc định là unavailable; dữ liệu sai quan hệ/định dạng bị từ chối toàn bộ. Đây là kiểm tra tính nhất quán, không cấp quyền.
- Types là projection/commands nội bộ UI, **chưa phải HTTP contract được duyệt**. Price/currency/version là chuỗi chính xác; currency phải chọn tường minh. SKU không trim/normalize và không có trong update command. Không truyền role, ownerReference, status hoặc reviewReason từ form.
- `actions.create/update/submitForReview` chỉ được gắn bởi adapter tới protected API thật. Thiếu handler thì nút ghi tương ứng bị khóa. Không có dữ liệu mẫu, fetch, token hoặc fake authority trong feature entry.
- Adapter chỉ trả `confirmed` khi server xác nhận bền vững; không ánh xạ timeout, HTTP 202/pending hay kết quả không rõ thành confirmed. Sau confirmed/conflict/error/unavailable, host phải đọc lại dữ liệu; UI không tự thêm Product, đổi trạng thái/version hoặc gửi lặp. Callback `onReload(storeId)` yêu cầu host thực hiện việc này và truyền mảng dữ liệu mới.
- Khi phiên đăng nhập/quyền đổi, host phải chuyển state khỏi ready ngay và bỏ dữ liệu cũ. Feature ẩn dữ liệu khi unauthorized, bỏ qua phản hồi muộn sau khi đổi Store/unmount và chặn edit/submit với version đã cũ. Backend vẫn phải xác minh JWT, ownership, state và optimistic locking trong transaction.
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
