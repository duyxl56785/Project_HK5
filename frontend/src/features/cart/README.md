# TV3 — Giỏ hàng đa Store (component độc lập)

**Trạng thái:** SOURCE/UI CANDIDATE, chưa gắn route, chưa gọi backend/đăng nhập thật.

- `CartView.tsx` hiển thị snapshot theo Store; callback sửa số lượng/xóa chỉ được bật khi parent cung cấp. Không thực hiện request HTTP, không cấp quyền và không tự coi click là lưu thành công.
- `cartModel.ts` kiểm tra đồng nhất Store/Seller, Product trùng, quantity 1–1000, revision dạng chuỗi số chính xác, snapshot monetary decimal và overflow NUMERIC(19,2). Sử dụng bigint minor-unit để tính *giá trị mặt hàng*; không áp đặt đơn vị tiền, phí sàn, vận chuyển, House Store hay điều kiện M01.
- `CartView` hiển thị trạng thái loading/empty/error/unauthorized/unavailable/claimed và fail-closed khi Cart source mâu thuẫn. Props không chứa bearer, JWT hay dữ liệu riêng tư ngoài phần cần hiển thị.
- `index.ts` cung cấp component + type + pure model cho TV1/TV3 tích hợp sau khi thống nhất DTO, current actor/permissions, revision, API error semantics.

**Điều kiện còn thiếu:** adapter mapper từ backend Java CartView thực tế (bao gồm cách biểu diễn revision/amount), authenticated API client và route thuộc TV1; currency/fee/M01; Inventory/Catalog live gate. Không tự sửa `frontend/src/app` hay `frontend/src/shared`.

**Kiểm thử đề nghị (trong `frontend/`, Node 22+, sau khi `npm ci`):**

```powershell
node --experimental-strip-types --test src/features/cart/cartModel.test.mjs
node --test src/features/cart/CartView.test.mjs
npm run typecheck
npm run build
```

SSR test là component-render fixture, **không phải browser/API/E2E**. Không công bố PASS cho đến khi có log chạy đúng HEAD.
