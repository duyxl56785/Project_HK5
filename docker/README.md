# Hạ tầng phát triển

`compose.dev.yml` chỉ khởi tạo **PostgreSQL, Redis, Kafka** và không giả định backend/frontend V2 đã chạy. TV1 sẽ đối chiếu hạ tầng dự án hiện hữu trước khi xây dựng stack tích hợp. Mật khẩu ở ví dụ chỉ dùng cho máy local và không được áp dụng production.

```bash
docker compose -f docker/compose.dev.yml up -d
docker compose -f docker/compose.dev.yml ps
docker compose -f docker/compose.dev.yml down
```

Không dùng `down -v` nếu muốn giữ dữ liệu. PostgreSQL khởi tạo một database mặc định; 8 database tách biệt của V2 cần script khởi tạo/migration được TV1 đối chiếu với repository gốc trước khi thêm.
