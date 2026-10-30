# Frontend

Giao diện đầy đủ cho Khách hàng, Người bán và Quản trị viên: React 19, TypeScript, Vite, Tailwind CSS v4, React Router, TanStack Query.

Chạy từ thư mục frontend (cần stack backend đang chạy, xem `docs/RUN_LOCAL.md`):

```bash
npm ci
npm run dev
```

Dev server và `vite preview` chuyển `/api` tới gateway `http://localhost:8080`, nên trình duyệt luôn gọi cùng origin `http://localhost:5173`. Cookie refresh và quy tắc CSRF nhờ vậy hoạt động không cần cấu hình thêm.

Kiểm tra:

```bash
npm run typecheck
npm test
npm run build
```

Cấu trúc:

- `src/app`: router và layout theo vai trò.
- `src/shared`: API client (tự refresh access token một lần), phiên đăng nhập, `Guard` phân quyền, UI dùng chung.
- `src/features/<miền>`: trang của từng miền. Access token chỉ nằm trong bộ nhớ, không lưu vào localStorage.

`Guard` chỉ để điều hướng; quyền thật do backend kiểm tra.

Ownership: `src/app`, `src/shared` TV1; các feature theo bảng docs/OWNERSHIP.md.
