# PR #171 — kiểm tra độc lập retention sau bản sửa default-off

Nguồn: PR #171 tại HEAD `60b3defd865d873df91433a9557e783a05aa876b`, base `main@f0190e54a18b263df5b5cc0e9f7987873d30617e`. Chạy ngày 04/11/2026 trên Windows, Java 21, Docker Engine 29.8.1, PostgreSQL 16 Testcontainers tạm. Không sử dụng volume hay dữ liệu ứng dụng.

Lệnh tại thư mục `backend` của detached worktree đúng HEAD: `TESTCONTAINERS_RYUK_DISABLED=true; .\gradlew.bat :order-service:postgresIntegrationTest --tests '*CheckoutDraftRetentionPostgresIT' --console=plain` (biến môi trường đặt theo cú pháp PowerShell). Exit code **0**; Gradle `ORDER_PG16_TEST_SUMMARY tests=6 passed=6 failed=0 skipped=0`, `BUILD SUCCESSFUL in 43s`. JUnit XML gốc: **6 PASS / 0 FAIL / 0 ERROR / 0 SKIP**.

Đối chiếu source: `app.checkout.draft-purge-enabled` lấy `HK5_CHECKOUT_DRAFT_PURGE_ENABLED:false`; hàm `purgeExpired()` trả về không xóa khi cờ tắt. Test `scheduledPurgeIsOffByDefaultAndOptInStillOnlyRemovesExpiredSuccess` xác minh mặc định giữ draft quá hạn và opt-in chỉ xóa `SUCCEEDED` quá 7 ngày. Đây là PostgreSQL regression của retention, **không** chứng minh production DB grants/audit/backup, fixture UNKNOWN hay UAT cuối. PR #173 là patch phụ cùng nguyên nhân; không cộng test giữa hai HEAD.

File `TEST-CheckoutDraftRetentionPostgresIT.xml` là raw JUnit output giữ nguyên byte; `.gitattributes` tắt chuyển đổi EOL cho file đó. SHA256 ở `SHA256.txt`. Không có raw console log được lưu cho lượt này; số liệu Gradle trên là bản ghi kết quả lệnh, không trình bày như raw log.
