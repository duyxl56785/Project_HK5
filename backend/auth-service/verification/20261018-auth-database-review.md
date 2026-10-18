# Auth Database — bản sửa chờ TV1 review

Trạng thái: **WAITING_REVIEW**. Refs #1. Phiên 2026-10-18 (Asia/Bangkok), log runtime dùng UTC.
Nhánh `feat/tv1-auth-database`, HEAD `03bd846`; không stage/commit/push, không cập nhật GitHub.
Issue #1 đã đọc lại; remote không có PR cho nhánh này. Finding đối chiếu theo yêu cầu Technical Lead do người dùng cung cấp.
Giữ nguyên công việc chưa commit có sẵn. Chỉ sửa/bổ sung Auth persistence, test, migration nháp và tài liệu liên quan.

## Nguyên nhân và phạm vi có thể trùng account

Contract v1.0 §5.1 yêu cầu Java `strip().toLowerCase(Locale.ROOT)` ở create/login/lookup, DB bảo vệ unique kể cả concurrent insert. Không có quyết định ASCII-only, NFC, IDNA, accent folding hay Unicode case folding.

Đã tái hiện trên **PostgreSQL 16.15**, `lc_ctype=en_US.utf8`, Java **21.0.11**, UTC. PostgreSQL `lower(... COLLATE "C")` chỉ đổi ASCII; V1 kiểm tra giá trị gốc bằng hàm này, sau đó UNIQUE so sánh giá trị lưu. Vì vậy hai chuỗi khác nhau có cùng Java-normalized key vẫn có thể cùng tồn tại nếu một writer bỏ qua Java normalization.

| Input trước `@example.invalid` | Java normalize | PostgreSQL C lower | V1 nhận raw input? |
|---|---|---|---|
| `ascii` | `ascii` | `ascii` | Có; hai chuẩn giống nhau |
| `É` (U+00C9) | `é` (U+00E9) | Giữ `É` | Có |
| `İ` (U+0130) | `i` + U+0307 | Giữ `İ` | Có |
| `ΟΣ` | `ος` (final sigma U+03C2) | Giữ `ΟΣ` | Có |
| U+10400 (Deseret, ngoài BMP) | U+10428 | Giữ U+10400 | Có |
| U+001C + `x` | `x` | Giữ separator | Có |
| U+2003 + `x` | `x` | Giữ EM SPACE | Không, POSIX space check của V1 chặn |

Probe V1 thực sự insert cả raw và Java-normalized email thành công cho từng cặp được V1 nhận, sau đó rollback fixture. Các bài test còn xác nhận ASCII uppercase bị SQL check từ chối, trong khi Java create/lookup chuẩn hóa ASCII case và whitespace như cũ.

Các đường `Account` + `AccountPersistence.create` hiện tại đã normalize trước persist; concurrent Java inserts cùng canonical key có một người thắng. Lỗ hổng nằm ở invariant DB: import/SQL writer/phiên bản writer khác có thể tạo account thứ hai không qua normalizer. Lookup Java khi đó không tìm thấy raw account, hoặc luôn chọn canonical account khác. Không kết luận database local hiện có duplicate vì chưa inventory DB đó.

Nguồn đối chiếu: [PostgreSQL 16 collations](https://www.postgresql.org/docs/16/collation.html), [POSIX character classes và locale](https://www.postgresql.org/docs/16/functions-matching.html). Bằng chứng quyết định ở đây là PostgreSQL thực trong JUnit XML, không suy kết quả từ tài liệu.

## Bản sửa đề xuất

- Giữ nguyên `Account.normalizeEmail`, entity, AccountRepository, AccountPersistence và Auth Contract v1.0.
- Không sửa V1. SHA-256 trước/sau: `C083D9BD4BFA5E7086CE5C358708E65D5F8307E4BA9634DF10F67E671B7F46CD`.
- [V2 nháp](../src/main/resources/db/proposed/V2__enforce_java21_email_canonical_form.sql) thêm CHECK chỉ nhận email đã là fixed point của Java 21 lowercase và không còn ký tự `strip()` ở hai đầu. CHECK dùng tập code point tường minh, tránh phụ thuộc libc/ICU lower. Giữ UNIQUE hiện có, không trigger/auto-rewrite/backfill/merge account.
- Tập lowercase-changing có 1.433 Unicode scalar, pin Java 21 / Unicode 15.0. Unit test sinh lại toàn bộ tập từ Java; PostgreSQL test đối chiếu regex với mọi scalar hợp lệ trong U+0001–U+10FFFF. NUL/surrogate không phải dữ liệu text PostgreSQL hợp lệ. Kiểm đủ các ký tự Java whitespace ở cả hai đầu.
- Java xử lý lowercase có ngữ cảnh/mở rộng trước khi ghi; DB chỉ kiểm kết quả canonical, không mô phỏng thuật toán lowercase. Unicode lowercase vẫn được nhận. `ß`/`ss`, `é`/`e` + U+0301, `ος`/`οσ` vẫn là các email khác nhau đúng Java hiện tại. NBSP không bị tự strip thêm.
- `RefreshTokenRepository.consume` giữ `Propagation.MANDATORY`, dùng native SQL với `MATERIALIZED` CTE lấy `FOR UPDATE` trên family trước conditional token update. Caller không thể bỏ qua family lock chỉ bằng cách quên gọi `lockById`/`lockByTokenHash`. Lock sống tới commit/rollback của caller, bao trùm consume + child insert. `flushAutomatically` giữ các thay đổi JPA trước bulk update; entity token đã load có thể stale như trước.
- Caller vẫn cần khóa family **trước khi đọc/quyết định** rotation/reuse/logout và mọi revoke phải dùng cùng lock. Bản sửa này không triển khai Auth API hoặc transaction nghiệp vụ/API tương lai. Repository/entity `save` hoặc SQL tùy ý vẫn có thể bypass workflow; đây không phải cơ chế phân quyền thao tác ở DB.

## Kiểm thử và evidence

Môi trường PowerShell, thư mục `backend`, `JAVA_HOME=C:\Program Files\Java\jdk-21.0.11`, `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`. Không bật `TV1_DATABASE_TESTS`; Testcontainers tạo PostgreSQL 16 mới với credential ngẫu nhiên và database riêng. Các container/database của người dùng không được migrate hoặc xóa.

| Lượt chạy | Lệnh Gradle | PASS | FAIL | NOT_RUN | Exit |
|---|---|---:|---:|---:|---:|
| RED trước sửa | `:auth-service:test --tests '*AuthPersistenceTest.rawUnicodeAliasCannotBypassDatabaseNormalization' --tests '*AuthPersistenceTest.consumeAcquiresFamilyLockEvenWhenCallerDoesNot' --console=plain` | 0 | 2 | 0 | 1 |
| Focused đầu | `:auth-service:test --tests '*EmailNormalizationTest' --tests '*AuthPersistenceTest' --console=plain` | 2 | 1 setup | Các phương thức PostgreSQL chưa chạy do setup lỗi | 1 |
| Focused rerun | Như trên | 25 | 2 | 0 | 1 |
| **Final** | **`./gradlew.bat :auth-service:build --console=plain`** | **30** | **0** | **1** | **0** |

Các lỗi focused đã sửa: đọc locale từ `pg_database.datctype` thay vì GUC không tồn tại; fixture whitespace dùng email riêng và validation của schema legacy dùng target V1 khi V2 còn pending. Không sửa log/XML các lượt này. Các số trên tách riêng từng lượt, không cộng với XML lịch sử trước phiên.

Final có **27 PostgreSQL PASS**, **2 normalization unit PASS**, **1 dependency readiness PASS**. `DatabaseReadinessTest` **NOT_RUN (1)** do chủ động không bật test migrate DB local. Dependency readiness có sẵn không chứng minh JWT/API production và không được sửa trong phiên này.

PostgreSQL PASS bao gồm duplicate Unicode qua Java và SQL canonical, 5 biến thể concurrent insert chỉ một winner và không task/role mồ côi; canonical Unicode không bị đổi policy; token hash/FK/absolute-expiry constraints; consume không transaction bị từ chối; consume tự khóa family; chờ logout commit rồi trả 0; giữ lock tới commit (SQLSTATE `55P03`); consume + child rollback; hai rotation/reuse serialization; logout/family isolation; restart app và PostgreSQL giữ dữ liệu.

Flyway PASS: V1 → V2 trên dữ liệu canonical giữ account ID; V1 checksum hợp lệ; migrate lại không có pending; JPA `ddl-auto=validate`; kiểm lại sau restart. Schema test riêng có hai account cùng Java-normalized key làm V2 thất bại SQLSTATE `23514`; Flyway rollback DDL, giữ nguyên hai ID/email và current V1, không repair/clean/delete.

- [Final build log](../build/review-evidence/20261018/build.log)
- [Final PostgreSQL JUnit XML, kèm stdout probe](../build/review-evidence/20261018/final-xml/TEST-com.projecthk5.authservice.AuthPersistenceTest.xml)
- [Final normalization JUnit XML](../build/review-evidence/20261018/final-xml/TEST-com.projecthk5.authservice.EmailNormalizationTest.xml)
- [Final readiness NOT_RUN XML](../build/review-evidence/20261018/final-xml/TEST-com.projecthk5.authservice.DatabaseReadinessTest.xml)
- [Final dependency XML](../build/review-evidence/20261018/final-xml/TEST-com.projecthk5.authservice.Hs256DependencyTest.xml)
- [RED log](../build/review-evidence/20261018/red.log), [RED XML](../build/review-evidence/20261018/TEST-com.projecthk5.authservice.AuthPersistenceTest.xml)
- [Focused log](../build/review-evidence/20261018/focused.log), [Focused rerun log](../build/review-evidence/20261018/focused-rerun.log); XML tương ứng ở `focused-xml/`, `focused-rerun-xml/`.
- [Static check log](../build/review-evidence/20261018/static-checks.log), [SHA-256 manifest](../build/review-evidence/20261018/sha256.txt).

Evidence nằm trong `build/` được Git ignore; chưa stage/commit. Sao lưu evidence trước khi chạy `clean`. Raw XML/log không được chỉnh byte để sửa whitespace; manifest xác nhận bản sao XML final giống output JUnit gốc.

## TV1 cần duyệt trước rollout

1. Chấp nhận cách bảo vệ canonical Unicode bằng CHECK pin Java 21, giữ nguyên normalize contract. Không chọn ASCII-only/citext/ICU lower/NFC một cách ngầm định. Nâng JDK/Unicode cần review tập ký tự và migration mới.
2. Inventory email trên bản backup bằng chính Java normalizer, nhóm theo canonical key, lập phương án cho từng conflict/noncanonical account. Không tự gộp, xóa, đổi email hay ID. Bản nháp hiện dừng với mọi noncanonical row, kể cả chưa tạo duplicate.
3. V2 đang ở `db/proposed`, **không tự chạy bởi migration profile hiện tại**. Sau phê duyệt: kiểm tra version kế tiếp chưa bị dùng; thử restored backup; kiểm tra thời gian quét bảng và lock DDL; owner chốt cửa sổ rollout. Sau đó mới chuyển sang `db/migration`, cập nhật expected version trong `DatabaseReadinessTest`, migrate bằng role riêng và chạy validation/runtime smoke. `clean-disabled=true`, không baseline/repair V1.
4. Review native locking query và bảo đảm Auth use case tương lai bao một transaction cho lock/read/consume/child hoặc revoke, response sau commit. Owner chịu trách nhiệm transaction API; chưa có API để kiểm thử ở phạm vi này.

Chưa áp V2, chưa inventory hoặc chạy Flyway validation trên database local hiện có. Chưa đo thời gian DDL với kích thước dữ liệu thật; CHECK `ALTER TABLE` quét dữ liệu và cần khóa. Auth API/JWT/Gateway/Frontend và E2E ngoài phạm vi. Chờ review tiếp theo.
