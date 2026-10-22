# TV5 — PR #63: xử lý review và checkpoint cục bộ, 20/10/2026

**CHECKPOINT TRƯỚC CÔNG BỐ** — người dùng đã cho phép commit/push và cập nhật GitHub sau lượt kiểm tra này. Refs #5, #12, #63, #64, #65.
Nhánh: `codex/tv5-unblock-handoff-20261020`. Head PR và local commit: `a1bce60d4a4223936dd96ef7e758faaa7334e4d8`.
Các bản sửa được soạn trên head này; trạng thái công bố thực tế được xác nhận tại PR #63 và Issue #5/#12. Không Approve/Merge hoặc bật Finance/pair flag.

## 1. Review mới nhất và yêu cầu chính xác

Đọc lại PR #63, reviews, inline comments, threads, commits, toàn bộ 15 path diff, CI và Issue #5/#12.
Snapshot PR #63 hoàn tất **2026-10-20T14:54:31Z**; status delta các PR liên quan lúc **14:55:26Z**.
Base API: main/03fb1b2; main được đọc riêng tại `4f12b05d0d6408deb42cb329676e643351106fcd`.
Đây là snapshot có thời điểm, không bảo đảm trạng thái tương lai.

PR #63 có **một CHANGES_REQUESTED**, của `lexuanbao258-collab`, tại đúng head trên:
[review 5580742783](https://github.com/duyxl56785/Project_HK5/pull/63#pullrequestreview-5580742783).
Không có inline comment hoặc review thread; 0 thread OPEN, 0 check-run.
`reviewDecision=CHANGES_REQUESTED`, `mergeable=true`, `mergeable_state=blocked`.
TV2 `duyxl56785` vẫn requested; TV1 đã review. Chưa re-request vì chưa push head mới.

**Finding duy nhất: P2 — current handoff đã lỗi thời.** Snapshot 45 PR/16 Issue được ghi rõ thời điểm nên giữ nguyên.
Nhưng U01/U02/U03/U22, báo cáo mục 3/7 và QA/checklist vẫn hướng dẫn review các PR đã merge hoặc đóng.
TV1 không đưa thêm finding source/security, không tranh chấp kết quả test lịch sử và không yêu cầu rerun PG cũ chỉ để sửa tài liệu.

| Phần yêu cầu | Đối chiếu thực tế | Phân loại / bản sửa cục bộ |
|---|---|---|
| Status delta và U01/U02/U03/U22 | #39/#61 MERGED; #60/#62 CLOSED WITHOUT MERGE | FIXED_VERIFIED cục bộ: receipt mới, mục 12, matrix hiện tại đã sửa; remote head vẫn OPEN finding |
| Next action và ownership | Không còn human review39/review60/61/62 task; C06/COD/current actor/Product/SKU/runtime vẫn riêng | FIXED_VERIFIED cục bộ: mục 7/12 và liên kết nổi bật QA/checklist/RTM; không reopen/request PR đóng |
| Provenance và thiếu source integration | Inventory lịch sử, original SHA/results/initial FAIL/GREEN giữ nguyên; không assume60/62 integrated | FIXED_VERIFIED cục bộ: hash và file-identity check; owner TV3/TV2 quyết định cần đưa lại phần nào |
| Chấp nhận bản sửa trên GitHub | Chưa commit/push, reviewer chưa có new head để kiểm | OPEN / WAITING_PUBLICATION, rồi WAITING_REVIEW TV1; không tự resolve/Approve |
| Follow-up #60/#62 | Chưa thấy replacement PR được owner xác nhận | NEEDS_OWNER TV3/TV2 nếu coverage/candidate còn cần; không tạo dependency merge63 ngoài yêu cầu docs của TV1 |

[Status delta và current actions](TV5_OWNER_UNBLOCK_HANDOFF_2026-10-20.md#12-post-snapshot-status-delta--current-actions)
liên kết [derived API receipt](evidence/tv5-unblock-20261020/post-snapshot-status.json).
#39 merged04:06:41Z, head4e4e623; #61 merged03:47:48Z, head0af4754.
#60 closede0a28de lúc04:11:57Z, #62 closed6a79016 lúc04:12:00Z, đều chưa merge; không suy lý do đóng.

Đối chiếu main: hai Order test file #60 tồn tại nhưng blob khác; audit #60 và cả ba path #62 trả404.
Đây chỉ chứng minh identity/absence tại path/SHA, không kết luận implementation tương đương không tồn tại nơi khác.
#65 là provider regression riêng, không thay caller coverage #60 hoặc Catalog candidate #62.
42 acceptance scenarios tài liệu #39 vẫn NOT_RUN; merge docs không phải COD/Finance acceptance.

## 2. Lệnh #64/#65 và kết quả thực thi mới

Lượt trước bị BLOCKED_TOOL_APPROVAL, chưa thực thi. Lượt này đã xin quyền qua cơ chế duyệt bình thường và được chạy;
không vượt bước duyệt. Dùng hai checkout detached riêng đã sạch; không đổi nhánh TV5 có thay đổi chưa commit.
Java21.0.7, Gradle8.10; JVM UTC/API1.44. Docker Engine sau khởi động xác minh29.8.1.
Không xóa volume/dữ liệu có sẵn; không sửa source/assertion.

```text
backend/gradlew.bat -p backend :inventory-service:test --rerun-tasks --no-daemon --console=plain
backend/gradlew.bat -p backend :inventory-service:postgresIntegrationTest --tests '*InventoryOperationPostgresIT' --rerun-tasks --no-daemon --console=plain
```

| Invocation / head | UTC start → end | Kết quả | Evidence |
|---|---|---|---|
| #64 unit /06ebfc423ede1bb07a2fa9b6804b21fe388991d7 |14:55:26→14:55:43|27 PASS,0FAIL,exit0|[Derived unit](evidence/tv5-unblock-20261020/pr64-unit-followup.json)|
| #65 PG initial /5bfbc15c328179f119f5e07552c67dc1cd6e9ebc |14:55:28→14:55:44|10 JUnit FAIL initialization records,exit1; business assertions BLOCKED|[Derived initial](evidence/tv5-unblock-20261020/pr65-pg-initial-followup.json)|
| #65 PG retry /same5bfbc15 |14:57:19→14:57:41|10 PASS,0FAIL,exit0|[Derived retry](evidence/tv5-unblock-20261020/pr65-pg-retry-followup.json)|

Initial #65: `Could not find a valid Docker environment`; kiểm Docker API pipe xác nhận Engine chưa chạy.
Đã xin quyền và khởi động Docker Desktop, chạy lại nguyên lệnh ở cùng SHA. Giữ log/XML FAIL ban đầu nguyên byte.
Không quy lỗi môi trường thành defect PR, không cộng counts giữa SHA/invocation thành release total.
#64 nay MERGED lúc08:55:11Z; kết quả trên là test exact head gốc, không phải test merge SHA/main.
#65 vẫn OPEN tại snapshot, CI readiness SUCCESS riêng; không cộng CI/owner counts vào TV5.

Raw logs/XML private tại `.verification/tv5-pr63-followup-20261020/runs/`;
public-ready JSON chỉ là derived summaries/case names/commands/timestamps/SHA256, không raw credentials/system-out.
Không có stale XML được tính vào ba invocation. Không sửa raw evidence để che FAIL.
Unit filter không chứng minh deployed path normalization/current actor. PG aggregate reserved balance không chứng minh
Order/SellerOrder reservation ownership, release authority, positive pair HTTP hoặc live E2E.
#65 unit riêng, #64 PG riêng và live E2E **NOT_RUN** trong lượt này; không cần chạy để sửa P2 docs #63.

## 3. File đã chuẩn bị và kế hoạch công bố được ủy quyền

Bốn file checkpoint đã đọc lại và cập nhật, bảo toàn nội dung có sẵn:

- Báo cáo này: finding matrix, SHA, test results, merge/publication gates.
- `docs/QA.md`: link current delta, tách historical/unit/PG/CI/E2E.
- `docs/TV5_W1_CHECKLIST.md`: superseding status/checklist có bằng chứng, chưa tick GitHub publication.
- `tests/TV5_TRACEABILITY.md`: finding → evidence/status/owner.

Bổ sung tối thiểu `reports/TV5_OWNER_UNBLOCK_HANDOFF_2026-10-20.md` theo finding TV1 và 4 derived JSON mới.
Không thay code/backend/frontend/docker, policy, schema, config, assertion hoặc historical inventory/raw assets.

Kế hoạch commit ghi trước khi thực thi:

1. `docs(tv5): refresh post-snapshot owner handoffs (Refs #5 #12 #63)`:
   owner report, QA/checklist/RTM, checkpoint và post-snapshot-status.json.
2. Evidence được giữ cùng commit1 nếu checkpoint dẫn tới ba summaries; chỉ tách `docs(qa)` khi mỗi commit có đầy đủ file được liên kết.

Không chia commit khiến relative links tham chiếu file chưa có. Chỉ stage danh sách file đã kiểm, không `git add .`.
Trước công bố phải kiểm lại remote head, reviews/removal events và nội dung Issue để tránh ghi đè thay đổi mới.
Sau checkpoint cục bộ, người dùng đã cho phép commit/push gói tài liệu/evidence, cập nhật PR #63/Issue #5/#12 và yêu cầu TV1 review lại một lần.
Quyền công bố không cho phép Approve/Merge, đổi policy hoặc sửa production ngoài ownership.
Trước commit đã đọc lại API và fetch đúng nhánh63: remote vẫn a1bce60, TV2 đang requested;
không có sự kiện gỡ reviewer TV1 có chủ đích. Sau push phải verify SHA/CI rồi re-request TV1 một lần;
không request lại TV2 đang có, không tự resolve review của TV1. Trạng thái dưới đây là snapshot trước công bố,
checklist công bố thực tế và receipt được cập nhật tại PR/Issue sau khi kiểm chứng thành công.

## 4. Điều kiện merge và người cần phản hồi

- P2 docs đã sửa cục bộ nhưng chưa xuất hiện tại head PR: đã được cho phép công bố; cần commit/push thành công và kiểm lại GitHub.
- TV1 cần review lại new head; CHANGES_REQUESTED hiện vẫn có hiệu lực. Không tự dismiss/Approve.
- Required rules đọc được: ít nhất1 approval, resolve threads; cấm force push/delete. Hiện0threads; không có check-run trên docs head.
- Kiểm lại CI/mergeability/review protection sau push; snapshot mergeable=true không tự đủ điều kiện merge.
- M01 **PENDING BUSINESS APPROVAL**, Finance production **BLOCKED**, flagsOFF. Chờ TV1/chủ dự án chỉ định business authority;
  TV3/TV4/TV1/TV2 chốt các payment/delivery/current actor/Product source/runtime gate thuộc ownership.
- Business/runtime gates không bị coi là điều kiện mới để merge riêng gói docs #63; cũng không được coi đã đạt khi docs merge.
- Issue #5/#12 tiếp tục OPEN/PARTIAL, không tự đóng hoặc ghi DONE.

## 5. Checklist tại snapshot trước công bố

- [x] Đọc CHANGES_REQUESTED mới nhất và toàn bộ review threads #63; finding P2 được xác định đúng.
- [x] Cập nhật current actions và post-snapshot delta; giữ historical inventory/FAIL/GREEN evidence.
- [x] Kiểm lại bốn file và đối chiếu source-path #60/#62 với main đúng SHA.
- [x] Chạy #64 unit và #65 PG qua cơ chế duyệt; giữ initial FAIL và retry PASS riêng.
- [ ] Commit/push/GitHub publication và re-request TV1: đã được cho phép sau snapshot này; chỉ tick trên GitHub khi có receipt xác minh.
- [ ] TV1 xác minh new head rồi cập nhật review; hiện remote finding vẫn OPEN.
- [ ] Business/runtime/Issue acceptance: chưa đủ DONE.


## 6. Kiểm tra tĩnh cục bộ

Lượt kiểm tra **2026-10-20T15:00:57Z**: 20 file trong gói diff/evidence, 15 API head blobs khớp local HEAD,
112 liên kết file, 5 liên kết section mới, 13 JSON parse hợp lệ, 47 raw artifact hashes khớp (bao gồm evidence lịch sử).
Historical evidence unchanged, production tree diff và `git diff --check` đều exit0.
Không phát hiện token/private-key marker trong các pattern đã kiểm; đây không phải secret audit toàn diện.
Các con số này là static/provenance checks riêng, không phải unit/PG PASS.
Bốn file checkpoint/QA/checklist/RTM sẵn sàng về kiểm tra nội dung cục bộ; cần commit nguyên gói có report/JSON được dẫn link.
