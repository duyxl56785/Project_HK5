# Seller Persistence — kiểm tra lại ngày 16/10/2026

Refs #2. Kiểm tra tại 08:33–08:38 Asia/Saigon, source `73296d3` trên `feat/tv2-seller-persistence`. Báo cáo này bổ sung kết quả mới; không thay thế hoặc cộng lại các lượt PASS lịch sử.

## Git và phạm vi

- Preflight: working tree sạch, HEAD và origin cùng `73296d3`; giữ nguyên ba commit `408addc`, `c30d312`, `73296d3`.
- [PR #7](https://github.com/duyxl56785/Project_HK5/pull/7): OPEN, Ready, head `82dea09`, base main; GitHub mergeable=true nhưng blocked, chưa có APPROVED độc lập tại head hiện tại.
- [PR #25](https://github.com/duyxl56785/Project_HK5/pull/25): OPEN/Draft, base `feat/tv2-seller-product`, head `73296d3` lúc kiểm tra, mergeable=true. Không retarget khi #7 chưa merge.
- Cả hai PR đã request TV1 `lexuanbao258-collab` và TV5 `nguyenminhduy090-alt`; không có check runs. #25 chưa có review submission. Review TV5 trên #7 là COMMENTED tại head cũ, không phải approval bản sửa.
- 13 file persistence chỉ thuộc seller-service. Đã đọc entity/repository/service/exception/migration, cấu hình/build, unit/integration tests và tài liệu. Không đổi source, migration, shared build, main hoặc module người khác trong lượt này.
- Local main giữ `d6ddbb8`; origin/main `230d760` chứa hai merge tài liệu Auth draft và Gradle wrapper. Không merge main vào feature branch trong lượt này; GitHub chưa báo conflict cho hai PR.

## Docker — BLOCKED

Kiểm tra ngoài sandbox để tránh kết luận nhầm từ giới hạn quyền của công cụ:

- Docker CLI 29.8.2; context `desktop-linux` trỏ đúng Linux Engine named pipe. `docker version/info` không nhận được Server vì thiếu `dockerDesktopLinuxEngine` pipe.
- WSL status/list đọc được: default distribution docker-desktop, version 2, Stopped. Cảnh báo WSL1 không khả dụng không chứng minh WSL2 hỏng. AccessDenied của WSL trong sandbox không lặp lại ngoài sandbox.
- Không có Docker process trước khi thử khởi động. Khởi động Desktop đã cài bằng phiên người dùng bình thường, cửa sổ ẩn; backend lỗi lại lúc `2026-10-16T01:34:33Z`.
- Host log: `initializing Secrets Engine`, rename `engine.sock` sang `engine.sock.stale` thất bại: `The file cannot be accessed by the system`.
- Thư mục runtime có thuộc tính Encrypted; socket 0 byte có Encrypted + ReparsePoint. ACL cấp ADMIN FullControl; truy vấn reparse metadata chỉ đọc vẫn trả Error 1920. Chưa chứng minh được process giữ khóa hoặc EFS là nguyên nhân duy nhất. Đây là lỗi host/runtime có bằng chứng, chưa phải lỗi Category/Seller.
- `docker desktop stop --timeout 20` FAIL: context deadline exceeded, frontend/backend vẫn chạy tại thời điểm kết thúc lệnh. Không force-kill hoặc tự khởi động lặp lại.
- Không xóa/đổi tên socket, giải mã, sửa ACL/cấu hình Windows, reset Docker, xóa images/volumes, unregister WSL hoặc thay dữ liệu trong lượt này. Đĩa Docker VHDX hiện có, khoảng 21 GB; chỉ đọc metadata, chưa backup hoặc xác minh toàn vẹn dữ liệu bên trong.
- Engine smoke container và PostgreSQL startup: NOT_RUN. Không có native PostgreSQL executable được phát hiện trên PATH; không thay PostgreSQL bằng H2/mock.

### Bước thủ công an toàn cho chủ máy / TV1

1. Chọn **Quit** ở hộp thoại lỗi Docker, thoát Desktop từ tray và kiểm tra process đã dừng. Nếu bị kẹt, lưu công việc rồi khởi động lại Windows; chưa sửa socket.
2. Khởi động lại Desktop từ tài khoản ADMIN tương tác. Kiểm tra `docker version`, `docker info`, `docker context ls`, `wsl --status`, `wsl -l -v`. CLI nằm ở `%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin\docker.exe` nếu PATH chưa có.
3. Nếu vẫn lỗi rename: TV1/chủ máy cần kiểm tra quyền EFS/key và tiến trình giữ file bằng công cụ quản trị tại máy. Thu thập diagnostics và rà secrets trước khi chia sẻ/upload. Không tự giải mã toàn AppData hoặc thay ACL hàng loạt. Lượt này dừng trước các thao tác cần Administrator/thay hệ thống nhạy cảm theo yêu cầu chủ máy.
4. Trước mọi sửa chữa nâng cao/reinstall, xác nhận Desktop đã dừng hoàn toàn rồi backup VHDX tại vị trí thực tế. Đã thấy `%LOCALAPPDATA%\Docker\wsl\disk\docker_data.vhdx`; không copy đĩa đang chạy. [Docker hướng dẫn backup khi Desktop không khởi động](https://docs.docker.com/desktop/settings-and-maintenance/backup-and-restore/). Chưa tự thực hiện backup, reinstall hoặc sửa EFS trong lượt này.
5. Khi Engine trả Server, chạy container đơn giản như `docker run --rm hello-world`, rồi chạy lại các lệnh Gradle bên dưới. Nếu máy này chưa sửa được, có thể bàn giao cùng SHA cho TV1/TV5 chạy trên máy có Docker Linux hoạt động và lưu XML/log tương ứng; không gọi đó là Docker local đã được sửa.

Nguồn chẩn đoán: [Docker troubleshoot](https://docs.docker.com/desktop/troubleshoot-and-support/troubleshoot/), [Microsoft cipher/EFS](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/cipher). Không có hướng dẫn chính thức đã đối chiếu nào chứng minh chỉ cần xóa socket là an toàn trong trường hợp này.

## Kết quả thực thi mới

JDK Temurin 21.0.12.1; Gradle 8.10 executable đã cài qua wrapper, không thêm wrapper/shared build vào feature branch. JAVA_HOME đặt riêng cho tiến trình kiểm thử.

```text
gradle -p backend build --dry-run --no-daemon --console=plain
gradle -p backend assemble test --rerun-tasks --continue --no-daemon --console=plain
gradle -p backend build --continue --no-daemon --console=plain
```

- Task graph: exit 0; cả seller-service và product-service có integrationTest trước check/build. Chữ SKIPPED trong dry-run không phải JUnit test bị skip.
- Assemble + unit: BUILD SUCCESSFUL, exit 0, 46 giây, 59 tasks executed; assemble 11 module. Unit tests thực sự được rerun.
- Full backend build/check: **BUILD FAILED**, exit 1, 35 giây, 2 integration tasks executed + 59 up-to-date. Unit đã PASS ngay trước đó được tái sử dụng; không tính thành lượt test thứ hai. Hai lỗi đều Testcontainers initializationError tại DockerClientProviderStrategy; chưa có PostgreSQL container hoặc DB test body chạy.

| Bộ kiểm thử / tác vụ | PASS | FAIL | SKIPPED | NOT_RUN |
|---|---:|---:|---:|---|
| Seller unit | 80 | 0 | 0 | 0 |
| Seller PostgreSQL | 0 | 1 lỗi khởi tạo suite | 0 | 21 test body dự kiến |
| Category + Product fallback regression | 53 | 1 lỗi khởi tạo DB suite | 0 | 14 Category DB test body |
| Backend assemble | 11 module | 0 | 0 | 0 |
| Backend full build/check | 0 | 1 lần build | 0 | 0 |
| JWT thật/API/Gateway/Frontend/E2E | 0 | 0 | 0 | Toàn bộ, chưa có suite live để định lượng |

Hai initialization failures không phải 35 test body FAIL. Không có JUnit test bị skip. Chín module NO-SOURCE không được tính là test nghiệp vụ PASS.

XML Seller: Application 13, Lifecycle 50, Profile 10, PersistenceService 7. XML Product: CategoryService 18, Controller 13, CategorySecurity 7, ProductServiceSecurity 15. Tổng **133 PASS, 0 unit FAIL, 0 skipped**.

Bằng chứng local, ignored: `build/persistence-verification/recheck-20261016/` chứa task-graph.log, assemble-unit.log, full-build.log, junit-summary.json và docker-evidence.txt. Raw XML ở seller/product `build/test-results/{test,integrationTest}`. Không commit build outputs hoặc log host đầy đủ.

## Review kiến trúc và acceptance

- Domain/lifecycle giữ nguyên, entity JPA riêng; result nội bộ không lộ entity qua HTTP. Không có Seller business Controller hoặc production policy allow-all.
- Orchestration REQUIRED trên public class, cần Spring bean/proxy khi wiring; test configuration có explicit bean. Tạo bằng `new` bên ngoài Spring không áp dụng transaction. Chưa đăng ký production service khi thiếu verified policy là giới hạn có chủ đích.
- Assigned UUID + nullable Long @Version cho entity mới, owner UNIQUE/collation C, trạng thái/reason/version constraints khớp contract đã chốt. Không exists-before-insert thay unique DB; không copy REQUIRES_NEW/advisory lock của Category.
- Expected version kiểm trước thay đổi; named unique/optimistic-lock failures được dịch thành lỗi nội bộ. Flush nằm trong REQUIRED transaction; outer rollback phải undo state/version. Các hành vi runtime này vẫn phải được PostgreSQL chứng minh.
- Suite DB có 21 invocations: migration/JPA/durable registration; duplicate/exact owner; resubmit cùng ID/lifecycle/reason; hai ca REQUIRED rollback; stale expectedVersion; quyền/input không đổi DB; 11 rows constraint; concurrent insert quan sát lock wait thật; hai transaction load cùng version tranh moderation.
- Chưa thấy defect source được chứng minh cần sửa. Không đổi kiến trúc/migration hoặc thêm test giả để bù môi trường.
- Persistence: **PARTIAL, PostgreSQL acceptance BLOCKED**. Unique/concurrent registration, rollback, @Version/concurrent moderation và Flyway runtime đều NOT_VERIFIED trong lượt này. PR #25 review được ở dạng Draft, chưa đủ merge; #7 cần re-review/approval độc lập và đánh giá bằng chứng DB trước merge. Không merge PR nào.

## Contract mới và nhiệm vụ tiếp theo

[PR #24](https://github.com/duyxl56785/Project_HK5/pull/24), head `c8a4b44`, đã ghi **9 quyết định TL DECIDED**, chưa merge. HS256/keyring, opaque verified sub, CUSTOMER onboarding, ADMIN moderation/live authorization và 401/403/503 đã có hướng chính thức. Không còn coi cả chín quyết định là chưa chốt. Tuy nhiên issuer/audience/runtime key, current-state adapters, role synchronization/versioning và Seller endpoint/DTO/internal pair wire contracts vẫn chưa được triển khai/kiểm chứng. Main Auth/User/Gateway còn scaffold; chưa thể mở ghi bằng JWT giả hoặc header client.

Source hiện tại chưa có Store/Product entity/business API. Nhánh TV3 vừa fetch vẫn scaffold; Issue #3 cần catalog/store/product identity, giá server, eligibility, expectedItems và dữ liệu checkout hai Store. TV4 PR #26 chỉ có tài liệu baseline/handoff, test runtime NOT_RUN; Issue #4 cần ownership Store/Seller, trạng thái hợp lệ và Product identity cho Inventory/fulfillment. Không tự coi handoff draft của TV4 là contract được chốt. Kế hoạch #19 còn OPEN và có điểm cần thống nhất Store boundary: bảng phạm vi nhắc Store ở product-service trong khi xác nhận persistence chưa quyết định boundary. Chưa chọn schema/module Store thay nhóm.

Ưu tiên tối đa năm việc:

1. **Gỡ blocker Docker và nghiệm thu PR #25.** Mục tiêu: chạy 21 Seller + 14 Category PostgreSQL tests thật và full build/check, vì hiện chưa có bằng chứng persistence bền vững/concurrency. Branch hiện tại `feat/tv2-seller-persistence`, tiếp tục PR #25. File cần cập nhật: SELLER_TEST_REPORT.md và báo cáo này; chỉ sửa SellerPersistenceIntegrationTest.java hoặc Seller source nếu test chứng minh defect. Contract dùng SELLER_PERSISTENCE_CONTRACT.md đã xác nhận, không đổi schema/API. TV1 hỗ trợ host/Docker; TV5 review constraints/concurrency và test evidence. Tests bắt buộc: smoke container, Flyway/JPA, durable/unique/exact owner/concurrent registration, resubmit giữ ID, rollback, stale/concurrent moderation, SQL constraints, Category regression và full backend build. AC: mọi DB body thực thi PASS, không skip, full build exit 0, diff đúng TV2; JWT/E2E vẫn ghi riêng. Sau đó xin reviewer xác nhận, chưa tự merge.
2. **Chốt handoff Store/Product với TV1/TV3/TV4/TV5** trong PR contract riêng: data boundary Store, ID/owner mapping, status/public visibility, giá/currency/version, batch read/validate và lỗi/timeout/fail-closed; cấp fixtures hai Store test-only đúng hợp đồng. Có thể soạn song song khi Docker/JWT bị chặn; chưa mở production endpoint hoặc quyết định DTO shared một phía.
3. **Seller API + verified Auth adapter** trong PR riêng khi persistence và wire/current-state contract sẵn sàng: actor từ verified sub, CUSTOMER register, ADMIN/live role/no self moderation, real-signed JWT tests, rollback/conflict/error/Gateway tests. Durable role sync phối hợp TV1; không coi mock policy là tích hợp.
4. **Store domain/persistence/ownership** sau khi chốt boundary: một Store cho Seller APPROVED, unique slug và truy cập A/B, PostgreSQL concurrency; giữ HTTP ghi deny-all đến verified adapter hoàn tất.
5. **Product tối thiểu + catalog read/validate cho checkout**, phối hợp TV3/TV4 và TV5: SKU unique, status/price/eligibility, FK Category restrictive bằng migration mới + CATEGORY_IN_USE/concurrent tests, seed hai Store; ảnh/moderation theo phạm vi được giao. Không chờ toàn bộ Seller UI xong mới bàn giao hợp đồng/fixtures.

Kết luận: chưa nghiệm thu persistence; #25 thiếu PostgreSQL/full build PASS và review. Cần TV1 cho host/runtime Auth/wire contract, không cần chờ để review domain hoặc soạn đề xuất Store/Product. Chưa bắt đầu lập trình feature Store/Product/Seller API trong lượt này. Issue #2 giữ OPEN/PARTIAL và chỉ tích phần có bằng chứng.
