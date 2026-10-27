# TV1 Gateway → Auth HTTP → PostgreSQL16 — 27/10/2026

Refs #1, #80, #102. **FIXED_CANDIDATE / WAITING_INDEPENDENT_REVIEW**; #102 giữ Draft, base `feat/tv1-auth-runtime-p0-20261026@e45cede29435bbce553cb244cb956493cb98c26b`. HEAD đầu phiên `a4d63600d1c72712391566ac04d794149eab0c59`; SHA mới và CI sau push được xác nhận trên PR, không tái sử dụng CI cũ.

## Thay đổi và phạm vi

- Test khởi động Gateway Spring Boot/Netty trên loopback port ngẫu nhiên, Auth bootJar/Tomcat trong JVM riêng và Testcontainers `postgres:16` disposable. Không mock bean nghiệp vụ, JWT, repository hoặc HTTP transport. Auth giữ dependency riêng; không trộn servlet vào classpath Gateway.
- Runtime dùng `auth_app`; migration dùng `auth_migrator`, tách mật khẩu ngẫu nhiên. Test fail nếu thiếu Docker, không skip/reuse DB. Cờ Auth/Gateway chỉ bật trong fixture; production/default vẫn đóng. Secure cookie được kiểm thuộc tính rồi gửi thủ công trên HTTP loopback, không phải bằng chứng browser cookie policy.
- Sửa refresh account không hợp lệ: exception 401 riêng không đánh dấu rollback-only transaction cha. Exception dependency 503 vẫn rollback. Không thay DTO, migration hoặc role policy.
- Gateway phản hồi JSON lỗi JWT/CSRF theo contract, unknown/internal path 404, method ngoài allowlist 405 + Allow. CORS đăng ký chính xác năm method/path Auth, không wildcard preflight.
- Giữ outbound sanitizer đã được review: test transport kiểm đủ chín spoofed headers, giá trị trùng/khác hoa thường, method/path/body, Bearer, Origin, CSRF, Cookie và Content-Type. E2E Auth/DB kiểm tác động thực của các request này.
- Gradle đưa Auth bootJar vào input của Gateway test; không cache/bỏ qua chạy HTTP/DB. CI chạy thêm bộ current-authority hiện có, bắt buộc E2E và current-authority không fail/skip và in count/SHA256 XML. Không sửa source TV2–TV5.

## Kiểm tra thực tế

Môi trường local: Windows, Java 21.0.11, Gradle 8.10, Docker Engine 29.7.2/API 1.44, PostgreSQL16, UTC. Một invocation cuối **exit 0 / BUILD SUCCESSFUL / 4m19s / 14 tasks executed**:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JAVA_TOOL_OPTIONS='-Dapi.version=1.44 -Duser.timezone=UTC'
# Từ backend/; không cấu hình DB bên ngoài cho hai ca optional bên dưới.
.\gradlew.bat --init-script ..\tests\security\auth-current-authority.init.gradle `
  "-Ptv5AuthAuthoritySources=$((Resolve-Path ..\tests\security\auth-current-authority).Path)" `
  :auth-service:test :auth-service:tv5CurrentAuthorityRegression :api-gateway:test `
  :auth-service:bootJar :api-gateway:bootJar --rerun-tasks --no-daemon --console=plain
```

| Suite trong invocation cuối | PASS | FAIL/error | SKIP / NOT_RUN |
|---|---:|---:|---:|
| Auth test, gồm 28 persistence PostgreSQL | 110 | 0 | 2 |
| Gateway unit + embedded HTTP forwarding | 22 | 0 | 0 |
| Gateway → Auth bootJar → PostgreSQL E2E | 10 | 0 | 0 |
| Current-authority PostgreSQL regression hiện có | 5 | 0 | 0 |
| Tổng cùng invocation | 147 | 0 | 2 |

Hai ca optional local chưa chạy: `DatabaseReadinessTest` thiếu `TV1_DATABASE_TESTS=true`/DB provision riêng; `EmailInventoryTest` thiếu `AUTH_EMAIL_INVENTORY_JDBC_URL`. Không suy inventory dữ liệu thật từ DB fixture. CI readiness có DB riêng và bật readiness; số liệu CI phải đọc từ run mới.

- [x] Register 201, CUSTOMER-only, password hash, provisioning task atomic; duplicate và concurrent registration 201/409; provisioning insert lỗi rollback account.
- [x] Login, JWT thực từ Auth (issuer/audience/kid/sub/signature/TTL); Gateway và Auth trực tiếp từ chối sai signature/issuer/audience/kid, expired/future token; nhận previous key đã cấu hình.
- [x] `/me` kiểm signed ∩ current roles; CUSTOMER token cũ không tự nhận ADMIN/SELLER sau DB promotion. Refresh lấy roles mới; revoke, disjoint grants 403, inactive/missing account 401. Role mutation chỉ là fixture, không phải Seller role-sync.
- [x] Cookie Secure/HttpOnly/SameSite=Lax/Path, TTL không vượt 7 ngày, không Domain, không refresh token trong JSON. Rotation giữ absolute expiry; used-token replay commit revoke family, clear cookie, không ảnh hưởng family thiết bị khác. Unknown/expired refresh trả 401; logout idempotent và revoke theo token lịch sử; access JWT còn hạn sau logout đúng OD-06.
- [x] Hai HTTP refresh đồng thời: đúng một 200/một 401, một child row, child bị revoke sau replay. Không grace window/retry mù.
- [x] Origin/CSRF deny matrix trên cả Gateway và servlet HTTP thật, không mutation/consume cookie; CORS/preflight allowlist; throttle 429 + Retry-After qua HTTP, thay X-Forwarded-For không vượt quota single-instance.
- [x] Cắt kết nối thật và NOLOGIN cho `auth_app` trong container test: `/me` và cả bốn POST trả 503; không token/cookie mới, không account/family/token mới, logout thất bại không revoke. Chờ health pool phục hồi rồi refresh cookie cũ thành công.
- [x] Hai bootJar build, PowerShell CI gate syntax, source diff/whitespace kiểm tra; runtime deployment không thực hiện.

## RED và tính toàn vẹn evidence

- E2E đầu: 6 PASS / 2 FAIL. Refresh inactive gây `UnexpectedRollbackException`, error dispatch bị security che thành 403 thay vì 401. Ca còn lại là fixture yêu cầu phục hồi quá sớm sau NOLOGIN; đã thêm chờ health, không retry mutation.
- Gateway contract regression trước sửa: 11 PASS / 7 FAIL; sau sửa status/error/CORS: 18 PASS. Bộ cuối bên trên chạy lại toàn bộ.
- Bản thử `noRollbackFor=DecisionFailure` quá rộng: current-authority 4 PASS / 1 FAIL ở unavailable account query. Đã thu hẹp về exception account 401; cả 5 ca nguyên bản PASS trong invocation cuối, không sửa test TV5.
- Lỗi hạ tầng đầu tiên do Docker client API cũ; chạy API1.44 với dependency hiện có. Một expectation default-off ban đầu cố định 403 đã được sửa thành từ chối 401/403; không coi đó là lỗi production.

Raw log/XML byte-preserved nằm local, Git-ignored tại `backend/api-gateway/build/verification/auth-e2e/`. Không publish raw vì có thể chứa thông tin fixture. Không sửa log để qua diff-check. SHA256:

| Artifact tương đối với thư mục evidence | SHA256 |
|---|---|
| `red/TEST-com.projecthk5.gateway.GatewayAuthPostgresE2ETest.xml` | `eaf766ddc3a22684df00494519d0265a9567955f7202bf7edd583f9e84c64671` |
| `gateway-contract-red/TEST-com.projecthk5.gateway.GatewayEmbeddedNettyForwardingTest.xml` | `b9a7675c6de9282f84ca53798700ead9d2099d2362e7a3bcb401e7102c641e09` |
| `authority-red/TEST-com.projecthk5.tv5authqa.CurrentAccountAuthorityPostgresRegressionTest.xml` | `5b0310c8e9a4e23fb4b3fb2ab42f99c1565764e26f716779f3e49945715f77fe` |
| `final/auth-gateway-e2e-verified.log` | `03b9e1407fafa50fa8fde71c987ef51d856ebfb1295bdbbb7652dc4aa789b767` |
| `final/gateway/TEST-com.projecthk5.gateway.GatewayAuthPostgresE2ETest.xml` | `37e4e7a6c4108e810b75a11d9a6cd0d328500ce0dafa9a6b885acced8e83c0bc` |
| `final/gateway/TEST-com.projecthk5.gateway.GatewayEmbeddedNettyForwardingTest.xml` | `5b68e88c95b5c38035e3ef03ac7bd4ff1cdf894c2777719f4a3074f9bb63bd82` |
| `final/authority/TEST-com.projecthk5.tv5authqa.CurrentAccountAuthorityPostgresRegressionTest.xml` | `c6f65bb0bb54adbd53c4072403c5a384ad9ee20052efc0c475ceea1e4bfdf62d` |
| `final/source-manifest.json` (6 file source/build/CI) | `b8f572585593de11b9a2b2cf979dbaac766a58c51e81925b69a99481aec87797` |

## BLOCKED và review còn lại

**Seller → Auth role-sync: BLOCKED**, chưa có wire contract hai owner duyệt. Đề xuất để TV1/TV2 review: command từ durable Seller outbox sau commit, gồm `eventId`, `sellerId`, `accountId` từ mapping tin cậy, `sellerStatus`, `sourceVersion` tăng đơn điệu theo Seller và `occurredAt`; chỉ grant/revoke SELLER, không ADMIN. Auth lưu idempotency/version cùng role update; duplicate không tạo side effect, stale không ghi đè trạng thái mới, gap/reconciliation và ACK/retry cần hai owner chốt. Per-pair credential tách JWT key; method/path/operation, rotation và delegated/system policy **TBD**, không tạo endpoint tạm.

Các service-pair OD-09 chưa được hai phía duyệt tiếp tục BLOCKED theo matrix baseline. Không dùng fixture DB role mutation làm evidence cho Seller/Store authority, suspension/freshness hay service-pair runtime.

**NOT_RUN / release gates:** browser/FE, User provisioning worker/reconciliation, real upstream network timeout/failure mapping, distributed limiter/proxy trust, production secrets/status inventory và các miền TV2–TV5. Cần independent review mới cho transaction boundary và Gateway status/CORS/error changes; ba finding TV2 FIXED_VERIFIED tại `a4d6360` không được coi là approval cho delta này. Không merge main, deploy hoặc bật runtime production.
