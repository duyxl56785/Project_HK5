# TV5 — QA matrix và evidence ba lỗi database PR #31

Refs #4/#5/#31. Người thực thi các lượt bên dưới: **TV5 Nguyễn Minh Duy**, 17/10/2026. Đây là bằng chứng mới, tách khỏi probes do TV2 cung cấp, unit15 của #35 và số liệu V2. Không tự Approve/Merge/Close hoặc resolve thread của reviewer khác.

## Source và phối hợp

- #35 đã được owner merge vào #31 qua `21d8450`; không làm lại compile fix. Nhánh riêng `codex/tv5-fix-pr31-database` bắt đầu đúng head đó.
- TV5 tái hiện schema defect và push candidate CHAR mapping `9ee299c`. Trong lúc thực hiện, owner đưa V2 migration + atomic inserts lên #31 tại `adc705e`. TV5 đồng bộ vào nhánh riêng qua `2a91759`, bỏ candidate CHAR mapping đã được thay thế, giữ nguyên toàn bộ source/migration của owner.
- Bản bổ sung tập trung regressions, sửa hai Mockito verify calls dùng matcher không nhất quán và tách unit/PG tasks. Không push branch TV4, force push, sửa V1, chốt contract hoặc thêm tính năng tuần 2–3.
- Source kiểm cuối `6c6cf8c`: `src/main` giống owner `adc705e`; các commit tiếp theo chỉ docs/evidence. Parent hiện Ready bởi owner, không đồng nghĩa được nghiệm thu.

## Lượt chạy thực tế, không cộng gộp

| Run | Source / phạm vi | PASS / FAIL | Exit |
|---|---|---|---:|
| schema-red | Original production mapping `21d8450` + schema harness | 0 PASS /1 FAIL: Hibernate bpchar/CHAR vs VARCHAR, validate giữ nguyên |1|
| schema-green | Candidate CHAR mapping `9ee299c` trước cập nhật owner |15 unit +1 PG PASS; historical candidate, không phải final V2 source |0|
| initialize-red | `9ee299c` chỉ gỡ schema blocker + regression mới |1 PASS /2 FAIL trong3 PG invocations |1|
| operation-red | Cùng old production + đúng regression file dùng cho GREEN |4 PASS /1 FAIL trong5 PG invocations; disjoint-SKU same-ID double commit |1|
| owner-unit-red | Owner follow-up service unit tests |4 PASS /2 FAIL: invalid mixed Mockito matchers, không phải DB failures |1|
| owner-full-green | Merged working tree trước commit source, sau matcher fix |16 unit +13 PG PASS,37s; last committed HEAD trong metadata không đại diện merged source |0|
| **final-green** | **Committed source `6c6cf8c`** |**16 unit +13 PG PASS**,0fail/error/skip,31s,8tasks |**0**|

Java21.0.7 / Gradle8.14.3 / Docker29.8.1 / PostgreSQL16-alpine. Process `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC -Dapi.version=1.44`. Final command: `:inventory-service:build :inventory-service:postgresIntegrationTest --rerun-tasks --no-daemon --continue --console=plain`. Module build only. Unit16 = validation3/domain7/service6; PG13 = owner3 + TV5 schema2/initialize3/operation5. Các con số là test invocations, không phải13 yêu cầu độc lập hay tổng release.

## Ma trận truy vết

| ID | Yêu cầu / defect | Regression và evidence final-green | Kết quả |
|---|---|---|---|
| INV-DB-01 | Schema hash phải khớp JPA với validate bật | SchemaPostgresIT: startup V1+V2, physical VARCHAR và hash JPA round-trip | PASS |
| INV-DB-02 | Nâng DB đã có V1 không mất dữ liệu/checksum | SchemaPostgresIT: V1→V2, hash/ledger/stock/version và V1 checksum giữ nguyên | PASS |
| INV-INIT-01 | Duplicate fresh SKU không được ghi đè | InitializePostgresIT: real HTTP201 rồi409; available/reserved/version/timestamp không đổi | PASS |
| INV-INIT-02 | Duplicate khi đã reserve không phá số dư | InitializePostgresIT: available3/reserved2 được giữ; HTTP409, version/time không đổi | PASS |
| INV-INIT-03 | Hai initialize đồng thời chỉ một thắng | InitializePostgresIT: writer giữ DB row/unique lock, request sau chờ lock thật rồi409 | PASS |
| INV-ID-01 | Disjoint SKUs, cùng operationId/different payload không cùng commit | OperationPostgresIT: dừng sau SELECT thật thấy rỗng, B commit, A resume; A unchanged/B8/hash winner giữ | PASS |
| INV-ID-02 | Concurrent same-key/same-payload chỉ áp dụng một lần | OperationPostgresIT: second waits real row lock, response replay=true, stock chỉ giảm1 | PASS |
| INV-ID-03 | Concurrent same-key/different-payload bị từ chối | OperationPostgresIT: second waits row lock, IDEMPOTENCY_KEY_REUSED; một ledger | PASS |
| INV-TX-01 | Lỗi SKU sau phải rollback SKU trước và ledger | OperationPostgresIT: stock+reserved và operation claim rollback khi insufficient stock | PASS |
| INV-STOCK-01 | Hai operationId không oversell cùng SKU | OperationPostgresIT: reserve7 từ10, request sau chờ lock rồi insufficient; balance3/reserved7, losing claim rollback | PASS |
| INV-AUTH-01 | Shared identity/service-pair/role/Store authorization | Regression HTTP ở trên chỉ dùng fixture internal token hợp lệ, không xác minh production auth | NOT_RUN / contract BLOCKED |
| INV-E2E-01 | Live Auth/Product/Checkout/Inventory/recovery | Chưa được giao triển khai adapter/flow; không suy từ mock/unit/module PG | NOT_RUN |

Ba test PG đã được owner viết cũng thực sự chạy,0skip. Hook AOP chỉ điều phối sau repository SELECT thật, không trả dữ liệu giả; các race khác quan sát `pg_stat_activity` trước khi release writer. Test initialize/operation RED và GREEN có source bytes giống hệt nhau. Schema expectation đổi có chủ đích từ candidate CHAR sang migration V2 VARCHAR của owner, không tắt validation hay xóa assertion.

## Bằng chứng và checklist

[TEST_RESULTS](../backend/inventory-service/TEST_RESULTS_TV5_DB_FIXES.md), [raw log/XML/run metadata/manifest](../backend/inventory-service/verification/tv5-db-fixes/). Raw FAIL/PASS giữ nguyên byte, có scoped Git attributes; SHA256 và Git blob bytes được kiểm trước bàn giao. Docker inventory sau tất cả runs: **0 missing prior containers/volumes**. Chỉ dùng database test mới; không xóa volume/data có sẵn.

Source/docs diff-check (Java/Gradle/SQL/Markdown/attributes) PASS. Lệnh diff-check không giới hạn file có cảnh báo CRLF/trailing whitespace của generated raw manifest/evidence; giữ nguyên byte và kiểm SHA256/blob, không sửa log để làm sạch cảnh báo hoặc ghi toàn bộ diff sạch sai sự thật.

- [x] Xác nhận #35 đã được tích hợp và đọc source/reviews mới nhất #31.
- [x] TV5 tự tái hiện đủ3 DB defects, giữ RED evidence riêng từng nhóm.
- [x] Bảo toàn source owner đã sửa song song; V1 nguyên trạng, forward V2 được kiểm nâng dữ liệu.
- [x] Unit/PG/regression GREEN trên source đã commit; giữ nguyên assertions, sửa đúng Mockito matcher misuse.
- [x] Commit/push riêng các nhóm schema/sync, initialize, operation race; giữ Docker data và bảng QA/evidence thực tế.
- [ ] Owner review/tiếp nhận supplemental tests/matcher fix và xác nhận lại parent head/CI.
- [ ] TV1/TV3/TV4 xác nhận auth/service-pair, Product↔SKU/Store, operation identity và lifecycle/recovery contracts.
- [ ] Toàn gate nghiệm thu Issue #5 đạt. **OPEN/PARTIAL — WAITING_REVIEW/INTEGRATION**, chưa DONE.
