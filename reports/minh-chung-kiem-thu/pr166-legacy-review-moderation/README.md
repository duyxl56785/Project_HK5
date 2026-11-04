# Minh chứng R06: ẩn review cũ dài hơn 1000 ký tự

Nguồn xuất phát: PR #166 HEAD `a80cbe5714fa152c81f18323912d96d21a72ad31`, base `f0190e54a18b263df5b5cc0e9f7987873d30617e`. Chạy ngày 03/11/2026 trên Windows, Docker Desktop Engine 29.8.1, PostgreSQL Testcontainers 16.6 Alpine với dữ liệu UUID/comment giả. Không sử dụng DB hoặc volume của dự án đang chạy.

## RED trước sửa

Thêm assertion rằng review cũ 1001 ký tự vẫn ẩn được sau V9 mà không đổi comment. Chạy:

```powershell
$env:TESTCONTAINERS_RYUK_DISABLED='true'
.\backend\gradlew.bat -p backend :product-service:integrationTest --tests '*ReviewCommentMigrationPostgresTest' --rerun-tasks --no-daemon --console=plain
```

Exit 1: **1 FAIL** tại `ReviewCommentMigrationPostgresTest.java:66`, PostgreSQL từ chối `UPDATE product_reviews SET hidden = TRUE, hidden_reason = ...` vì `CHECK ... NOT VALID` cũng kiểm mọi UPDATE trên row cũ. [Log RED](red-gradle.log) là output chuyển hướng chưa sửa. JUnit XML RED không được lưu trước khi chạy lại và **không được khai là có**.

## GREEN sau sửa

V9 dùng trigger để kiểm `INSERT` hoặc khi nội dung `comment` thực sự thay đổi. Comment cũ >1000 vẫn được giữ và Admin có thể hide/show; thay comment thành giá trị >1000, tạo review >1000 vẫn bị SQLState `23514`. Chạy:

```powershell
$env:TESTCONTAINERS_RYUK_DISABLED='true'
.\backend\gradlew.bat -p backend :product-service:test --tests '*ReviewCommentLimitTest' :product-service:integrationTest --tests '*ReviewCommentMigrationPostgresTest' --tests '*CatalogSchemaProposalPostgresTest' --tests '*ProductImageMetadataSchemaIntegrationTest' --tests '*ProductMigrationPostgresTest' --rerun-tasks --no-daemon --console=plain
```

Exit 0, **67 PASS / 0 FAIL**: 2 unit và 65 PostgreSQL. [Log GREEN](green-gradle.log) và 5 JUnit XML giữ byte gốc, có SHA256 trong [SHA256.txt](SHA256.txt). Đây là regression scoped của migration/Review, chưa phải E2E HTTP, Admin revoke hoặc UAT cuối.

Các file log/XML có `.gitattributes -text` để Git không chuyển dòng; kiểm hash theo Git blob, không theo file đã bị checkout chuyển CRLF. Không có credential hay dữ liệu người dùng thật trong gói này.
