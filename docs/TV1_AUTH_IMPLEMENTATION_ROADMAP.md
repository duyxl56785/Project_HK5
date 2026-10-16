# TV1 — Auth implementation roadmap và preflight PHASE A

Ngày 2026-10-16, Refs #1. Baseline `main` cuối audit: `230d760fd71b8359bb00d4100c2eebb4829d1ffe`, sau PR #22 được merge bởi người khác trong phiên làm việc; baseline ban đầu `226dd2c`. Áp dụng [Contract v1.0](TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md), do Technical Lead Lê Xuân Bảo chốt; không ghi nhận consumer approval nếu chưa có bằng chứng.

> Đây là kế hoạch đi kèm PR Contract, **không phải PHASE B đã hoàn thành**. Không tạo migration, dependency, seed, API, DB hoặc container trong PR này. PR Contract v1.0 phải merge trước khi tạo `chore/tv1-auth-readiness`; không tự merge. Issue #1 vẫn mở.

## 1. Audit nền tảng thực tế

| Hạng mục | Kết quả tại PHASE A | Bằng chứng / bước tiếp theo |
|---|---|---|
| Auth / User | Scaffold, chưa controller/entity/repository/security/DB config | Chỉ entry point, starter-web, Actuator; ports 8101/8102; chưa migration hoặc dữ liệu seed tracked |
| Gateway | Reactive scaffold, chưa routes/verifier/CORS | starter-gateway, port 8080; không dùng servlet SecurityFilterChain cho Gateway |
| Java | PATH đang Java 11.0.32.1, không đạt toolchain 21 | `java -version`; JDK 21.0.11 tại `C:/Program Files/Java/jdk-21.0.11` chạy được bằng absolute path. PHASE B cấu hình JAVA_HOME/JDK21 riêng cho tiến trình; không đổi máy toàn cục ngầm |
| Gradle | Wrapper 8.10 đã có trên main sau #22 merge; không cần Gradle trên PATH | `backend/gradlew.bat --version` bằng JDK21 exit0, Gradle 8.10/Launcher+Daemon JVM21.0.11; properties có distribution SHA256. Build/dependency resolution chưa chạy |
| Stack | Boot 3.3.5, Cloud 2023.0.5, dependency-management 1.1.6, Java toolchain 21 | `backend/build.gradle`; không thay version/framework |
| Security / JWT | Chưa dependency trên Auth/User/Gateway main | BOM chính thức Boot 3.3.5 quản lý Spring Security 6.3.4; dependency resolution thực tế chưa chạy |
| Flyway / JDBC | Chưa cài/configure cho TV1 | BOM Boot 3.3.5 quản lý Flyway 10.10.0, PostgreSQL JDBC 42.7.4; cần `flyway-core` + PostgreSQL module, datasource thật và migration tests |
| PostgreSQL | Compose khai báo `postgres:16`, chỉ DB `hk5_dev`; chưa có auth_db/user_db | Chưa kiểm runtime/schema/dữ liệu vì Docker Engine không kết nối được; không suy ra DB trống |
| Docker | Compose CLI v5.4.0; config validation exit 0 | `docker compose -f docker/compose.dev.yml config --quiet` PASS; `docker info --format '{{.ServerVersion}}'` FAIL do thiếu named pipe Docker Desktop Linux Engine; không khởi động/xóa container/volume |
| Credentials hiện có | Compose có credential ví dụ local trong tracked baseline | Không copy vào tài liệu, secret mẫu hoặc service config mới; PR infrastructure riêng phải tách credential từng DB/service và giới hạn port bind khi cần |
| CI | Không có file tracked `.github/workflows/*` tại baseline; chưa có bằng chứng CI Auth | Không kết luận branch protection đang enforce; không suy local wrapper verification thành CI PASS |

Nguồn phiên bản: [Spring Boot dependency definitions tại tag v3.3.5](https://github.com/spring-projects/spring-boot/blob/v3.3.5/spring-boot-project/spring-boot-dependencies/build.gradle). Dùng dependency không pin version riêng để giữ BOM; PHASE B vẫn phải kiểm `dependencyInsight` vì BOM/dependency khác có thể ảnh hưởng resolution. [Boot 3.3 Flyway initialization](https://docs.spring.io/spring-boot/3.3/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway) yêu cầu module theo database; trang 3.3 hiện có thể là patch mới hơn 3.3.5, không lấy version hiện tại thay baseline.

## 2. Compatibility và architecture review

| Mức | Phát hiện / rủi ro | Xử lý / owner |
|---|---|---|
| CRITICAL — release blocker nếu xảy ra | HS256 key lộ ở bất kỳ verifier cho phép forge token, gồm ADMIN | Technical Lead chấp nhận trust domain MVP; TV1 kiểm secret isolation, rotation/incident plan; không mở endpoint khi thiếu verifier |
| MAJOR | Java mặc định 11, Docker Engine unavailable | Wrapper đã merge/chạy version bằng JDK21 nhưng DB tests vẫn bị chặn; TV1 xử lý môi trường ở PHASE B sau contract merge, không giả build PASS |
| MAJOR | Chưa 2 DB riêng, migration, current-account/role adapter hoặc Seller role sync | Chặn persistent Auth và sensitive authorization; TV1/TV2 chốt wire contract, version/retry/freshness trước tích hợp |
| MAJOR | Refresh concurrent/lost-response có thể buộc login lại theo policy nghiêm | Giữ rotation/reuse family, test 2 tab + network fault; FE single-flight không thay atomicity backend |
| MAJOR | V2 gốc và schema/dữ liệu runtime chưa kiểm được | Không đổi ID/normalize email hàng loạt; inventory + mapping + migration plan nếu phát hiện dữ liệu cũ |
| MINOR | Draft v0.1 chưa chốt UUID nội bộ; example ID không phải seed UUID | Giữ userId DTO name, opaque consumer semantics; Auth integration fixtures tạo UUID thật; không sửa domain String |
| MINOR | Category PR #7 cố ý trả 403 khi chưa Auth | TV2 giữ fail-closed tới PR verifier, sau đó update 401/403 tests; không đổi bằng PR tài liệu |
| GOOD | SellerIdentity PR #7 và buyerId PR #20 đều String opaque | Tương thích sub wire; không parse UUID ở consumer, không sửa TV2/TV5 code |
| GOOD | DB-per-service, immutable domain boundaries, Idempotency-Key và internal/public boundary được giữ | Giảm phạm vi thay đổi; không thay business events, money/review keys hoặc transaction TV2–TV5 |

PR tham chiếu cuối audit: [#7](https://github.com/duyxl56785/Project_HK5/pull/7) head `82dea09` thêm fallback Product deny-all; [#20](https://github.com/duyxl56785/Project_HK5/pull/20) head `0222378` thêm checklist/hướng dẫn; [#19](https://github.com/duyxl56785/Project_HK5/pull/19) head `1407e9d` làm rõ Review/Finance (order-product uniqueness, seller Store isolation), không đổi security TL. Cả ba chưa merge; không import AGENTS.md từ nhánh chưa merge như quy tắc main. Review lịch sử và test do thành viên khác chạy là bằng chứng của PR đó, không tính là test runtime nhiệm vụ này. Scope Review/Finance P0/P1, order/product business key và settlement vẫn do owner liên quan chốt; chỉ security policy OD-07 đã được TL xác nhận.

## 3. Gate PHASE B — chưa bắt đầu

1. PR Contract v1.0 được merge và main cập nhật fast-forward an toàn; kiểm lại PR #22/main/new PR để không dùng audit cũ làm trạng thái mới. Không tạo readiness branch trên contract chưa merge rồi coi là PR độc lập vào main.
2. Tạo `chore/tv1-auth-readiness`; dùng JDK21 và Gradle Wrapper đã merge trong #22; kiểm lại version/checksum theo main thực tế trước build, không dùng Java11 mặc định. Không tự merge PR khác hoặc đổi wrapper trong PR Contract.
3. Kiểm Docker Engine/PostgreSQL16, inventory DB/schema/volumes hiện hữu bằng thao tác đọc trước; không `down -v`, không xóa volume/migration. Compose valid không đồng nghĩa DB ready.
4. Chốt env/config riêng cho auth_db và user_db, role DB riêng và Flyway ownership; kiểm dependencies/JWT HS256 trên Boot 3.3.5 bằng resolution và test nhỏ. Hoàn thiện CI build/test ở PR nền tảng riêng nếu cần.
5. Nếu có thay đổi readiness thực tế, commit/push/PR riêng; chỉ sau gates này mới triển khai PR Database/API/User/Integration trong nhiệm vụ tiếp theo.

## 4. Database provisioning và dependency plan

Một PostgreSQL16 instance có thể chứa hai database riêng `auth_db`/`user_db`; mỗi ứng dụng chỉ có một datasource vào DB mình sở hữu. AccountId liên kết logic, không FK hoặc quyền truy vấn sang DB khác. Dùng role/mật khẩu riêng cấp runtime, hạn chế CONNECT/CREATE/public privileges theo nhu cầu; migration role được quyền DDL trong đúng DB/schema, app runtime tối thiểu. Boot không tạo database bằng Flyway: provision DB/role trước rồi Flyway tạo schema/tables.

Compose hiện chỉ tạo một database mặc định. PR infrastructure tương lai thêm bootstrap idempotent cho volume mới và runbook provisioning không phá dữ liệu cho volume đã có; không giả init script sẽ chạy lại trên volume cũ hoặc đổi POSTGRES_DB sẽ rename DB. Kiểm DB tồn tại và owner trước CREATE, không DROP/rename tự động. [PostgreSQL16 CREATE DATABASE](https://www.postgresql.org/docs/16/sql-createdatabase.html) là thao tác quản trị riêng, không chạy trong Flyway transaction thông thường. Không đụng volume/migration trong PHASE A.

Dependencies tương lai theo module, không bật toàn bộ subprojects:

- Auth/User persistence: starter-data-jpa, PostgreSQL JDBC, flyway-core và flyway-database-postgresql; `ddl-auto=validate`, không `create`/`update`/`create-drop` trên dữ liệu thật.
- Auth/User và protected servlet services: starter-security, starter-oauth2-resource-server, validation theo DTO; JWT signing/verification dùng JOSE/Nimbus do BOM quản lý, không thêm một thư viện JWT khác chỉ để ký HS256.
- Gateway: reactive resource-server/security theo WebFlux; không thêm starter-web vào Gateway hoặc share servlet filter với Gateway.
- Tests: security-test, PostgreSQL16 Testcontainers tương thích BOM; không dùng H2 để thay concurrency/constraints/migration tests PostgreSQL thật.

Actual dependency tree, Flyway/PostgreSQL16 integration và test harness vẫn NOT_RUN, phải xác minh ở PHASE B/PR Database. Không nâng Boot/Cloud trong PR Auth để sửa conflict không liên quan.

## 5. Các PR implementation — nhiệm vụ tiếp theo

| Thứ tự / PR | Dependency / phạm vi | Acceptance criteria và test bắt buộc |
|---|---|---|
| 0. Readiness / infrastructure | Contract v1.0 merge; wrapper #22 đã merge; nhánh `chore/tv1-auth-readiness` | JDK21/wrapper reproducible, Compose config và engine/PG readiness; hai DB/role không truy cập chéo; dependencyInsight + minimal CI; không mất volume/data |
| 1. Auth Database | Gate 0; chỉ Auth + hạ tầng TV1 cần thiết | Flyway mới theo auth_db; accounts(UUID, normalized email unique, password hash, status), role grants(CUSTOMER/SELLER/ADMIN), refresh families/tokens(hash unique, absolute expiry, used/revoked/parent), durable profile provisioning tasks(unique accountId). Entity/Repository, FK nội bộ, UTC timestamps. PostgreSQL16 tests migrate DB rỗng, validate lại, duplicate email concurrency, refresh uniqueness/locking, role constraints, persistence restart; không sửa migration đã áp dụng |
| 2. Auth API | PR Database; env HS256/issuer/audience/cookie/throttle; Gateway public route chưa mở khi thiếu verifier | Register transaction account+CUSTOMER+provision task; login/hash; 5 Auth endpoints và DTO/error đúng contract; atomic rotation/reuse/logout. Unit/HTTP/security/PG integration: role injection, wrong key/alg/kid/claims, expiration, Unicode/password max128 không truncate, race refresh/logout/replay, 7-day absolute TTL, lost response, CSRF, auth failures/rate limit. Không trả raw refresh/password hash |
| 3. User + sync | Database + Auth API; User DB/config và internal pair | Profile, shipping address/một default theo Issue #1; User migrations riêng, logical accountId, không cross-DB FK. Worker/durable task + insert-if-absent provisioning/recovery, không ghi đè profile sửa tay. Tests User outage, duplicate/out-of-order request, crash sau commit trước ACK, retry/reconciliation; GET/PATCH me và ownership; address concurrency riêng |
| 4. Integration | Auth/User APIs; owner adapters TV2–TV5 và internal wire contracts được review | Gateway allowlist + HS256 reactive/downstream verify, CORS/CSRF/header filtering; FE memory/single-flight và multi-tab coordination. Tests anonymous401/role403, current ADMIN removed, Seller suspended với JWT cũ, Store A/B isolation, internal pair/delegated actor, COD ADMIN+DELIVERED+idempotent, FE→Gateway→DB và 2 Store UAT với owner. Không tự sửa code TV2–TV5 trong PR TV1 |

Mỗi PR nhỏ có thể tách persistence/security/worker nếu diff quá lớn, nhưng không merge/public một bước thiếu validation hoặc fail-closed. Phần operation nhạy cảm/current-state adapter và role sync phải phối hợp TV1/TV2; nếu chưa có, giữ route deny-default. Không coi mock policy hoặc seed là tích hợp thật.

## 6. Data, seed và fixtures

- Main chưa có Auth/User migration/seed tracked; dữ liệu thực chưa inventory, không kết luận không có legacy accounts. Nếu phát hiện ID không UUID hoặc email collide sau normalize: báo MAJOR, giữ ID cũ và lập explicit mapping/migration/rollback plan trước import; không tự chuyển đổi.
- Auth/DB fixtures dùng UUID account sinh trong test, consumer unit fixture vẫn String opaque; ADMIN, CUSTOMER, hai SELLER/Store và pending/suspended seller tách rõ actor/mapping. TV2 cấp Store facts, TV3/TV4 cấp order/COD facts, TV5 cấp review/finance expectations.
- Không migration seed tài khoản ADMIN có mật khẩu mặc định. Test-only password/token/key sinh runtime hoặc isolated fixture không dùng lại ở deploy; admin bootstrap vận hành riêng với secret runtime và audit. Không log credential/token/raw refresh/hash/mật khẩu; log task/operation/account reference có kiểm soát.
- Retain used refresh token hash tới hết family expiry để detect reuse; cleanup chỉ dữ liệu hết hạn theo job được kiểm thử, không prune token cũ sớm để làm replay test pass. Logout/reuse/rotation cùng lock family, test cạnh tranh không cho family đã revoke sống lại.

## 7. Verification hiện tại và handoff

**PASS đã chạy:** kiểm Git/PR state; fast-forward main và nhánh contract sau #22 merge; đọc source/config/ownership; JDK21 absolute invocation; wrapper 8.10 `--version` bằng JDK21 exit0; Compose CLI/config exit0; tra BOM exact tag; compatibility String identity PR #7/#20 và diff head mới #7/#19/#20; Markdown/JSON/links/decision/diff kiểm ở PR Contract. **FAIL đã quan sát:** Docker Engine không kết nối qua named pipe; Java PATH là 11 nếu dùng mặc định. **NOT_RUN:** Gradle build/test/dependency resolution, database/Flyway migrations, runtime JWT/HTTP/session/CORS/E2E/CI; chưa có implementation và chưa đạt gate PHASE B.

TV2–TV5 dùng [Contract v1.0](TV1_AUTH_IDENTITY_GATEWAY_CONTRACT.md): opaque sub, verify HS256/issuer/audience/claims, không tin client identity; role không thay ownership/live status; ADMIN-only COD sau DELIVERED; internal pair không thay actor authorization; mọi thay đổi data/money/review key thuộc owner. Handoff phiên bản chính xác bằng commit SHA của PR Contract, không ghi consumer approval thay cho reviewer. Chưa có stub/token test thật được bàn giao trong nhiệm vụ tài liệu.
