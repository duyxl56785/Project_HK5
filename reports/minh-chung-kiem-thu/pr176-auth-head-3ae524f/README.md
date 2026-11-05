# PR #176 — Auth/current authority regression sau khôi phục Docker

Nguồn chạy: PR #176 exact HEAD `3ae524fb9c885ac6a2f14f7dc7b0d28d0f4c3952`, detached worktree sạch, 04/11/2026. Windows, Java 21, Docker Engine 29.8.1 và Testcontainers tạm; không dùng database hoặc volume chung.

Lần đầu khi Docker Engine không có pipe: lệnh full `:security-common:test :auth-service:test` exit 1, `AuthPersistenceTest` lỗi khởi tạo vì Testcontainers không tìm thấy daemon. Đây là **BLOCKED_ENV** cho lượt đó, không phải lỗi source; các unit liên quan được chạy riêng và 15 PASS. Sau khi mở lại Docker Desktop mà không reset/xóa dữ liệu, đặt `TESTCONTAINERS_RYUK_DISABLED=true` rồi chạy `.\gradlew.bat :security-common:test :auth-service:test --rerun-tasks --no-daemon --console=plain` tại thư mục `backend`: exit **0**, `BUILD SUCCESSFUL in 35s`.

JUnit XML lần chạy sau: `security-common` **8 test / 0 FAIL / 0 SKIP**; `auth-service` **49 test / 0 FAIL / 11 SKIP**. Tổng thực thi **46 PASS / 0 FAIL / 11 SKIP**. Chín XML gốc ở `security/` và `auth/`, SHA256 ở `SHA256.txt`; `.gitattributes` giữ nguyên byte. Không lưu raw console log của lượt này; README chỉ là derived summary. Kết quả không thay bằng chứng HTTP riêng của TV2 ở #178, không chứng minh UAT cuối hay Auth production approval.
