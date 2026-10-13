# Frontend scaffold

React 19 + TypeScript + Vite + Tailwind CSS v4. **Hiện chưa có giao diện nghiệp vụ, auth, API client hoặc routing production; chỉ có landing và placeholder routes.**

Chạy từ thư mục frontend:
```bash
npm install
npm run typecheck
npm run build
npm run dev
```

Chưa có package-lock.json; TV1 nên chạy `npm install`, commit lockfile trong PR được review để toàn nhóm dùng `npm ci`. Sau khi import UI thực từ V2 cần đối chiếu router, DTO, query và style trước khi thay thế placeholder.

Ownership: `src/app`, `src/shared` TV1; các feature theo bảng docs/OWNERSHIP.md.
