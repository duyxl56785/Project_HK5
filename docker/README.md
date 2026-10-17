# Hạ tầng phát triển — TV1 PHASE B

Chạy từ gốc repository bằng Windows PowerShell 5.1+ hoặc PowerShell 7. Chỉ cần PostgreSQL cho readiness; Redis/Kafka giữ nguyên. Giữ Compose project hiện tại (mặc định `docker`), volume `docker_pgdev`, network `docker_default`. Không thêm `-p` hoặc đổi `COMPOSE_PROJECT_NAME` làm mất kết nối tới volume cũ.

## 1. Kiểm kê

```powershell
docker info --format '{{.ServerVersion}}'
docker compose ls
docker ps -a --filter label=com.docker.compose.service=postgres
docker volume ls
docker network ls
```

Nếu Engine chưa sẵn sàng, mở Docker Desktop, đợi Linux Engine rồi kiểm tra lại. Không restart/xóa stack khác.

## 2. Credentials và cổng

`.env.example` chỉ có placeholder rỗng. `.env` được Git ignore; giới hạn quyền đọc file cho tài khoản local. Không in environment, `docker inspect` đầy đủ hoặc `compose config` không có `--quiet` vào log chia sẻ.

**Volume cũ:** copy `.env.example` sang `.env` nếu chưa có; điền mật khẩu quản trị **hiện tại** và bốn mật khẩu độc lập ít nhất 32 ký tự từ secret manager/CSPRNG. Nếu bốn role đã tồn tại, dùng đúng secret hiện tại. Thay `POSTGRES_PASSWORD` không rotate mật khẩu trong volume cũ. Không sinh lại secret khi chạy lại.

**Máy mới, chưa có volume/database:** có thể sinh năm secret ngẫu nhiên, không in ra terminal:

```powershell
if (Test-Path .env) { throw 'Khong ghi de .env hien co' }
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$lines = foreach ($name in @('POSTGRES_PASSWORD','AUTH_DB_PASSWORD','AUTH_MIGRATION_PASSWORD','USER_DB_PASSWORD','USER_MIGRATION_PASSWORD')) {
    $bytes = New-Object byte[] 32
    $rng.GetBytes($bytes)
    "$name=$([Convert]::ToBase64String($bytes))"
}
$rng.Dispose()
[IO.File]::WriteAllLines((Join-Path (Get-Location) '.env'), $lines, (New-Object Text.UTF8Encoding($false)))
```

Compose nội suy `.env`; Java/script còn cần biến trong **tiến trình hiện tại**. Nạp file `NAME=value` bằng cách dưới, không dùng `Invoke-Expression`:

```powershell
foreach ($line in Get-Content -LiteralPath .env) {
    if ($line -match '^(POSTGRES_PASSWORD|AUTH_DB_PASSWORD|AUTH_MIGRATION_PASSWORD|USER_DB_PASSWORD|USER_MIGRATION_PASSWORD|POSTGRES_PORT|AUTH_DB_PORT|USER_DB_PORT)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
```

Nếu `5432` bị chiếm, chọn cổng trống rồi đặt cùng giá trị cho `POSTGRES_PORT`, `AUTH_DB_PORT`, `USER_DB_PORT` trong `.env` và nạp lại. Máy kiểm chứng PHASE B dùng `5433` vì PostgreSQL Windows giữ `5432`; không sửa tiến trình đó. Host binding luôn là `127.0.0.1`.

## 3. Khởi động, provision, kiểm tra

### JVM timezone trước khi chạy Java

Trên một số máy Windows, Java21 lấy mặc định `Asia/Saigon`. Image PostgreSQL16.15 Debian được kiểm tra không có tên này trong `pg_timezone_names`; pgJDBC42.7.4 gửi timezone mặc định trong startup parameters, khiến server trả SQLState22023 trước khi Hibernate đọc metadata. Đây là lỗi tương thích cấu hình môi trường, không phải bằng chứng sai credentials hoặc thiếu Hibernate dialect. `hibernate.jdbc.time_zone` hay connection-init SQL chạy quá muộn để sửa startup bị từ chối.

Sau khi nạp credentials/ports, đặt timezone **cho tiến trình Java đang chạy test/service**, không thay timezone Windows hoặc dữ liệu PostgreSQL. Ví dụ PowerShell từ gốc repository:

```powershell
$tv1PreviousJavaOptions = $env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS = (@($tv1PreviousJavaOptions, '-Duser.timezone=UTC') | Where-Object { $_ }) -join ' '
try {
    $env:TV1_DATABASE_TESTS = 'true'
    ./backend/gradlew.bat -p backend :auth-service:test :user-service:test --rerun-tasks --no-daemon
    # Khi chạy service, dùng cùng cấu hình và credentials runtime:
    # ./backend/gradlew.bat -p backend :auth-service:bootRun
} finally {
    $env:JAVA_TOOL_OPTIONS = $tv1PreviousJavaOptions
}
```

Linux/macOS: `JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Duser.timezone=UTC" ./backend/gradlew -p backend :auth-service:test :user-service:test --rerun-tasks --no-daemon`. Chạy packaged app bằng `java -Duser.timezone=UTC -jar <service.jar>` sau khi nạp environment. Kiểm cả tham số JVM trên command line nếu nó đang ghi đè timezone từ environment.

Có thể chọn một IANA timezone khác **được server hỗ trợ** khi owner có yêu cầu; xác minh bằng `SELECT name FROM pg_timezone_names WHERE name IN ('UTC','Asia/Saigon','Asia/Ho_Chi_Minh');`. Không tự map alias hay đổi business date policy. Lượt diagnostic với `Asia/Saigon` vẫn phải ghi FAIL nếu server từ chối, không sửa/skip test để che lỗi. [Báo cáo kiểm lại TV5](../docs/TV1_TIMEZONE_RECHECK.md) giữ source/version và từng lượt FAIL/PASS riêng.

```powershell
docker compose --env-file .env -f docker/compose.dev.yml config --quiet
docker compose --env-file .env -f docker/compose.dev.yml up -d --no-deps --wait --wait-timeout 60 postgres
docker compose --env-file .env -f docker/compose.dev.yml ps
./docker/provision-tv1.ps1
./docker/test-tv1-databases.ps1
# Chạy lại trên cùng volume/credentials:
./docker/provision-tv1.ps1
./docker/test-tv1-databases.ps1
```

Kiểm tra `$LASTEXITCODE` sau từng lệnh Docker; dừng nếu khác 0. Script PowerShell ném lỗi khi kiểm chứng thất bại. Script kiểm tra container thuộc thư mục Compose repository, healthy và PostgreSQL major 16; preflight cả hai DB/bốn role trước khi ghi. Chỉ nhận lại đối tượng có marker `hk5-tv1-readiness-v1`, đúng owner, không superuser/CREATEDB/CREATEROLE/REPLICATION/BYPASSRLS hoặc role membership. Secret truyền qua environment của `docker exec`, không qua argument/source/log SQL; chỉ đặt lúc tạo role, không đổi khi chạy lại.

| Database | Owner / migration login | Runtime login | Quyền runtime |
|---|---|---|---|
| `auth_db` | `auth_migrator` | `auth_app` | CONNECT database, USAGE public; không CREATE/TEMP/DDL |
| `user_db` | `user_migrator` | `user_app` | CONNECT database, USAGE public; không CREATE/TEMP/DDL |

Database lấy từ `template0`; thu hồi quyền PUBLIC trên hai DB và schema public của chúng. Migration role sở hữu DB/schema, không quản trị cluster. Không cấp DML đại trà cho bảng tương lai: migration tiếp theo GRANT SELECT/INSERT/UPDATE/DELETE đúng bảng cần thiết cho runtime; không cấp quyền trên `flyway_schema_history`. Function mới không mặc định EXECUTE cho PUBLIC.

`postgres/pg_hba.conf` ép bốn role dùng SCRAM, chỉ kết nối đúng DB của mình kể cả Unix socket; chặn `hk5_dev`/`postgres` mà không sửa ACL/data của chúng. TCP login khác cũng cần SCRAM; container-local administration vẫn trust, chỉ dành cho dev. Giới hạn quyền truy cập Docker; đây không phải cấu hình production/TLS.

Script test thực hiện login, password sai, cross-DB denial, runtime DDL denial, migration DDL trong transaction rồi ROLLBACK; không để lại bảng. Healthcheck không thay kiểm thử xác thực/quyền.

Các lệnh Docker/provision không cần JVM timezone; trước `build`, `test` hoặc `bootRun` Java, áp dụng block process-only ở trên. Không đưa credentials hoặc toàn bộ environment vào log chia sẻ.

```powershell
$pg = docker compose -f docker/compose.dev.yml ps -q postgres
docker exec $pg psql -X -U hk5 -d postgres -c 'SELECT datname,pg_get_userbyid(datdba) AS owner,datacl FROM pg_database ORDER BY datname;'
docker exec $pg psql -X -U hk5 -d postgres -c 'SELECT line_number,error FROM pg_hba_file_rules WHERE error IS NOT NULL;'
```

Không phụ thuộc init script: volume mới/cũ dùng cùng provisioning **sau** healthcheck. `/docker-entrypoint-initdb.d` chỉ tự chạy khi data directory mới; không xóa volume để kích hoạt lại.

## 4. Flyway và service startup

Auth/User có JPA, JDBC, `flyway-core`, `flyway-database-postgresql` theo Boot 3.3.5 BOM; không nâng/pin version riêng. Mỗi service có datasource riêng, `ddl-auto=validate`, `open-in-view=false`; Flyway mặc định **disabled** ở PHASE B. Service cần runtime password trong environment.

Profile `migration` chuẩn bị cho PR database tiếp theo: bật Flyway bằng `*_migrator` và `*_MIGRATION_PASSWORD` riêng. Chỉ cấp migration secret cho tiến trình startup/migration được phép DDL; runtime thông thường chỉ cần `*_DB_PASSWORD`. `clean-disabled=true`, `baseline-on-migrate=false`. Không bật profile trong PHASE B.

Convention: mỗi service quản lý `src/main/resources/db/migration/V<N>__<snake_case_description>.sql`, bắt đầu V1 ở PR tiếp theo, tăng số nguyên riêng từng database. Auth V1/User V1 không xung đột; phối hợp nhánh cùng service trước khi chọn version. Không sửa migration đã áp dụng. Chưa có migration/schema history/business table; validate 0 migrations chỉ là readiness.

Xem [development guide](../docs/DEVELOPMENT.md) để build/run và [báo cáo readiness](../docs/TV1_TECHNICAL_READINESS.md) để xem bằng chứng.

## 5. Recovery / rollback giữ dữ liệu

- **Global preflight thất bại:** admin kiểm tra owner/flags/membership/comment marker bằng truy vấn read-only. Đối tượng không nhận diện được hoặc owner khác: dừng, giao TV1 đối chiếu; không tự đổi owner/gắn marker để vượt gate.
- **Sai secret:** khôi phục secret hiện tại rồi test. Script không reset mật khẩu; rotation là thao tác quản trị riêng có xác nhận/maintenance plan.
- **Lỗi giữa chừng:** role creation có transaction; CREATE DATABASE không thể cùng transaction. Giữ đối tượng đã commit, sửa nguyên nhân rồi chạy lại cùng secret. Nếu dừng giữa CREATE DATABASE và COMMENT, DB có thể thiếu marker: admin đối chiếu owner `*_migrator`, DB mới rỗng và lịch sử thao tác trước khi quyết định khôi phục marker. Không tự nhận DB chỉ vì trùng tên; không chia sẻ SQL password/error context.
- **Port bị chiếm:** kiểm tra `Get-NetTCPConnection -LocalPort 5432`, chọn cổng trống như mục 2; không kill process khác. Container có thể ở trạng thái Created sau bind failure; sửa cổng rồi `up ... postgres` trên cùng project/volume.
- **HBA:** kiểm tra `pg_hba_file_rules.error`; recreate riêng PostgreSQL nếu cần. File HBA mount read-only ngoài volume. Không gỡ isolation khi các role mới còn dùng.
- **Tạm dừng:** `docker compose -f docker/compose.dev.yml stop postgres`. Giữ `.env`, volume, DB/roles. Rollback source/app không tự rollback dữ liệu provision; giữ HBA an toàn, không xóa DB/roles. Không `down -v`, prune, DROP DATABASE.

Secret quản trị local cũ từng nằm trong tracked baseline: coi là đã công khai, không dùng production. PHASE B bỏ credential khỏi source nhưng không tự rotate admin của volume hiện hữu.
