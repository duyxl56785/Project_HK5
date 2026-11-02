# TV3 — Migration V5 rehearsal, PostgreSQL 16 disposable

Ngày thực hiện: 2026-11-01, Codex Terminal (Bash/Linux). Không chạy trên Windows PowerShell.

**Kết luận: READY_FOR_TV4_TV5_REVIEW trong phạm vi migration rehearsal bằng dữ liệu giả. Chưa đủ điều kiện triển khai database thật hoặc nghiệm thu Checkout E2E.**

## Candidate được bảo toàn

- Worktree: `/workspace/tv3-draft-replay-138`, detached HEAD, working tree sạch trước/sau.
- HEAD: `dfe903230c0d2c41ac59b412fd0f249b9f951da2`.
- Tree: `e7a45238a336f1ad52b5e61471302864df2f86e8`.
- Saved ref `refs/tv3-preserved/journal-b903a6d`: `b903a6d77a18801766b3695cbc893ef30dd6233e`.
- Saved ref `refs/tv3-preserved/draft-04876a1`: `04876a1858703c3f936cfbb86ee24d8a04d1e729`.
- Chuỗi: `5280ac7 → b903a6d → 04876a1 → 8860124 → 9a6d9f3 → dfe9032`.
- `git-final.txt`, `source.json` lưu kiểm tra Git và hash source. Bản sao V1–V5 khớp byte với Git HEAD; V1–V4 cũng khớp `main@5280ac7c` (`base-migration-verification.json`). Không xác nhận lại GitHub/main hiện tại trong lượt DB rehearsal này.
- Không thay đổi source, commit, refs, flags, GitHub hay worktree khác. Tất cả harness, class biên dịch kiểm tra và evidence nằm ngoài repository.

## Môi trường và cách cô lập

- Java: Temurin 21.0.8+9; Gradle 8.10; Flyway 10.10.0.
- PostgreSQL **16.6**, image `postgres:16.6-alpine`, image ID `sha256:5c773214aed7adab0900cd0a05dbc468348f76fe1e4c2ca5b0e222e9531f7811`.
- Image digest: `postgres@sha256:1d04b9ba1d4996401f2552b51beda8187f175c0645c091e4781134fc9c9a3eef`.
- Docker 28.4.0 có sẵn. Tạo container mới cho từng lượt, label UUID riêng, `--pull=never`, rootfs read-only, RAM 512 MiB. PGDATA/socket/tmp đều tmpfs; không tạo/xóa Docker volume.
- Chỉ publish cổng ngẫu nhiên trên `127.0.0.1`. JDBC URL sinh từ container mới; không đọc URL/token/password DB của ứng dụng hoặc thành viên khác.
- Password ngẫu nhiên chỉ tồn tại trong process/container disposable, không lưu vào evidence. Chỉ dùng role fixture mới trong cluster mới. Không bật Inventory/Finance/Auth gates, không khởi động demo stack hoặc application server.
- Lượt thành công: container `c386b4c61a416938bbed07d8aee24e3bb18334c1174711d76dc405ff81aa6493`, label run `a36a6bf5251f4445956cb551d0d47cf1`, cổng 32770. Đã stop/remove chính container này sau dump; hai container thử trước cũng đã thu hồi. Xem `run.json` và `attempt-*/run.json`.

## Source và fixture

Đọc toàn bộ V1–V5 cùng Order/SellerOrder/OrderItem/CheckoutAttempt/OutboxEvent, repositories, CheckoutDraft/CheckoutDraftJournal, InventoryOperationJournal, IdempotencyCoordinator, OrderWriteService và cấu hình datasource/Flyway/JPA. Harness sử dụng production class/repository/transaction annotation có sẵn; không component-scan server, scheduler, HTTP adapter hoặc Security filter.

Flyway chạy thật V1–V4 trên cluster mới, **không baseline/repair/clean và không sửa checksum**. Dữ liệu giả được INSERT hợp lệ sau V4, commit trước snapshot. `before_schema.sql`/`before_data.sql` là ảnh V4 có dữ liệu; upgrade V5 chạy ngay trên bản sao disposable này.

Fixture gồm ba Customer, ba MarketplaceOrder, mỗi Order hai Store/SellerOrder và hai OrderItem; Product `p1/p2` khác Inventory `SKU-A/SKU-B`. Có shipping COD, monetary snapshots 41.18/300.00/12.00, phí 0 và khác 0, DELIVERED/COLLECTED/settled, CANCELLED/REJECTED/VOID, outbox pending/published/dead. Có SUCCEEDED/PROCESSING/FAILED/NEEDS_RECONCILIATION và journal PENDING/IN_FLIGHT/UNKNOWN/CONFIRMED/REJECTED, RESERVE/CONSUME/RELEASE; write-fence tombstone. Fingerprint và reserve quantity được đối chiếu với OrderItem bằng production RequestFingerprint.

| Table | Trước V5 | Ngay sau V5 | Sau regression |
|---|---:|---:|---:|
| orders | 3 | 3 | 3 |
| seller_orders | 6 | 6 | 6 |
| order_items | 6 | 6 | 6 |
| checkout_attempts | 7 | 7 | 8 |
| outbox_events | 5 | 5 | 5 |
| inventory_operation_journal | 11 | 11 | 12 |
| write_fence_abort | 1 | 1 | 1 |
| checkout_order_drafts | chưa có | 0 | 1 |

Các dòng thêm ở cột cuối do regression tạo attempt/draft/journal mới. Mọi dòng cũ được so sánh toàn bộ giá trị, không chỉ COUNT. Row hash, tiền, shipping, SKU, trạng thái COD, outbox và quan hệ Inventory giữ nguyên. Columns/constraints/indexes/grants của các bảng cũ và schema/default privileges đều không đổi (`snapshot-verification.json`).

## Kết quả thực thi

| Nhóm | Kết quả | Bằng chứng |
|---|---|---|
| Bootstrap V1–V4 và seed hợp lệ | PASS | `before.json`, SQL dumps |
| Upgrade V4 → V5, đúng một migration | PASS | `upgrade_metrics.json`, `rehearsal.log` |
| Validate Flyway V1–V5; không checksum conflict | PASS | raw log, history `after.json` |
| Giữ nguyên history V1–V4 và dữ liệu/schema cũ | PASS | `checks.json`, `snapshot-verification.json` |
| Lock timeout + transactional DDL rollback | PASS, lỗi 55P03 được kỳ vọng | `lock_observations.json`, `postgres.log` |
| Regression trên DB đã upgrade | PASS | các check trong `checks.json` |
| Tổng lượt hoàn chỉnh | **35/35 checks PASS, 0 FAIL, 0 SKIP; exit 0** | `run.json`, `rehearsal.log` |
| Hai lượt harness trước | **FAIL**, exit 1 mỗi lượt | `attempt-01/`, `attempt-02/` |
| Bộ JUnit 329 lịch sử | NOT_RUN trong lượt này | Không cộng vào 35 checks |
| Năm live acceptance / Playwright E2E | **NOT_RUN — 0 executed** | Chưa có stack/fault evidence được owner cấp |

35 là số checkpoint của harness JDBC/Flyway/JPA, **không phải 35 JUnit test**, không tạo JUnit XML giả. Các đối chiếu snapshot/bytecode là kiểm tra bổ trợ, không cộng vào tổng. PostgreSQL chạy thật, không mock persistence. Không gửi Inventory HTTP, không HTTP fixture hoặc browser trong lượt này. `UNKNOWN` trong regression được dựng qua API journal trên DB thật, không phải bằng chứng timeout/claim-conflict của provider thật.

Regression thực tế: Hibernate validate entity; customer-scoped Order read và hai SellerOrder; collect COD/settle/double-settle guard với flush thật rồi rollback; durable draft commit nhìn thấy từ physical JDBC connection mới; reload giữ frozen facts; REQUIRED writer rollback không làm mất draft; cả claim entrypoints và generic caller giữ UNKNOWN; legacy UNKNOWN không backfill/reopen. Constraint probes chạy transaction rollback với SQLSTATE thật: FK/parent purge 23503, duplicate 23505, version/shape CHECK 23514, JSON parse 22P02, role access 42501. Mọi original row vẫn nguyên sau regression.

## Thời gian, lock và grants

- Upgrade thành công: **5 ms execution theo Flyway**, **47 ms wall time** cả migrate call. Đây là một mẫu nhỏ trên cluster mới, không suy thời gian production. `bootstrap_and_seed_ms` 2599 bao gồm cả lock probe, không phải riêng thời gian seed.
- Lock probe chủ động giữ `ACCESS EXCLUSIVE` trên `checkout_attempts` trong chính DB disposable. Quan sát V5 chờ **ShareRowExclusiveLock** tại FK target, `wait_event_type=Lock`, `granted=false`. Session `lock_timeout=1500ms` dẫn tới 55P03, toàn lượt probe ~1589 ms. DDL V5 rollback, history vẫn 4, bảng mới chưa tồn tại, không cần repair; thả lock rồi migrate bình thường PASS.
- Lượt upgrade bình thường chỉ đặt timeout cho session harness (lock 5 s, statement 20 s); không thay config database/global/production. Không benchmark khóa dưới tải hay đồng thời cùng application traffic.
- Có ba role mới: migrator sở hữu schema/table; runtime có DML legacy, không CREATE; auditor chỉ SELECT. **V5 không có GRANT và default privileges trống**: runtime tách biệt thực sự bị 42501 trên bảng mới. Sau khi ghi bằng chứng, harness cấp `SELECT, INSERT` bảng draft cho runtime và `SELECT` cho auditor **chỉ trong DB fixture**, rồi chạy regression PASS. Runtime không UPDATE draft; auditor không DELETE. Xem `fixture_v5_grants.sql`.
- Đây là điều kiện triển khai cần xác nhận, không phải cam kết tự thay grants production. Cấu hình hiện tại dùng một datasource cho app/Flyway; role production có thể là table owner, khi đó khác mô hình tách role này. Chưa kiểm tra grants thực tế production.

## Hai lượt FAIL được giữ nguyên

1. `attempt-01`: assertion của harness kỳ vọng direct `validate(target=5)` thành công khi V5 chưa áp dụng. Thực tế Flyway báo pending migration (được xác định đầy đủ trong `pending_v5_validation.json` của lượt sau: `RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED`), không phải checksum conflict. Harness đổi sang validate applied V1–V4 trước migrate, validate V1–V5 sau migrate; không ignore validation, không repair. Fixture B/C cũng được sửa qty/fingerprint để khớp các OrderItem trước các lượt sau. Lượt 1 không dùng làm bằng chứng fixture business-consistent cuối.
2. `attempt-02`: V5 upgrade/validate PASS nhưng repository regression gặp 42501 trên `orders`; seed connection đang autoCommit=false nên grants legacy chưa commit và bị rollback khi đóng connection. Sửa **harness ngoài repo** bằng commit grants fixture; không đổi source/migration hay tắt kiểm tra quyền. Lượt 3 container mới hoàn chỉnh PASS. `historical_failure_attempt02.json` là bản raw failure cũ, không phải lỗi của lượt thành công.

## Rủi ro và quyết định owner

| Owner | Đã chứng minh | Còn cần quyết định/kiểm chứng |
|---|---|---|
| TV4 + DBA/maintainer | V5 additive; FK/constraints/lock timeout rollback; RESERVE/CONSUME/RELEASE legacy rows không đổi | Cửa sổ DDL do FK lock; timeout trên target; parent purge/retention bị FK draft chặn; không thay CONSUME cutoff DISPATCHED |
| TV5 | Money/COD/outbox snapshots nguyên; entity settlement rollback trên PG thật | Review Finance regression scope; không coi là duyệt MoneyRules policy/COD tiền thật; PII retention/backup của draft; target role grants |
| TV1 | Không đụng Auth/Gateway/flags/write-fence records cũ | Quyền migrator/runtime thực tế, current-authority/write-fence khi recovery; Auth revoke đối đầu write thật chưa chạy |
| TV2/nhóm trưởng | SKU/quantity và fixture fake business-consistent | Product/Store authority, live fixtures/stock và stack/fault injection cho 5 acceptance; quy trình nhận T1/T2 sau #138 |

V5 chỉ tạo một bảng và PK/FK/CHECK, không backfill hay tự recovery attempt cũ. Không tự hash-verify nội dung payload tại DB: CHECK chỉ xác nhận JSON object/shape hash; CheckoutDraftJournal kiểm hash/binding khi đọc. Chưa chứng minh semantic JSON không hợp lệ, large dataset/large payload, HA/replication, backup restore, tải đồng thời, privileged tampering hoặc toàn bộ recovery. Không có down migration; không đề xuất DROP bảng có draft/PII để rollback.

Inventory vật lý/stock, Inventory service DB, Finance DB riêng, live JWT/revocation, Catalog thật và website E2E đều **NOT_RUN**. Không suy rằng toàn bộ Order/Inventory/Finance production đã an toàn chỉ từ rehearsal này.

## Thứ tự triển khai đề xuất để owner duyệt, chưa thực hiện

1. Giữ quyết định **#138 merge trước**, sau đó T1/T2 thành Draft PR riêng từ main mới. Không tích hợp Git ở lượt rehearsal.
2. Kiểm tra source sau tích hợp, số migration V5 có bị sử dụng đồng thời, hash V1–V4 và Flyway history của đúng target; nếu drift/conflict thì dừng, không repair/sửa migration đã áp dụng.
3. Owner xác nhận backup/restore, role migrator/runtime, tối thiểu SELECT+INSERT bảng draft và retention/PII/purge policy; cấp quyền qua quy trình được duyệt. Không đưa password vào repo.
4. Chọn maintenance window, kiểm tra long transactions/lock, cấu hình session timeout được duyệt; migrate schema trước rollout application cần V5. Validate history và smoke bằng tài khoản runtime thực tế. Khi timeout, điều tra blocker, không tự retry mạng hay sửa trạng thái UNKNOWN.
5. Chỉ rollout sau review TV4/TV5 và CI của commit tích hợp; nếu app phải rollback thì bảo toàn bảng/draft, tránh chạy old worker có semantics UNKNOWN chưa quarantine. Kế hoạch rollback/compatibility cần owner chốt.
6. Tiếp tục giữ 5 live acceptance NOT_RUN cho tới khi owner cấp môi trường riêng và fault evidence thật. Không tự bật gate để nghiệm thu.

## Lệnh và evidence

Lệnh trên Codex Terminal, từ `backend` của candidate (harness/init script ngoài repo):

```bash
source /workspace/.setup/java-proxy.sh
./gradlew --offline --init-script /workspace/review-evidence/tv3-v5-rehearsal-20261101/classpath.init.gradle :order-service:tv3RehearsalClasspath --no-daemon --console=plain
python /workspace/review-evidence/tv3-v5-rehearsal-20261101/run_rehearsal.py
```

Gradle classpath task exit 0; không chạy lại full unit/PostgreSQL suites trên worktree. Script Python ghi đầy đủ isolation/cleanup flow, gọi Java 21 source-mode với runtime classpath trong `runtime-classpath.txt`. Harness dùng Flyway target4 → seed → lock probe → target5 → validate → JPA/JDBC regression; dùng `pg_dump --schema-only/--data-only` trước, sau và cuối. Các lệnh docker/SQL thực thi nằm nguyên trong hai script, password chỉ truyền qua environment sinh trong bộ nhớ. Không chạy lại script nếu chỉ cần đọc evidence vì script sẽ tạo lượt DB mới.

- `before*`, `after*`, `final*`: schema SQL, data SQL và JDBC snapshot/history/row hashes; stderr dump rỗng nếu thành công.
- `rehearsal.log`, `postgres.log`, `orchestrator.log`: raw logs lượt hoàn chỉnh.
- `checks.json`, `upgrade_metrics.json`, `lock_observations.json`, `run.json`, `results.json`: kết quả máy đọc được.
- `pending_v5_validation.json`: expected pending trước migrate; `applied_v1_v4_validation.json`: validation applied migrations thành công.
- `attempt-01/`, `attempt-02/`: giữ nguyên raw logs, lỗi, harness từng lượt và snapshot đã đạt tới; không cộng PASS từ các lượt này vào tổng cuối.
- `class-source-verification.json`, `source-recompile.log`: kiểm tra code thực thi so với source bằng compile ngoài repo và so sánh javap; xem trạng thái thực tế trong JSON.
- Kiểm tra này PASS: javac exit 0, 61 source Order/security-common tạo 123 class; cả 123 khớp instruction/signature/constants (`javap -c -p -s -constants`) với class trên runtime classpath đã dùng. So sánh bỏ qua debug tables; không khẳng định class-file SHA byte-identical. Không phải lần chạy test thứ tư.
- `SHA256SUMS`: hash toàn bộ evidence (trừ chính manifest), gồm logs/dumps/scripts/report. Kiểm tra bằng `sha256sum -c SHA256SUMS` tại thư mục này.

Không push, merge, mở PR, comment/review, tick Issue, sửa source, bật flag hoặc truy cập DB thật. Rehearsal hoàn tất; chờ TV4/TV5/maintainer review theo các giới hạn trên.
