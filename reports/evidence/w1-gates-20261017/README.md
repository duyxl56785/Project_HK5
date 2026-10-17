# TV5 — raw evidence W1 gates, 17/10/2026

Refs #5. [QA matrix và giới hạn](../../TV5_W1_ACCEPTANCE_2026-10-17.md). Source heads/môi trường ở sources.json; JUnit counts ở suites.csv; SHA256 cho từng raw file ở manifest.sha256. Log/XML giữ nguyên byte; CRLF/trailing spaces của output Java/psql có thể làm diff-check raw báo whitespace, không được sửa để làm sạch log. `.gitattributes` tắt text conversion cho log/XML/txt và metadata JSON/CSV có hash để blob Git khớp byte local.

| Run | Lệnh (tại snapshot tương ứng, `-p backend`) | Exit / kết quả |
|---|---|---|
| tv5-rules | `:product-service:test --tests '*ReviewRules*' :order-service:test --tests '*FinanceRules*'` | 0;63 PASS |
| pr29-postgres | `:seller-service:integrationTest` | 0;21 PASS PostgreSQL16.6-alpine thật |
| pr28-postgres | `:cart-service:postgresIntegrationTest :order-service:postgresIntegrationTest --continue` | 0;3 PASS PostgreSQL16-alpine thật; không đóng3 finding |
| pr27-saigon | `:auth-service:test :user-service:test`, TV1_DATABASE_TESTS=true, JVM Asia/Saigon | 1;1 PASS/2 FAIL,0skip; SQLState22023 ở2 contexts |
| pr27-utc | Cùng source/lệnh/environment DB, chỉ JVM timezone UTC | 0;3 PASS,0fail/skip |

Tất cả Gradle commands thêm `--rerun-tasks --no-daemon --console=plain`, dùng Gradle8.14.3/Java21.0.7. `JAVA_TOOL_OPTIONS=-Dapi.version=1.44 -Duser.timezone=UTC` cho Testcontainers; rules chỉ timezone UTC; readiness lần FAIL/PASS dùng `-Duser.timezone=Asia/Saigon` hoặc UTC riêng tiến trình. Không thay Windows timezone/Docker settings hoặc source.

PR27 dùng compose.dev.yml, provision-tv1.ps1 và test-tv1-databases.ps1 nguyên trạng từ6a7f172, test project mới có tên ở pr27-project-name.txt. Sinh5 secret độc lập32byte CSPRNG trong bộ nhớ; COMPOSE_PROJECT_NAME chỉ trỏ project test mới; POSTGRES_PORT=0, lấy localhost port được Docker cấp để đặt AUTH_DB_PORT/USER_DB_PORT. Không reuse credentials/volume hiện có, không ghi `.env` và không lưu secret vào evidence. Chạy provision/isolation hai lượt, rồi2 lượt Java; schema-after logs cho thấy public0table. Sau đó stop riêng service, giữ volume. Project test này tách biệt với quy trình nâng cấp volume dev đang dùng trong runbook owner.

Resource-preservation.json và inventories trước/sau chứng minh tài nguyên có sẵn không mất. Testcontainers tự quản lý tài nguyên test tạm do chính nó tạo; không xóa container/volume trước test. Không provision vào database của người dùng. Test project mới đã stopped, volume còn nguyên.

PR19/21 blob equality và PR26 row/ID/status checks ở qa-static-checks.log. Đây là kiểm tĩnh, không phải21 runtime PASS. Không chứa raw evidence231PASS của TV2 hoặc probes3FAIL TV2; chúng chỉ được dẫn nguồn trong QA matrix, không giả là lượt TV5 chạy.

Manifest chỉ bao gồm file evidence, sources.json và suites.csv; không tự hash manifest/README/attributes. Hash được kiểm tra lại trên blob Git sau commit. Log FAIL timezone được giữ cùng log UTC PASS. Test readiness/PG không chứng minh API/JWT/recovery/live E2E hoặc Finance policy đã duyệt.
