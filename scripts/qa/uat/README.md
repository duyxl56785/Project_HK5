# TV5 — UAT cô lập hai Store

Refs #5, #18. Đây là **rehearsal local**, không phải kết quả UAT cuối hoặc phê duyệt Finance/COD/Settlement.

## Cách ly và an toàn

- Chỉ chạy cùng `docker/compose.demo.yml` với overlay này và project name bắt đầu `hk5-uat-`.
- Hai volume được đặt tên rõ theo project: `<project>-pgdemo` và `<project>-productimages`; không dùng `pgdev`, `redisdev`, `pgdemo` hay `productimages` của project khác.
- Gateway và frontend chỉ bind `127.0.0.1`; các service/DB/Kafka/Redis không publish host port. Không có cổng nào bind `0.0.0.0` hoặc địa chỉ Internet.
- `gen-env.mjs` được gọi **không có** `--approved-inventory-pair` hay `--approved-finance-policy`. Script xác minh cả hai flag là `false` trước khi start.
- Secret là ngẫu nhiên, nằm trong `.env.demo`/`docker/secrets` bị Git ignore của worktree UAT. Không dùng credential production, không in secret/token.
- `Stop-IsolatedUat.ps1` chỉ chấp nhận tên `hk5-uat-*` và chỉ dọn container/network/two volumes thuộc đúng project. Không dùng prune, reset hay thao tác volume dự án khác.

## Thực thi rehearsal

```powershell
# Từ root của worktree UAT riêng; build là cần thiết nếu JAR chưa có.
.\scripts\qa\uat\Smoke-IsolatedUat.ps1 -Build

# Sau khi lưu evidence cục bộ cần thiết, dọn đúng project được script in ra.
.\scripts\qa\uat\Stop-IsolatedUat.ps1 -ProjectName hk5-uat-tv5-YYYYMMDDHHMMSS
```

Smoke kiểm tra Docker Compose, bootstrap tám database riêng, `flyway_schema_history`, seed tài khoản giả `ADMIN`, `CUSTOMER`, hai Seller/Store và Catalog read. Nó không chạy Checkout vì pair Inventory và Finance policy cố ý OFF.

## Gate trước UAT cuối

Chạy [checklist hai Store](../../../tests/TV5_UAT.md) chỉ khi PR phụ thuộc đã merge vào `main`, CI đúng SHA xanh và owner xác nhận đầy đủ Auth/RBAC, Catalog/Checkout/Inventory/Fulfillment, COD cùng M01 Finance/Settlement. Ghi SHA 40 ký tự, seed version, lệnh, kết quả và evidence vào Issue #18. Trước thời điểm đó, mọi case final là `BLOCKED`/`NOT_RUN`.
