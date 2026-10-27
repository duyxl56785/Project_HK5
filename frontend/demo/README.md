# Nếp — TV2 demo UI

Demo-only UI/UX, not production Auth/API integration. Dữ liệu mẫu và bộ chọn vai trò chỉ được import từ entry `/demo/`. Không có JWT, API backend, database hay yêu cầu mạng nghiệp vụ.

## Chạy trên Windows

Từ thư mục `frontend` của branch demo:

```powershell
if (!(Test-Path node_modules)) { npm.cmd ci }
npm.cmd run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Mở **http://127.0.0.1:5174/demo/**. Entry production `/` giữ nguyên. Dữ liệu chỉ ở bộ nhớ tab; chuyển trang/quay lại giữ thay đổi, tải lại trình duyệt hoặc Reset sẽ trở về seed. Không lưu token hoặc mật khẩu vào localStorage.

## Kịch bản trình diễn

1. Chọn **Người bán → Seller A**, vào **Sản phẩm → Thêm sản phẩm**.
2. Nhập tên, SKU mới duy nhất, danh mục, giá nguyên `250000` và chọn rõ `VND`; lưu bản nháp.
3. Vào **Ảnh sản phẩm**, chọn bản nháp; thêm tối đa 5 ảnh JPEG/PNG/WebP, mỗi ảnh ≤2 MiB. Đây là preview local, không upload server.
4. Quay lại **Sản phẩm**, tìm tên/SKU rồi **Gửi duyệt → Xác nhận**.
5. Chọn **Quản trị viên → Kiểm duyệt sản phẩm**, xem và duyệt sản phẩm vừa tạo.
6. Chọn **Khách hàng**, tìm và xem sản phẩm. Chỉ ACTIVE + Seller APPROVED + Store OPEN + Category hợp lệ được hiển thị.
7. Quay lại Seller để xem trạng thái. Đóng Store hoặc suspend Seller trong demo sẽ gỡ các Product tương ứng khỏi Catalog.
8. Chọn **Người bán mới** để thử đăng ký → Admin reject → resubmit giữ ID → approve → tạo Store. Admin có thể suspend/reinstate Seller.
9. Admin quản lý danh mục cha/con; không chọn chính nó/hậu duệ làm cha, không xóa danh mục còn con hoặc được Product sử dụng.
10. **Reset demo → Khôi phục demo** xóa thay đổi local và thu hồi Object URL. Không xóa file, database hoặc Docker data.

Menu trạng thái UI trình diễn loading/empty/error/unauthorized/unavailable riêng, không giả lập phản hồi API. Trên mobile, dùng nút mở menu quản lý.

## Build và test

```powershell
npm.cmd run typecheck
npm.cmd run build
node node_modules/vite/bin/vite.js build --config demo/vite.config.ts
```

Production ở `dist/`; bản demo độc lập ở `dist/tv2-demo/`. Không dùng output demo làm ứng dụng production.

Các file `.test.mjs`/`.dom.mjs` dùng Node test runner/Vite giống các feature hiện có. HappyDOM và Playwright là công cụ kiểm thử ngoài ứng dụng; có thể trỏ tới bản đã cài bằng `TV2_DOM_MODULE` và `TV2_BROWSER_MODULE` (đường dẫn module tuyệt đối). Không thêm dependency runtime cho demo.

```powershell
$tests = Get-ChildItem src/features/catalog,src/features/seller,src/features/product,src/demo -Recurse -File |
  Where-Object Name -Match '\.(test|dom)\.mjs$' | ForEach-Object FullName
node --test --test-concurrency=1 $tests
node --test --test-concurrency=1 src/features/catalog/CategoryBrowser.browser.mjs src/features/seller/SellerStoreOverview.browser.mjs src/features/product/ProductManagement.browser.mjs src/demo/Demo.browser.mjs
```

`TV2_DEMO_EVIDENCE` tùy chọn ghi screenshots demo; Chrome phải được cài cho browser tests. Kết quả browser là local-demo workflow, không phải E2E API/JWT/PostgreSQL/Checkout thật.

## Ranh giới tích hợp

Tái sử dụng Category Browser, Seller/Store Overview và Product UI #95. Hai tùy chọn presentation của Product UI giữ nguyên hành vi mặc định và write-safety/reload; demo dùng callback state local, không gắn authority fixture vào production. Không tự merge #95/#105 hoặc sửa Router/Auth/API client chung.

Mô tả Product và ảnh minh họa là dữ liệu trình diễn bổ sung, không tự định nghĩa DTO HTTP. Ảnh chọn từ File API được kiểm MIME/đuôi/signature/decode; Object URL được thu hồi khi loại bỏ/reset/tab unmount. Không hỗ trợ sắp xếp ảnh vì contract hiện tại chưa có thao tác đó.

Seller/Store/Product protected API, JWT/current roles/ownership, image storage/upload, Catalog authority và Cart/Checkout vẫn phải được tích hợp và kiểm thử riêng. Không đánh dấu F03–F08/P0/API/E2E production hoàn thành từ PR demo.
