# Sửa password NUL và kiểm chứng Auth tại PR #80

Refs #1, #80. Ngày kiểm chứng: 25/10/2026, UTC/Asia/Bangkok.

## Phạm vi và Git

- Checkout ban đầu: `main@d9cc3d2d9e57e3d93d63956377f3189d648670c3`, không có tracked diff; có `.serena/` và các thư mục `backend/*/bin/` untracked, giữ nguyên.
- `git fetch origin` thành công. PR #80 vẫn OPEN, head `feat/tv1-auth-jwt-current-authority-foundation-20261023@dfdc8143c4a86cdfce8eefc9a98e1b07126da41d`, base `main`. Kiểm tra lại trước commit: head không đổi.
- Worktree riêng: `.worktrees/tv1-auth-password-nul`, branch `fix/tv1-auth-password-nul-20261025`, tạo từ đúng head PR. Không sửa branch của thành viên.
- Đã đọc AGENTS, CONTRIBUTING, OWNERSHIP, Issue #1 và comments, Contract v1.0/OD-01–OD-09, ADR-0001, bootstrap gates, review/comments PR #80. TV2 REQUEST_CHANGES ngày 25/10/2026 là finding NUL hiện tại, khác finding lazy-role đã xử lý.
- GitHub trả `draft=false` trong khi mô tả PR ghi DRAFT/NOT_READY. Không sửa metadata, review hoặc thread trên GitHub.

## Thay đổi và nguyên nhân

- `AuthPasswordHasher.validPassword`: từ chối U+0000 trong validation chung của registration/login. Registration ném lỗi validation; login trả false. Cập nhật Javadoc để nêu ngoại lệ NUL.
- `AuthPasswordHasherTest`: regression trailing-NUL; registration/login với NUL đầu/giữa/cuối; mật khẩu Unicode đủ 128 UTF-16 code units và quá giới hạn; malformed surrogate; salt/digest bị sửa nhưng vẫn có encoding hợp lệ. Giữ các test đúng/sai password, Unicode dài, định dạng/work factor và salt ngẫu nhiên.
- JDK 21.0.11 / SunJCE probe độc lập, cùng salt fixture, PBKDF2WithHmacSHA256/600000/256: hai đầu vào ngắn khác nhau ở NUL cuối cho cùng digest. Phù hợp với bước zero-padding key của [HMAC, RFC 2104 §2](https://www.rfc-editor.org/rfc/rfc2104.html#section-2). Sau sửa, probe xác nhận positive control và từ chối NUL tại cả hai entry point.
- Không đổi PBKDF2 version/algorithm, encoding, iterations, salt/key size, migration, API hay cấu hình runtime. Tìm toàn bộ callers: primitive chỉ được gọi trong test, chưa có register/login HTTP wiring.

## Kiểm thử thực thi

Môi trường: Windows, Java `21.0.11+9-LTS-211`, Gradle wrapper 8.10, timezone UTC; Docker client/server 29.7.2, API server 1.55; JVM Testcontainers dùng `-Dapi.version=1.44`. Image `postgres:16`, database dùng một lần. Docker Desktop ban đầu chưa chạy, đã khởi động để chạy test; không xóa volume hoặc truy cập DB production.

| Lượt | PASS | FAIL | SKIP | Exit |
|---|---:|---:|---:|---:|
| RED, regression mới trên production source gốc | 0 | 1 | 0 | 1 |
| GREEN, cùng regression sau sửa validation | 1 | 0 | 0 | 0 |
| GREEN cuối, security unit: Bootstrap 5 + Hasher 11 + CurrentAuthority 5 + JwtCodec 4 | 25 | 0 | 0 | 0 |
| AuthPersistenceTest, PostgreSQL thật | 28 | 0 | 0 | 0 |
| CurrentAccountAuthorityPostgresRegressionTest, PostgreSQL thật, task riêng | 5 | 0 | 0 | 0 |

Ba dòng cuối thuộc cùng invocation thành công, tổng **58 PASS/0 FAIL/0 SKIP**, 1m25s. Hai lượt regression trước không cộng vào tổng này. RED là assertion `expected false, actual true` tại `verifyForLogin(password + NUL, encoded)`, không phải compilation/network failure.

Lệnh RED và GREEN đầu, chạy từ worktree root với JAVA_HOME trỏ JDK21:

```powershell
./backend/gradlew.bat -p backend :auth-service:test --tests '*AuthPasswordHasherTest.trailingNulCannotAuthenticateAsTheAcceptedPassword' --rerun-tasks --no-daemon --console=plain
```

Lệnh GREEN cuối; init script dùng đường dẫn tuyệt đối để tránh resolve sai dưới `backend`:

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.11'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
$qaSources=(Resolve-Path tests/security/auth-current-authority).Path
$qaInit=(Resolve-Path tests/security/auth-current-authority.init.gradle).Path
./backend/gradlew.bat -p backend --init-script $qaInit "-Ptv5AuthAuthoritySources=$qaSources" :auth-service:test --tests '*security.*' --tests '*AuthPersistenceTest' :auth-service:tv5CurrentAuthorityRegression --rerun-tasks --no-daemon --console=plain
```

Probe: `java --class-path backend/auth-service/build/classes/java/main .local-evidence/auth-nul/PasswordNulProbe.java`, JDK21, exit0; chỉ in version/provider và booleans. Lượt sandbox đầu tiên bị chặn download trước test; lưu riêng `red.log`, không tính là RED nghiệp vụ. `git diff --check` exit0. Review source/callers/diff: không phát hiện finding mới trong bản sửa; không thay transaction/concurrency/authorization.

## Evidence local và nhận diện source

Raw logs/XML giữ nguyên trong `.local-evidence/auth-nul/`, **không commit/publish** vì output integration có thể chứa credentials fixture. Bản sao XML GREEN ở `green-standard-xml/` và `green-authority-xml/`. SHA256:

| File | SHA256 |
|---|---|
| `AuthPasswordHasher.java` sau sửa | `e9364a3ae03b0ae2e5837b0f81dbc163cae068a1f8ecad1414b45fd1ee728af9` |
| `AuthPasswordHasherTest.java` cuối | `9493c0a1d28d74813a085810294b45151e04fa481ef9e87d1e349774fc933449` |
| `red-test.log` | `a31553d9f246b0053f7ff6fcb245e49a27c701f17f7afc5735a35fb6a5f2f866` |
| `red-password.xml` | `54da0f1c0d9e3bc8d7e39c553375f00cf443c83dd81a1c7e6c73ebf2de8a4e35` |
| `green-regression.log` | `45043dfa6b716111f70d532c6a949a86456115f59c021474f9d080c27370e3f6` |
| `green-regression.xml` | `a8a634ffebd70227c15c6e0f6012044b3d55093a728997a62181a8eed3051840` |
| `green-security-postgres.log` | `f62ed364524129391eb3f6b75ce7c1ae7aa7adf9ad112cae482244cd60f15b8d` |
| `jdk-probe.log` | `0cbd5cb4b88b6ea1b088a8895cad5380a1aba5deb1fdd01667183b4eaf2334fd` |

QA Java/init Git blobs so với HEAD gốc không đổi: `04b1d7ec9b0ff63dbf93eb34b591633e71bb70b7` / `27de70cf0a4f13f53780ff2b04501816f398848f`. Không sửa assertions hoặc chỉ lấy kết quả CI cũ thay cho PostgreSQL run này.

## Skills và gate bàn giao

- Đã kiểm tra catalog skills phiên, local roots `.codex/skills`, `.agents/skills` và repository; không tìm thấy SKILL.md riêng trong repository, không cài thêm.
- Đã áp dụng: `ponytail` (diff tối thiểu), `codex-evidence-loop` (bảo vệ checkout/evidence), `tdd` (public seams đã được người dùng chỉ định, RED → GREEN), `spring-boot-backend` (giữ Gradle/conventions hiện có), `postgresql-backend` (disposable DB/giữ schema), `postman:api-engineer` (contract và ranh giới verification), `ponytail-review` (source/caller/security review). Đã đọc `ai-memory-retrieval`; không có memory tools trong phiên nên retrieval/ghi quyết định NOT_RUN.
- **Giai đoạn 1: hoàn thành kỹ thuật, WAITING_REVIEW TV2**; không tự resolve finding hoặc coi test local là approval.
- **Giai đoạn 2: BLOCKED / chưa triển khai.** V1 migration cố ý không định nghĩa active-status vocabulary; `AuthBootstrapConfiguration` yêu cầu `HK5_AUTH_ACTIVE_STATUS` được owner xác nhận. `ACTIVE` trong AuthPersistenceTest và `QA_EXPLICIT_ACTIVE` trong QA là fixture, không phải business decision/production inventory. Issue #1 và checkpoint TV1/TV2 mới nhất vẫn yêu cầu quyết định trạng thái và review runtime/bootstrap trước HTTP wiring. Chưa có căn cứ để tự chọn canonical status hoặc cấp quyền dựa trên nó.
- TV1 cần xác nhận trạng thái account đang dùng và chính sách cho register/login/me; TV2 cần review commit NUL cùng bootstrap/password policy/work-factor trước wiring. Sau đó tiếp tục transactional CUSTOMER-only register, login generic errors/throttling/Origin-CSRF, verified/current-state me và servlet security tests theo contract.
- Bản ghi v1 từng tạo từ password chứa NUL, nếu tồn tại, không thể được nhận diện chỉ từ digest; không thực hiện inventory production hoặc migration ngầm. TV1 cần quyết định reset/reauth nếu có dữ liệu lịch sử như vậy. Không ảnh hưởng format của password không chứa NUL.
- MockMvc/Auth HTTP, Gateway, refresh/logout, Seller role-sync, CI mới và live E2E: **NOT_RUN**, vì chưa có source triển khai và chưa đủ runtime gate. Auth/Gateway vẫn NOT_READY; flags Finance/Inventory không đổi.
- Bàn giao bằng commit local trên branch riêng, không push/comment/request review/merge/deploy. Bước tiếp theo: TV2 kiểm tra patch local; chỉ tích hợp vào PR #80 khi được yêu cầu, rồi chạy lại ở integration HEAD.
