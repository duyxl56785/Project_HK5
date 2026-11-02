# TV3 — P1 đã được TV2 tích hợp, kiểm chứng exact owner HEAD

**FIXED_VERIFIED cho source/regression; BLOCKED_OWNER cho acceptance local cuối.** Lượt cập nhật này thay trạng thái “WAITING_OWNER_ACCEPTANCE” của patch production trong snapshot trước. Không cần gửi lại hoặc áp lại patch cũ.

- Main: `d446577d2f9268f2212a4ee214ff3d805ee7a1d9`.
- PR #147: **`44426859cbcdcd6dd9ba0d5ce6f84f91a317256d`**, tree **`20ca9d7212bc6d03e43cc9e14c42f654b7b8c728`**.
- TV2 merge #145 `81ad9f7` tại `ec91506`, sau đó commit `4442685` tiếp nhận hardening ba file của TV3. Chỉ fetch/read branch TV2, không push/sửa/merge branch đó.
- Worktree detached mới: `/workspace/tv3-owner-147-44426859`, sạch trước/sau; source không chỉnh sửa.
- Candidate TV3 `351fb032bf2c6931a6ce5720940aa3d7169c5e80` và mọi worktree/refs cũ vẫn được giữ. So với candidate này, owner source chỉ khác vị trí khai báo cùng private record `PreparedOrder`; bỏ đúng một dòng khai báo ở cả hai thì CheckoutService khớp byte. Adapter và regression boundary khớp byte hoàn toàn. `source-proof.json` ghi phép đối chiếu thực tế.

## Regression chạy mới trên exact HEAD #147

CODEX TERMINAL — Linux, Java21.0.8+9, Gradle8.10, Docker28.4.0. Lệnh đầy đủ/cwd/exit trong `commands.json`; build output riêng trong `/tmp/tv3-owner-147-44426859-20261102`, không ghi đè JAR/stack demo. Không dùng lại report cũ để gán kết quả cho HEAD mới.

Raw reports xác nhận PostgreSQL16.15/16.6 thật qua Testcontainers. Unit có mocks; Checkout boundary là PostgreSQL + HTTP peer fixture, không phải Inventory/Gateway/browser live.

| Module | Unit PASS | PostgreSQL PASS | FAIL / ERROR / SKIP |
|---|---:|---:|---|
| Order | 171 | 113 | 0 / 0 / 0 |
| Cart | 13 | 13 | 0 / 0 / 0 |
| Inventory | 27 | 19 | 0 / 0 / 0 |

**356 focused PASS đã nằm trong full backend 1.725 PASS / 0 FAIL / 0 ERROR / 16 SKIP, exit0, 88/88 tasks executed.** Không cộng hai lượt lặp thành tổng test mới. 16 SKIP vẫn do provisioning gates TV1/AUTH_EMAIL_INVENTORY_JDBC_URL; không coi SKIP là PASS. Frontend typecheck exact owner HEAD: exit0.

Giữ bằng chứng ba JVM halt `AFTER_DRAFT`, `AFTER_T1`, `AFTER_RESERVE` trong lượt PG mới. Draft/T1 được commit trước HTTP; immutable IDs/SKU/quantity/shipping/money/outbox, atomic PENDING claim, T2 generation fencing và UNKNOWN quarantine đều được kiểm bởi các suite như matrix ở [handoff trước](../README.md). Hai source production P1 và test boundary đã được owner tiếp nhận; không còn delta P1 chờ apply.

V1–V5 khớp byte candidate và #145; không sửa migration hay tạo version mới. Fresh PG chạy Flyway/schema validation. V4→V5 rehearsal lịch sử35/35 vẫn giữ tại [rehearsal report](../../t1t2-20261101/migration-rehearsal/REPORT.md); đây không phải rehearsal mới trên DB thật. Chỉ phục hồi mode0644 cho HBA non-secret trong worktree test mới (checkout tạo0600); nội dung không đổi, không chỉnh cấu hình Auth/shared/production. Chi tiết `test-file-permission.json`.

Bốn Cart ownership/concurrency PG probes và tám assertion-unit của negative contract có trong evidence parent, không chạy lại/cộng lại vì source tương ứng không đổi. Tám assertion-unit/6 discovery thuộc test update mới trên nhánh TV3 #145, chưa có trong owner #147 cho đến khi owner đồng bộ test đó. Không gọi chúng là browser/Gateway live.

## GitHub CI và giới hạn live

Public checks của #147 tại exact `44426859` xác nhận:

- [Backend readiness — SUCCESS](https://github.com/duyxl56785/Project_HK5/actions/runs/41453659081).
- [Frontend and E2E — SUCCESS](https://github.com/duyxl56785/Project_HK5/actions/runs/41453659331).

Đây là GitHub execution, không phải số test local. Workflow E2E của repo khởi động stack disposable thật và chạy Playwright standard, có order journey/Cart refusal. Không suy ra bộ năm acceptance riêng, exact two-Store stock deltas hoặc hai fault UNKNOWN đã được thực thi từ trạng thái workflow SUCCESS. Không có raw per-case CI counts trong bằng chứng local này; `pr147-ci-readback.json` chỉ xác nhận checks state/HEAD/run URLs.

User đã xác nhận **chưa có phê duyệt stack local mới** cho source tích hợp. Vì vậy cả năm acceptance riêng giữ NOT_RUN/BLOCKED ở HEAD mới: two-Store/replay, cancel/reject RELEASE, DISPATCHED CONSUME, claim-conflict UNKNOWN, timeout UNKNOWN. Sáu negative Gateway/API và browser Order History cũng NOT_RUN/BLOCKED cho source này. **0 local live executed, 0 local live PASS/FAIL.** Lịch sử ba PASS/hai UNKNOWN BLOCKED và sáu negative legacy PASS giữ nguyên theo HEAD cũ.

Không khởi động demo, bật flags, dùng production/shared DB, tạo fault giả hoặc sửa SQL. 15 container demo cũ và 16 volume có sẵn giữ nguyên; mount comparison chuẩn hóa thứ tự, không thay source/secret/env của stack. `safety-final.json` có kiểm tra worktree/ref/container sau toàn bộ regression.

## Quyết định còn lại

| Owner | Trạng thái / bước tiếp theo |
|---|---|
| TV2 / maintainer | **P1 production đã tích hợp.** Chọn quy trình merge #145/#147 và review cuối; cấp project/ports/HEAD/fixtures cho stack local tách biệt. Không tự merge hoặc resolve REQUEST_CHANGES/thread. |
| TV4 | Xác nhận definitive no-movement errors, operation ownership/authority, UNKNOWN reconciliation và fault fixtures/receipts thật; RELEASE/CONSUME không bị TV3 thay semantics. |
| TV5 + TV2 / DB owner | Retention/PII của successful drafts, grants/backup/purge/runbook production và UAT; cung cấp fault evidence cho hai UNKNOWN. Không tự thêm TTL hoặc retry/release. |
| TV1 | Current-authority/revoke đối đầu business write thật và provisioning các gated tests; Gateway/Auth không bị sửa trong lượt này. |

Không có source fix mới cần đưa cho TV2 sau `44426859`. TV3 chỉ publish cập nhật test acceptance và evidence vào #145; không mở PR mới hoặc approve/merge thay owner. Không tuyên bố Checkout/website/Finance production DONE.

## Bằng chứng

Raw log/XML local bất biến tại `/tmp/tv3-owner-147-44426859-20261102/`; `raw-input-sha256.json` ghi hash. Public XML bỏ stdout/stderr/properties, che synthetic shipping/email/phone. `backend-tasks.log` là task/result extract; raw warning/shutdown stack traces vẫn giữ local. `SHA256SUMS` kiểm các artifact public. Không publish env, password, token, JWT, DB dump hoặc shipping PII.

Refs #3 #10 #16 #145 #147.
