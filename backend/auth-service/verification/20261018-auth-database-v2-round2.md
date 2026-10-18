# Auth Database V2 — bàn giao vòng 2

**WAITING_REVIEW — TV1 Lê Xuân Bảo.** Refs #1. Ngày 2026-10-18, Asia/Bangkok; JUnit dùng UTC.
Nhánh `feat/tv1-auth-database`, HEAD `03bd846`. Không stage, commit, push hay cập nhật GitHub.
Issue #1 và quy định ownership đã đọc; đã fetch origin. Remote chưa có PR cho nhánh này; không có bản review vòng 2 riêng trên nhánh/Issue, nên phạm vi đối chiếu là sáu yêu cầu người dùng và báo cáo review trước đó.

## Thay đổi

- Kiểm tra migration Auth local và lịch sử mọi ref đã fetch: V2 chưa bị dùng. Chuyển V2 từ `db/proposed` sang `db/migration`; chỉ thay dòng chú thích đầu, giữ nội dung CHECK và contract Java 21. Không sửa V1.
- `AuthPersistenceTest` và `EmailNormalizationTest` dùng `db/migration`, bỏ location proposed. Test thực sự migrate V1 → V2, giữ ID/email của 5 account ASCII/Unicode và kiểm lịch sử chỉ có V1/V2; validate checksum, migrate lại, JPA validate và restart.
- Hai schema fixture riêng kiểm tra V2 từ chối một email noncanonical đơn lẻ và một cặp trùng canonical. SQLSTATE `23514` là kết quả mong đợi; DDL rollback, ID/email và Flyway V1 giữ nguyên.
- `DatabaseReadinessTest` kỳ vọng V2, nhưng không bật trong phiên vì test này có thể migrate DB hiện hữu.
- Thêm `EmailInventoryTest`: không khởi động Spring/Flyway, chỉ SELECT trong transaction `REPEATABLE READ READ ONLY`, dùng chính `Account.normalizeEmail`, rồi rollback. Báo số lượng/ID, không xuất email hoặc canonical key.
- Thêm chế độ giữ Testcontainers, kiểm tra cả Ryuk và cấu hình reuse trước khi khởi động; cập nhật README. Không đổi entity, query refresh, contract, API hoặc module khác.

## Môi trường và lệnh

Windows PowerShell; Java 21.0.11; Gradle Wrapper 8.10; Docker Engine 29.7.2.
Testcontainers dùng image `postgres:16`, server thực tế **PostgreSQL 16.15**, `lc_ctype=en_US.utf8`.
Database test mới, credential ngẫu nhiên, không dùng volume dev. Chỉ restart PostgreSQL của container test.

Từ `backend`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC -Dapi.version=1.44'
$env:TV1_RETAIN_TEST_DATABASE = 'true'
$env:TESTCONTAINERS_RYUK_DISABLED = 'true'
$env:TESTCONTAINERS_REUSE_ENABLE = 'true'
# TV1_DATABASE_TESTS không được bật.
# Nạp AUTH_DB_PASSWORD hiện có từ .env vào process, không in secret.
$env:AUTH_EMAIL_INVENTORY_JDBC_URL = 'jdbc:postgresql://127.0.0.1:5433/auth_db'
./gradlew.bat :auth-service:build --console=plain
```

## Kết quả thực thi

| Kiểm tra | Kết quả | Evidence / diễn giải |
|---|---|---|
| Auth build cuối | PASS, exit 0 | `BUILD SUCCESSFUL in 25s` |
| PostgreSQL integration | PASS: 28, FAIL: 0 | V1 → V2, Unicode, constraints, transaction/concurrency, restart |
| Java normalization unit | PASS: 2, FAIL: 0 | Tập ký tự Java 21; PostgreSQL đối chiếu 1.433 scalar thay đổi khi lowercase |
| Kiểm kê DB dev | PASS: 1, FAIL: 0 | Đọc bằng `auth_app`, transaction read-only, không Flyway |
| Dependency readiness có sẵn | PASS: 1, FAIL: 0 | Không suy thành JWT/API production PASS |
| Migrate/readiness DB hiện hữu | NOT_RUN: 1 | Chủ động không bật `TV1_DATABASE_TESTS` |
| Flyway validate | PASS | Gọi `validateWithResult()` thật trên DB cô lập ở V1, V2 và sau restart; không repair/clean/baseline |
| Refresh-token concurrency regression | PASS | consume tự khóa family; chờ logout; giữ lock tới commit; hai rotation/reuse; rollback consume + child |
| `git diff --check` và whitespace file mới | PASS | [static-checks.log](../build/review-evidence/20261018-round2/static-checks.log) |
| V1 SHA-256 | PASS | `C083D9BD4BFA5E7086CE5C358708E65D5F8307E4BA9634DF10F67E671B7F46CD` trước/sau |

**Tổng lượt cuối: 32 PASS, 0 FAIL, 1 NOT_RUN.** Các ca cố ý làm migration thất bại vẫn là test PASS vì đã xác nhận rollback và bảo toàn dữ liệu.

Lượt đầu: 30 PASS, 2 FAIL, 1 NOT_RUN, exit 1. Hai lỗi test mới là runtime role không có USAGE ở schema fixture; đã sửa inventory fixture dùng migration role trong transaction read-only. Giữ nguyên [log lượt đầu](../build/review-evidence/20261018-round2/attempt1-build.log) và XML tại `attempt1-xml/`; không cộng kết quả lượt đầu vào lượt cuối.

## Kiểm kê database hiện hữu

Nguồn: container dev `8409de695604` (`docker-postgres-1`), PostgreSQL 16.15, `auth_db`, schema `public`.
Container vốn dừng đã được khởi động để đọc; không chạy profile migration trên database này.

| Chỉ số | Kết quả |
|---|---:|
| Account đọc được | 0 |
| Email không canonical | 0 |
| Email Unicode không canonical | 0 |
| Nhóm email trùng canonical | 0 |

DB dev trước/sau vẫn chỉ có Flyway **V1**, checksum **-529061705**, success=true, 0 account.
[Snapshot trước](../build/review-evidence/20261018-round2/existing-db-before.log) và [sau](../build/review-evidence/20261018-round2/existing-db-after.log) có SHA-256 giống nhau.
Inventory fixture riêng đã phát hiện đúng noncanonical đơn lẻ (1 account, 0 nhóm trùng) và duplicate canonical (2 account, 1 nhóm); inventory sau rollback migration giống trước. Không dùng số liệu fixture để kết luận dữ liệu thật.

## Giữ dữ liệu và giới hạn

**Sai sót lượt đầu:** tắt Ryuk và bỏ `stop()` vẫn chưa đủ; JVM shutdown hook đã tự dọn container test mới `9630c03ad240...` và volume tạm của nó. Đây là sai lệch so với yêu cầu không xóa database/volume. Không có database/volume hiện hữu nào bị xóa; toàn bộ tên volume có trước test vẫn tồn tại. Đã sửa bằng `withReuse` và kiểm tra reuse được bật; không chạy lệnh xóa database/volume để xử lý lỗi.

Container lượt cuối **`f6089dcc1a9ce81a84f21e89f99dca7c864b193b268cd724bd5865058134bed4`** vẫn chạy sau khi JVM kết thúc, volume **`c5733b72bb0756218c085b759fa8cc331438564eac2781be1c1f2d9a1393d0e6`** được giữ lại. [Evidence giữ container/volume](../build/review-evidence/20261018-round2/container-retention.log). Không tự dọn sau bàn giao.

Không áp V2 lên DB hiện hữu; chưa rehearsal restored backup có dữ liệu thật hoặc đo lock/thời gian quét bảng. DB dev rỗng nên inventory không chứng minh mọi nguồn dữ liệu production an toàn. Inventory nhóm canonical trong bộ nhớ; cần external sort nếu vượt heap. TV1 cần chốt rollout và mọi xử lý conflict; Unicode pin Java 21 phải review trước nâng JDK. Auth API/JWT/Gateway/Frontend/live E2E ngoài phạm vi.

## Artifacts và file thay đổi

- [Build log cuối](../build/review-evidence/20261018-round2/build.log), [exit code](../build/review-evidence/20261018-round2/build-exit.txt).
- [JUnit PostgreSQL](../build/review-evidence/20261018-round2/final-xml/TEST-com.projecthk5.authservice.AuthPersistenceTest.xml), [JUnit inventory](../build/review-evidence/20261018-round2/final-xml/TEST-com.projecthk5.authservice.EmailInventoryTest.xml), [JUnit normalization](../build/review-evidence/20261018-round2/final-xml/TEST-com.projecthk5.authservice.EmailNormalizationTest.xml).
- [JUnit readiness NOT_RUN](../build/review-evidence/20261018-round2/final-xml/TEST-com.projecthk5.authservice.DatabaseReadinessTest.xml), [JUnit dependency](../build/review-evidence/20261018-round2/final-xml/TEST-com.projecthk5.authservice.Hs256DependencyTest.xml).
- [Danh sách thay đổi riêng phiên này](../build/review-evidence/20261018-round2/changed-files.txt), [working-tree status đầy đủ](../build/review-evidence/20261018-round2/after-status.txt), [SHA-256 artifacts](../build/review-evidence/20261018-round2/sha256.txt).

File sửa trong phiên: `README.md`; V2 (chuyển proposed → migration); `AuthPersistenceTest.java`; `DatabaseReadinessTest.java`; `EmailNormalizationTest.java`; thêm `EmailInventoryTest.java` và báo cáo này. `build.gradle`, `application.yml`, persistence sources và báo cáo review trước là công việc đã có, được giữ nguyên. Bản thay đổi untracked, IDE `bin/` và `.serena/` có sẵn ngoài Auth không được stage hay sửa trong phiên.

Evidence nằm trong `build/` được Git ignore; raw XML được copy nguyên byte và kiểm SHA-256 với JUnit output. Không chạy `clean` sau khi tạo evidence. Chờ TV1 kiểm tra tại working tree; không có SHA commit/push hoặc PR mới để bàn giao.
