# TV1 — PHASE B Technical Readiness

Refs #1. Kiểm chứng ngày 2026-10-16 trên Windows/PowerShell tại `D:\CMC\Project-HK5-Git`. Baseline main `1a9d579cdf42c8aa170577a137624c4ec0cfdcce`; [PR #24](https://github.com/duyxl56785/Project_HK5/pull/24) MERGED lúc `2026-10-16T01:46:47Z`, đã kiểm tra bằng GitHub CLI và `merge-base --is-ancestor`. Main fast-forward không phát sinh thay đổi; branch `chore/tv1-auth-readiness`. Không merge PR/đóng Issue #1; giữ `.serena/` và mọi `backend/*/bin/`.

## A. Technical readiness

| Component | Current State | Test thực tế | Result | Remaining Risk |
|---|---|---|---|---|
| Java | Oracle JDK 21.0.11 | `java -version`, JAVA_HOME/PATH chỉ tiến trình | PASS | PATH terminal khác phải cấu hình lại |
| Gradle | Wrapper 8.10, checksum distribution có sẵn | `gradlew.bat --version`, build 11 module | PASS | Lần đầu cần mạng; không nâng wrapper |
| Docker | Engine 29.7.2 Linux | info, Compose config/ps, inventory container/volume/network | PASS | Chỉ local; không chạy Redis/Kafka |
| PostgreSQL | 16.15 healthy, project `docker`, volume `docker_pgdev` | SQL version, inventory, HBA parse không lỗi | PASS | Container-local admin trust; không phải production |
| auth_db | Owner auth_migrator, runtime auth_app, 0 bảng | login, wrong password, cross-DB/DDL/SET ROLE denial | PASS | Chưa schema nghiệp vụ |
| user_db | Owner user_migrator, runtime user_app, 0 bảng | Tương tự Auth | PASS | Chưa schema nghiệp vụ |
| Flyway | core + PostgreSQL module 10.10.0 theo BOM | 2 live JPA/JDBC tests + `validateWithResult`, 0 pending | PASS | Validate 0 migration; migrate nghiệp vụ NOT_RUN |
| Auth dependencies | JPA/Hibernate 6.5.3.Final, JDBC 42.7.4 | dependency resolution; Security 6.3.4, Nimbus 9.37.3 ở test classpath; HS256 correct/wrong-key test | PASS | Chưa Security/JWT issuer runtime |
| Gateway dependencies | Gateway 4.1.6/reactive scaffold | resolved runtimeClasspath, không servlet MVC | PASS | Chưa routes/verifier/CORS; HTTP security NOT_RUN |
| CI | Backend readiness, JDK21/PostgreSQL16 trên Ubuntu runner | Provision volume mới, rerun, isolation scripts, build/tests | PASS | [GitHub run thực tế](https://github.com/duyxl56785/Project_HK5/actions/runs/38839212118); branch protection/review vẫn do repository quản lý |
| Security config | 4 secret độc lập runtime, 4 role không superuser/membership, HBA + ACL cách ly | positive/negative SQL, localhost bind, Git/secrets review | PASS | Admin secret baseline cũ đã công khai, không tự rotate; không chứng nhận production |

Framework giữ nguyên Boot 3.3.5, Cloud 2023.0.5, dependency-management 1.1.6, Java 21, Gradle 8.10. Thêm persistence chỉ ở Auth/User để kiểm chứng readiness; Security/JOSE chỉ testImplementation ở Auth, không mở endpoint. BOM và hành vi Flyway đối chiếu [Boot v3.3.5 dependency definitions](https://github.com/spring-projects/spring-boot/blob/v3.3.5/spring-boot-project/spring-boot-dependencies/build.gradle), [Boot database initialization](https://docs.spring.io/spring-boot/3.3/how-to/data-initialization.html). Quyền database tham chiếu [PostgreSQL16 privileges](https://www.postgresql.org/docs/16/ddl-priv.html).

## B. Files changed / ownership

| Added / Modified | Paths | Purpose | Ownership |
|---|---|---|---|
| Added | `.env.example` | Placeholder rỗng, mẫu cổng local | TV1 shared infrastructure |
| Modified | `docker/compose.dev.yml` | Runtime admin secret, localhost/cổng env, HBA mount | TV1 |
| Added | `docker/postgres/pg_hba.conf` | SCRAM và giới hạn DB của 4 role mới | TV1 |
| Added | `docker/provision-tv1.ps1`, `docker/test-tv1-databases.ps1` | Provision idempotent, guard owner/role/container, live positive/negative checks | TV1 |
| Modified | `backend/{auth-service,user-service}/build.gradle` | JPA/JDBC/Flyway theo BOM, kiểm tra live DB không dùng cache | TV1 |
| Modified | `backend/{auth-service,user-service}/src/main/resources/application.yml` | Datasource riêng, validate, Flyway disabled | TV1 |
| Added | `backend/{auth-service,user-service}/src/main/resources/application-migration.yml` | Profile migration role riêng cho PR kế tiếp | TV1 |
| Added | `backend/{auth-service,user-service}/src/test/java/.../DatabaseReadinessTest.java` | JPA/JDBC connect + Flyway validate PostgreSQL thật | TV1 |
| Added | `backend/auth-service/src/test/java/.../Hs256DependencyTest.java` | HS256 dependency smoke test với key runtime ngẫu nhiên | TV1 |
| Added | `.github/workflows/backend-readiness.yml` | CI build + live DB permission checks trên volume mới | TV1 shared infrastructure |
| Modified | `README.md`, `backend/README.md`, `docs/DEVELOPMENT.md`, `docker/README.md` | Clone/JDK/wrapper/env/provision/build/recovery | TV1 shared docs |
| Added | `docs/TV1_TECHNICAL_READINESS.md` | Bằng chứng, giới hạn và handoff | TV1 |

Không thay code TV2–TV5, Contract v1.0, ADR, migrations hay API. `.env` local chứa secret thật được giữ ngoài Git.

## C. Database report

Trước provisioning: `hk5_dev`, `postgres`, `template0`, `template1`, owner `hk5`; chỉ role login `hk5` (admin/superuser). `hk5_dev`: public schema owner `pg_database_owner`, 0 application table. Không suy từ kết quả lịch sử; đã truy vấn lại.

Sau provisioning:

| Database | Owner | Runtime privileges / isolation |
|---|---|---|
| auth_db | auth_migrator | auth_app: CONNECT + public USAGE; PUBLIC không CONNECT/TEMP; không DDL/SET ROLE |
| user_db | user_migrator | user_app: CONNECT + public USAGE; PUBLIC không CONNECT/TEMP; không DDL/SET ROLE |
| hk5_dev | hk5 | Owner/ACL/schema giữ nguyên; role TV1 bị HBA chặn |
| postgres, template0, template1 | hk5 | Giữ nguyên; role TV1 chỉ được kết nối DB riêng qua HBA allowlist |

4 login mới: NOSUPERUSER/NOCREATEDB/NOCREATEROLE/NOREPLICATION/NOBYPASSRLS, không membership; mỗi role có secret CSPRNG độc lập 32 byte (Base64 trong environment). Migration role sở hữu đúng DB, runtime không sở hữu DB/schema. Không cấp blanket DML/future table rights: PR nghiệp vụ phải GRANT đúng bảng, giữ schema history ngoài quyền runtime. Không FK xuyên DB, không business migration V1, không seed.

Provision trên **volume cũ Windows** và **volume mới GitHub runner** đã PASS. Chạy lại nhiều lần ở Windows không đổi DB OID/owner/ACL, role OID/password verifier; so sánh schema-only dump `hk5_dev` trước/sau bằng nhau (loại nonce `\restrict` của pg_dump). Cả auth_db/user_db/hk5_dev vẫn 0 application table; Flyway chưa tạo schema history. Script chỉ CREATE nếu thiếu, không DROP/ALTER password/đổi owner. Recovery giữa CREATE DATABASE và COMMENT, owner lạ, secret sai và rollback giữ dữ liệu: [runbook](../docker/README.md#5-recovery--rollback-giữ-dữ-liệu).

Cổng 5432 đang do process PostgreSQL Windows giữ. Lần recreate đầu FAIL bind localhost; đổi local `.env` sang 5433 và kiểm tra lại PASS. Không kill/sửa process đó; chỉ recreate `docker-postgres-1`, cùng project/volume/network. Các container dự án khác không bị thao tác.

## D. Verification — commands đã thực thi

Các lệnh Gradle chạy tại `backend`, với JDK21 set trong process; Docker/Git/script chạy tại gốc repo, env được nạp từ `.env` mà không in giá trị.

| Loại | Commands / evidence | Kết quả |
|---|---|---|
| Git gate | `git status --short`, branch/remote; `gh pr view 24 ...`; `git fetch origin`; `git merge --ff-only origin/main`; ancestor check; tạo readiness branch | PASS |
| Configuration | `docker info --format ...`; `docker compose --env-file .env -f docker/compose.dev.yml config --quiet`; `ps`, `docker ps -a`, `volume ls`, `network ls`; inspect chỉ trường cần thiết | PASS |
| JDK/wrapper | `java -version`; `.\gradlew.bat --version` | PASS: 21.0.11 / 8.10 |
| Baseline build | `.\gradlew.bat build --console=plain` trước thay đổi | PASS: 55 tasks; **11 module test NO-SOURCE**, không phải tests PASS |
| DB provisioning | `up -d --no-deps --wait --wait-timeout 60 postgres`; `./docker/provision-tv1.ps1` nhiều lần | PASS trên volume cũ |
| DB permission isolation | `./docker/test-tv1-databases.ps1` | PASS 4 roles; password/cross-DB/DDL negative checks |
| Idempotency/data preservation | SQL fingerprints trước/sau rerun: OID/owner/ACL và password verifier so sánh trong bộ nhớ; `pg_dump --schema-only --no-comments hk5_dev`; table inventory | PASS; không in verifier/secret |
| Dependency resolution | Auth/User/Gateway `dependencies`; Auth `dependencyInsight --dependency spring-security --configuration testRuntimeClasspath` | PASS: Flyway10.10.0/JDBC42.7.4/Security6.3.4/Nimbus9.37.3; không FAILED dependency |
| HS256 smoke | `:auth-service:test --tests '*Hs256DependencyTest'` | PASS sau sửa fixture kid/JWK |
| Live JPA/JDBC/Flyway | `TV1_DATABASE_TESTS=true`, `:auth-service:test :user-service:test --rerun-tasks --console=plain` | PASS: 3 tests, 0 skipped/failures/errors |
| Final build | `TV1_DATABASE_TESTS=true`, `.\gradlew.bat build --console=plain` | PASS: 59 tasks, live DB tests executed; 9 module khác NO-SOURCE |
| Không có DB credentials | Bỏ TV1_DATABASE_TESTS/runtime passwords, chạy Auth/User test | PASS: HS256 chạy, 2 live DB tests SKIPPED; không báo thành DB PASS |
| Guard/config/secrets | Thiếu AUTH_DB_PASSWORD bị từ chối trước mutation; PowerShell parser; staged paths/diff --check; đối chiếu exact runtime/admin secrets với staged content và local logs; .env Git ignored | PASS; không thay full-history/CVE scan |
| CI volume mới | [run 38839212118](https://github.com/duyxl56785/Project_HK5/actions/runs/38839212118), implementation commit 088a4de | PASS: provision + rerun + permission checks + JDK21/Gradle8.10 build; completed 2026-10-16T02:14:12Z |
| Business unit/integration | Chưa entity/API/schema; không triển khai ở PHASE B | NOT_RUN |
| Business migration | Không chạy migrate/clean/baseline; chỉ validate/info trên DB rỗng | NOT_RUN |
| E2E / Gateway HTTP / production | Chưa implementation tương ứng; không tác động production | NOT_RUN |

Bằng chứng local (generated, Git ignored): `backend/build/readiness/{runtime-dependencies,security-dependencies,final-build}.log`; JUnit XML tại `backend/auth-service/build/test-results/test/TEST-*.xml` và tương tự User. Suite: Auth DatabaseReadinessTest 1/0/0/0, Hs256DependencyTest 1/0/0/0, User DatabaseReadinessTest 1/0/0/0 (tests/skipped/failures/errors). CI lưu log runner ở link delivery khi có run thực tế. Nguồn kiểm tra nằm trong các test/script committed để chạy lại.

FAIL quan sát và đã xử lý: sandbox Git ownership/network (chạy lại với quyền phù hợp, không đổi safe.directory toàn máy); Docker port5432 conflict (dùng5433); HS256 fixture có kid nhưng ImmutableSecret không mang kid (đổi fixture sang JWK có cùng key ID); psql connection errors không in SQLSTATE như SQL execution errors (negative check khớp thông báo authentication/HBA và bắt buộc exit khác0). Các lỗi không được tính là PASS cho đến lần chạy lại đạt.

### D.1. Đối chiếu JVM timezone PR #27

Bổ sung ngày 2026-10-17 cho head `6a7f1724bb533cc5ee92c431afb24d9844dff631`. [Review TV2/TV5](https://github.com/duyxl56785/Project_HK5/pull/27) so sánh cùng source/database, chỉ đổi JVM timezone:

| JVM timezone | Kết quả readiness Auth/User | Kết luận |
|---|---|---|
| `Asia/Saigon` | 1 PASS, 2 FAIL, 0 SKIPPED; Gradle exit 1 | PostgreSQL 16.15 của môi trường review không có tên này trong `pg_timezone_names`; từ chối startup parameter `TimeZone`, SQLState `22023` trước test body |
| `UTC` | 3 PASS, 0 FAIL/error/SKIPPED; Gradle exit 0 | UTC được PostgreSQL đó chấp nhận; cả hai test DB thực sự chạy với `TV1_DATABASE_TESTS=true` |

[Log/XML và SHA256 của TV5](https://github.com/duyxl56785/Project_HK5/tree/c9d39217993970563ab9917ef2fdd911483f62bf/reports/evidence/w1-gates-20261017) giữ riêng lượt FAIL và lượt UTC PASS. [CI readiness tại đúng head trên](https://github.com/duyxl56785/Project_HK5/actions/runs/38839525314/job/116776151812) cũng SUCCESS; đây là bằng chứng trước thay đổi tài liệu này. Không quy lỗi timezone thành lỗi credentials/dialect hoặc suy rộng rằng mọi PostgreSQL đều thiếu `Asia/Saigon`.

Dùng [hướng dẫn PowerShell UTC và khôi phục `JAVA_TOOL_OPTIONS`](DEVELOPMENT.md#jvm-timezone-cho-authuser-trên-powershell) trước Gradle test/build và Auth/User `bootRun`, áp dụng riêng từng terminal; không đổi timezone Windows, HBA, quyền DB, credentials hoặc volumes.

Kiểm tra lại local ngày 2026-10-17: `docker-postgres-1` đang Exited, credentials Auth/User chưa nạp vào tiến trình; readiness DB và `bootRun` **NOT_RUN**, không tự khởi động/provision hay dùng PostgreSQL của dự án khác. Full backend `build --no-daemon --console=plain` với JDK 21/UTC, bỏ `TV1_DATABASE_TESTS`, **PASS (exit 0)**: XML ghi 1 PASS (HS256), 2 DB tests SKIPPED, 0 failure/error; không tính SKIPPED thành DB PASS. Lượt build trước có `--rerun-tasks` cũng tạo lại ba XML này. Đoạn đặt/khôi phục JVM options được kiểm tra với biến ban đầu chưa có và đã có giá trị.

## E. Architecture / security review

- **CRITICAL:** không phát hiện lỗi phá dữ liệu/secret mới trong thay đổi đã kiểm tra; không chứng nhận Auth production. Rủi ro HS256 shared trust theo ADR vẫn giữ nguyên, không RS256/JWKS.
- **MAJOR:** admin credential baseline cũ đã công khai; đưa ra env không xóa lịch sử Git/không rotate volume. Không tái sử dụng ngoài local. Chưa security runtime/routes; không public hóa scaffold.
- **MAJOR đã xử lý:** một DB chung và thiếu app-role isolation → hai DB/four role, HBA + ACL và negative tests thực tế. Port clash → env port riêng, không ảnh hưởng PostgreSQL khác.
- **MINOR:** Gradle cảnh báo deprecated features cho Gradle9; không nâng wrapper để xử lý ngoài phạm vi. Flyway warning không có migrations là đúng PHASE B; Windows JDK global không được thay đổi.
- **GOOD:** volume/data cũ giữ nguyên; opt-in migration profile, validate-only runtime, không schema nghiệp vụ; secrets runtime; test actual PostgreSQL; framework và contract không đổi; minimal CI có permissions contents:read và actions pinned SHA.

## F. Delivery và kết luận

**READY cho PR Auth Database + Flyway.** Local Windows/volume cũ và GitHub CI/volume mới đã kiểm chứng thành công. Không có blocker nền tảng còn mở trong phạm vi PHASE B; chưa phải readiness nghiệp vụ/production.

Implementation commit: `088a4de39c6c65d4eb10803169ea466d02e3c066`, branch `chore/tv1-auth-readiness`, [PR #27](https://github.com/duyxl56785/Project_HK5/pull/27) vào main, [CI PASS](https://github.com/duyxl56785/Project_HK5/actions/runs/38839212118). Báo cáo được cập nhật bằng commit docs tiếp theo để lưu kết quả CI thật. Issue #1 đã xác minh vẫn OPEN. PR không tự merge; Technical Lead cần review/merge theo quy trình trước PR persistence. Không push main/force-push; các file untracked ban đầu giữ nguyên.

## G. PR tiếp theo

`feat/tv1-auth-database`: Flyway V1 accounts/roles/refresh persistence + durable provisioning task theo Contract v1.0; entity/repository; GRANT đúng business tables cho auth_app; PostgreSQL16 integration tests (empty migration/revalidate, uniqueness/concurrency/locking/transaction constraints). Chỉ bật migration trong tiến trình có đúng role/secret. Điều chỉnh readiness test 0-pending khi bắt đầu có migrations. Auth API/JWT issuer, User business, Gateway/Frontend integration vẫn là các PR riêng. Technical Lead review readiness PR trước khi merge và bắt đầu PR Auth Database; Issue #1 tiếp tục mở.
