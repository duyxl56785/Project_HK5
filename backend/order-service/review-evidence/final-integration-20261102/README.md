# TV3 — final integration P1 / main sync, 02-11-2026

**FIXED_VERIFIED trong phạm vi source và regression local.** TV2 đã sửa root P1 tại #147 `cb576a6894d88304097862dc2ee7c25373903084` (mới hơn HEAD `a1f2409` được giao). TV3 không áp lại patch cũ dùng Rejected.productId. Bản mới giữ Rejected.sku và association từ Reservation đã được Checkout/Catalog xác minh.

## Mốc đã kiểm thử

- Main mới nhất: `b2dd17ed4d1b4f5ebc536649dc08eb30fae4902a`; main ban đầu `e9b98c7`, sau đó #140 merge trong lúc chạy.
- #145 gốc: `9dbd7815299960c5b3474977c8bed7f5c54ff8c5`.
- #147: `cb576a6894d88304097862dc2ee7c25373903084`.
- Candidate local cả ba nguồn + hardening: `ee8c59ca4dc65c385bfe13f4b94fc7ea190fae10`, tree **`29772fac0b944f9943f2838745bced98f9a73901`**, worktree `/workspace/tv3-final-main-145-147`. Candidate commit không push.
- Nhánh #145 đã merge thường main qua `98f16a8` rồi `28f487ccb511b8e873e3de287f8a64d499cc5b80`, tree **`1f5ae8a6dea64305cc1cde8c55a56eb649fa6937`**. Nhánh này chứa main +145; source147 vẫn do TV2 tiếp nhận riêng. Evidence commit tiếp theo chỉ thêm artifact, không thay source đã test.

## Thay đổi thực tế

[p1-latest-owner-contract.patch](p1-latest-owner-contract.patch) là delta đúng3 file trên cây main+145+147 hiện hành:

1. InventoryHttpAdapter: buộc tuple SKU/quantity và attempt của Reservation đã xác minh khớp journal bất biến trước HTTP; chỉ đọc SKU dạng chuỗi thực sự từ error. Không dùng chữ hiển thị "an item" hoặc number coercion làm identity.
2. CheckoutService: OUT_OF_STOCK chỉ khi INSUFFICIENT_STOCK và đúng1 dòng Reservation đã xác minh khớp SKU. Không nhận Product ID từ provider hoặc replay. Không đổi DTO.
3. CheckoutInventoryBoundaryPostgresIT: regression thiếu kho đúng Product, non-stock INVALID_QUANTITY/INVALID_SKU, SKU lạ/thiếu/sai kiểu, forged provider/replay Product ID; xác minh draft và IN_FLIGHT T1 đã commit trước HTTP.

P1 compile của owner cb576 đã được xác minh compile exit0 trước hardening. Khi merge145+147 có1 conflict do PreparedOrder record/helper problem được thêm cùng vị trí; giữ CẢ HAI, không chọn ours/theirs. Draft commit trước HTTP, frozen IDs/money/shipping/outbox, T1 immutable operationId/SKU/quantity/fingerprint, PENDING atomic claim, T2 generation fencing, UNKNOWN quarantine và successful shipping replay guard giữ nguyên. CONSUME/RELEASE không đổi semantics; không auto retry UNKNOWN/new ID/blind RELEASE. overallStatus, itemized refusal và DELETE Cart được giữ.

## Kết quả thực chạy — CODEX TERMINAL Linux

| Phạm vi | Unit PASS | PostgreSQL PASS | FAIL/ERROR/SKIP |
| --- | ---: | ---: | --- |
| Cây tích hợp: Order |171|113|0/0/0|
| Cây tích hợp: Cart |13|13|0/0/0|
| Cây tích hợp: Inventory |27|19|0/0/0|
| Tổng focused (nằm trong full build) |211|145|0/0/0|
| Probe Cart ownership/claim/concurrency riêng |—|4|0/0/0|
| Nhánh145 sau đồng bộ: Order |163|105|0/0/0|
| Nhánh145 sau đồng bộ: Cart |11|13|0/0/0|
| Nhánh145 sau đồng bộ: Inventory |27|19|0/0/0|

**Full backend cây tích hợp:1688 PASS,0 FAIL/ERROR,16 SKIP, exit0.** Focused356 đã nằm trong số này, không cộng lại.16 SKIP giữ nguyên source gates:15 cần TV1_DATABASE_TESTS/provisioning và1 cần AUTH_EMAIL_INVENTORY_JDBC_URL. Không tự bật test DB vào database chung để ép xanh.

Lượt full e9b98c7 dùng --rerun-tasks:1678PASS/16SKIP, exit0. Sau main140, Product source/migration/frontend thay đổi; chạy lại Product tests + build:1688PASS/16SKIP theo final reports, exit0 (9 tasks executed,76 up-to-date). Order/Cart/Inventory source và input kiểm thử không đổi bởi #140 nên giữ report cuối mỗi task đúng1 lần. Nhánh145 chạy lại đủ6 tasks riêng sau sync:338PASS,0FAIL/SKIP, exit0. Bốn probe PG ngoài repository (không mock DB) kiểm riêng Customer isolation/orphan deletion, claimed Cart rollback, missing/repeated clear và quan sát pg_blocking_pids khi clear phải chờ claim thật. Probe không cộng vào full build.

Frontend typecheck cả candidate và nhánh145 sau main140 đều exit0. Java21.0.8+9, Gradle8.10, Docker28.4.0; PostgreSQL Testcontainers16.15/16.6. Unit có mocks; Checkout boundary dùng HTTP peer fixture + real Order PG/journal/draft + mocked Cart. Không gọi đây là live Inventory/Gateway/browser E2E. Kafka Notification tests dùng Kafka/PG disposable thực; cảnh báo Kafka/datasource shutdown được giữ raw local.

## Lệnh và evidence

Working directory candidate `/workspace/tv3-final-main-145-147/backend`:

```bash
source /workspace/.setup/java-proxy.sh
env -u DOCKER_HOST -u DOCKER_CONTEXT -u DOCKER_TLS -u DOCKER_TLS_VERIFY -u DOCKER_CERT_PATH DOCKER_HOST=unix:///var/run/docker.sock ./gradlew --init-script /tmp/tv3-final-integration-20261102/build-output.init.gradle :order-service:test :order-service:postgresIntegrationTest :cart-service:test :cart-service:postgresIntegrationTest :inventory-service:test :inventory-service:postgresIntegrationTest build --rerun-tasks --continue --no-parallel --max-workers=1 --no-daemon --console=plain
# Sau merge140, cùng thư mục/env/init, chạy:
./gradlew --init-script /tmp/tv3-final-integration-20261102/build-output.init.gradle :product-service:test :product-service:integrationTest build --continue --no-parallel --max-workers=1 --no-daemon --console=plain
```

Cả hai exit0. Lệnh nhánh145 và probe riêng đầy đủ trong remaining-commands.json, mỗi lệnh exit0. `npm run typecheck` từ frontend tương ứng exit0. Init chỉ chuyển output vào /tmp và probe init thêm test source ngoài repo; không thay source/runtime flags/assertions để đạt PASS. HBA file non-secret của worktree test được giữ nội dung và phục hồi mode0644 để bản copy PostgreSQL đọc được, không sửa Auth.

Summary JSON + JUnit sanitized giữ toàn bộ testcase/result, chỉ bỏ stdout/stderr/properties/hostname. Raw logs/XML không sửa, nằm tại `/tmp/tv3-final-integration-20261102`, hash trong raw-input-sha256.json. Task extracts là bản trích có nhãn; không giả là raw log đầy đủ. SHA256SUMS kiểm artifact công khai. Không copy credential, shipping, raw draft/outbox payload hoặc DB dump vào gói.

## Bàn giao owner

TV2/maintainer chọn base/thứ tự merge được duyệt. Patch áp dụng trên source đã kết hợp main+145+147, không áp thẳng vào147 standalone chưa có journal. Bước tối thiểu: giữ PreparedOrder + problem helper, `git apply --check` patch rồi áp dụng trong worktree owner, chạy lại regression/CI exact HEAD cuối. TV3 bàn giao bản đã test; TV4 cần xác nhận definitive no-movement errors và reservation ownership/CONSUME/RELEASE. Không sửa branchTV2, không merge PR, không gỡ REQUEST_CHANGES/resolve thread thay reviewer.

Gate còn lại: owner tiếp nhận patch/merge order; fresh hosted CI ở source nhận patch; TV4 Inventory authority/ownership; TV2+TV5/DB owner retention/grants/backups/purge cho V5. TV3 đã chọn giữ immutable successful draft cho DEMO tạm thời, không tự thay sang production TTL/purge. Hai UNKNOWN live cần fault fixture/receipt thật TV4+TV5; giữ **3 livePASS lịch sử/2UNKNOWN NOT_RUN-BLOCKED**, không chạy lại hoặc tráo bằng unit/HTTP fixture.15 container demo, volumes và worktree/patch cũ được giữ nguyên. Không tuyên bố Checkout/UAT production DONE.
