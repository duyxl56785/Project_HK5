# Kiểm thử và bằng chứng

Main đã nhận rules/unit tests Review/Finance qua PR #20 và Category/Seller domain qua PR #7. API/persistence/JWT production của F15/F16 chưa được chứng minh hoàn tất. Mỗi thành viên tự chạy test liên quan và báo cáo:

| Hạng mục | Test command | PASS | FAIL | NOT_RUN | Bằng chứng |
|---|---|---|---|---|---|
| Ví dụ Auth | gradle :auth-service:test | - | - | chưa chạy | link PR/log |

Không suy số test của báo cáo V2 thành kết quả của repository mới khi chưa import code và chạy lại.

Một Issue được coi DONE khi: có PR đã merge, kiểm thử có bằng chứng, API/schema/event được kiểm tra và tài liệu được cập nhật.

## Baseline TV5 — 15/10/2026

- [Báo cáo thực thi và review PR](../reports/TV5_W1_AUDIT_2026-10-15.md).
- [Ma trận QA, UAT hai Store và checklist báo cáo](../tests/TV5_UAT.md).
- [Đề xuất contract/schema Review/Finance cần owner review](TV5_CONTRACT_PROPOSAL.md).

Rules Review/Finance có unit tests; chưa có API/persistence/JWT adapter production. Không dùng kết quả unit để tích hoàn thành F15/F16 hoặc E2E. Các con số test lịch sử ở PR #7 phải phân biệt với lượt chạy lại trong báo cáo TV5.

## Follow-up tuần 1

- [Đối chiếu báo cáo V2 theo chương/trang](../reports/TV5_V2_RECONCILIATION.md).
- [Truy vết yêu cầu → test → bằng chứng](../tests/TV5_TRACEABILITY.md).
- [Bảng lỗi và blocker](../reports/TV5_W1_DEFECTS.md).
- [Quyết định cần TV1–TV4 xác nhận](TV5_W1_DECISIONS.md).
- [Kết quả chạy follow-up và phân loại bằng chứng](../reports/TV5_W1_FOLLOWUP.md).

PASS: đã chạy đạt; FAIL: đã chạy lỗi; NOT_RUN: chưa thực thi; BLOCKED: chưa thể chuẩn bị/chốt ca do phụ thuộc được ghi rõ. Không cộng test của các mốc khác nhau, unit với E2E hoặc claim báo cáo V2 vào cùng tổng hiện tại. Việc thiết kế UAT/contract trong W1 không đồng nghĩa đã triển khai/chạy luồng tuần 2–3.

## Kiểm chứng điều kiện tuần 1 — 17/10/2026

- [QA matrix, PR heads, owner/blocker và ledger nguồn kết quả](../reports/TV5_W1_ACCEPTANCE_2026-10-17.md).
- [Raw log/XML, suite counts và SHA256](../reports/evidence/w1-gates-20261017/README.md).
- TV5 chạy mới: Review/Finance63 unit PASS; Seller21 PostgreSQL PASS; Cart/Order3 PostgreSQL PASS; Auth/User timezone Asia/Saigon1 PASS/2 FAIL, rerun UTC3 PASS. Không cộng các lượt hoặc xóa FAIL lịch sử.
- Issue #5 vẫn PARTIAL: test DB Seller/Cart/Order không chứng minh DB Review/settlement, mapping JWT runtime hoặc shared Finance contract đã nghiệm thu.

## Bản sửa bổ sung theo finding — 17/10/2026

- [PR #32](https://github.com/duyxl56785/Project_HK5/pull/32) → branch PR #19: sửa HS256/JWKS và checkpoint PR #7; giữ F15/F16. TV1 đã Approve đúng `0c77df6`; còn chờ tác giả tiếp nhận vào #19.
- [PR #33](https://github.com/duyxl56785/Project_HK5/pull/33) → branch PR #28: JWT YAML mapping, replay/customer scope và scanner/failure-handler CAS; final build Cart/Order **56 unit + 11 PostgreSQL PASS** tại `11dcf39`. RED/GREEN logs, test source và manifest nằm trong `backend/order-service/verification/tv5-fixes` của PR bổ sung. Không cộng với 63 rules/21 Seller/3 PG của lượt bàn giao trước.
- [PR #34](https://github.com/duyxl56785/Project_HK5/pull/34) → branch PR #27: owner đã sửa runbook ở `a810312`; TV5 giữ phần đó và bổ sung evidence cùng source Java: Asia/Saigon 1 PASS/2 FAIL, UTC 3 PASS, Asia/Ho_Chi_Minh 3 PASS. Không che lỗi của alias không được server hỗ trợ.
- Ba PR là patch/evidence bổ sung, không đưa source PR gốc chưa duyệt vào main. [Ledger bàn giao và owner](../reports/TV5_W1_ACCEPTANCE_2026-10-17.md#7-bản-sửa-bổ-sung-cho-các-finding) giữ WAITING_REVIEW/INTEGRATION; contract Review/Finance, live dependencies và E2E vẫn chưa được nghiệm thu.

### Checkpoint sau khi tác giả tiếp nhận patch

[Audit toàn bộ PR/reviewer](../reports/PR_REVIEWERS_AUDIT_2026-10-17.md): #32 đã vào #19 (`804d6f1`), #33 đã vào #28 (`89c20bc`), Git tree giống bản sửa đã kiểm chứng. Chờ reviewer quyết định lại tại parent, không coi review cũ tự được khép. #31 vẫn Draft và FAIL compileTestJava tại `1abfd4f`; bản sửa một dòng với **15 unit PASS** được bàn giao riêng qua #35 (`14df61b`). Đây là kết quả mới của TV5, PG/HTTP/E2E Inventory vẫn NOT_RUN. #27 có 2 CI readiness checks success đúng `a810312`, tách khỏi lượt chạy local TV5.

Phản hồi mới trong lúc audit: TV1 đã APPROVED #34 đúng `369b4f8`. TV2 xác nhận fixes #26/#27/#28, đồng thời báo **3 PostgreSQL probes FAIL ở #31** (schema CHAR/VARCHAR, duplicate initialize, concurrent operationId ledger). Đây là kết quả do TV2 cung cấp; TV5 chưa chạy lại và #35 không xử lý các DB defects đó. Xem audit §6 để biết owner/evidence; giữ #31 Draft và Issue #5 PARTIAL.

## Checkpoint 18/10/2026 — audit PR sau tích hợp

Các đoạn trên là snapshot lịch sử. #31/#36 và các PR QA/infrastructure trước đã merge; không còn báo ba DB findings cũ như source chưa sửa. [Audit hiện tại](../reports/PR_REVIEWERS_AUDIT_2026-10-18.md) kiểm5 PR mở #19/#28/#37/#38/#39: TV5 chạy mới #28 **61unit PASS**, #37 **291unit+49PG PASS** (11PG candidate SQL), #38 **17unit+15PG PASS**. Các lượt/source khác nhau, không cộng vào release total; runtime #19/#39 NOT_RUN/docs-only.

#39 cần sửa số19/21 và ID baseline bị đổi nghĩa; #38 cần rõ retryable errors/current replay balances. M01 money/Cart currency, S01 Store authority, C01/E01 Catalog và fulfillment state/auth/event còn owner-gated. [Raw evidence và publication limits](../reports/evidence/pr-audit-20261018/README.md) tách49 public hashes với3 credential-bearing XML giữ local/derived summary. Issue #5 vẫn PARTIAL; không suy unit/PG thành F15/F16 production hoặc live2Store E2E PASS.

## TV5 — Finance contract / current-head checkpoint 18/10/2026

- [Sáu contract, owner/signature/module matrix và M01 candidate TV5-FIN-v0.1](TV5_FINANCE_CONTRACT_SIGNOFF.md):
  S01/M01/Inventory/Security PENDING, C01/E01 và C06 REVISE; không có cross-owner APPROVED.
- [Lượt chạy mới TV5 và QA evidence](../reports/TV5_FINANCE_QA_2026-10-18.md): parent28 bd5ff09
  61unit+11PG PASS; branch bổ sung44f2838 72unit+13PG PASS,0fail/error/skip. Hai lượt riêng, không cộng.
- FIN-01..05 ghi rõ giới hạn precision/snapshots/settlement/Store authority/rounding. Test cấu hình dùng
  fixture policy không bật cờ deploy. Live UAT2Store/COD/settlement/Store query NOT_RUN/BLOCKED.

## TV5 — hậu tích hợp PR41, Finance persistence/replay regression

[Checkpoint mới](../reports/TV5_FINANCE_FOLLOWUP_2026-10-18.md): PR41 đã được owner merge vào
PR28 tại55bf5ff; chạy mới72unit+13PG PASS. Thêm1unit+4PG đạt73unit+17PG PASS tại sourcecd2c0be,
0fail/error/skip, không cộng hai lượt. FIN-06: parent aggregate vượt NUMERIC(19,2) dù từng Store hợp lệ;
PG rollback thật, nhưng validation trước RESERVE cần TV3/TV5 thống nhất. Replay giữ tiền/phí/event cũ.
S01/M01/Inventory/Security/Finance vẫn PENDING; C01/E01/C06 REVISE. Live settlement/Store API/E2E
NOT_RUN/BLOCKED; giữ policyfalse và các adapter fail-closed, không coi merge41 là contract approval.

## TV5 — FIN-06 Checkout diagnostic RED tại head28dd0cf

[Báo cáo và raw evidence mới](../reports/TV5_FIN06_CHECKOUT_RED_2026-10-18.md): PR42 đã tích hợp;
QA union từ main giữ nguyên byte và15relative linksPASS. Chạy task diagnostic riêng trên Checkout thật/PG:
**1PASS/2FAIL**,0error/skip,exit1. FIN-06 gọi reserve/write trước DB reject; FIN-01 first total3.002→replay3.00.
Không sửa assertion/production hoặc ghi thành E2E. Unit Catalog thay đổi tại63668b8 **50PASS** riêng.
Normal build không chạy diagnostic này; CI xanh không khép finding. Fix chờ TV3+applicable sign-off;
M01 và các contract chưa đủ chữ ký giữ PENDING/REVISE. [Cách chạy RED task](../tests/finance/README.md).

## TV5 — M01 human decision / owner utility checkpoint

[Gói quyết định MD01–MD10](TV5_M01_HUMAN_DECISION_PACKET.md) ghi lựa chọn, ví dụ số, schema/consumer
và signer cho TV5-FIN-v0.1; vẫn PENDING, không tự ký VND/scale0/bounds/freshness. [Lượt mới](../reports/TV5_MONEY_DECISION_CHECKPOINT_2026-10-18.md)
tại sourcec4d517e có6utility unitPASS bằng JUnit thật và diagnostic1PASS/2FAIL nguyênassertions,overallexit1.
Utility owneree15851 chưa được wire vàoCheckout. Docs38/39 đã sửa traceability/error semantics,21baseline+
21supplemental đềuNOT_RUN. RTM phân biệt staticchecks, PG/mock và liveE2E, không cộng kết quả các snapshot.

## TV5 — M01 authority và latest Finance preflight QA,18/10/2026

[PR44 / báo cáo source-specific](../reports/TV5_FINANCE_ACCEPTANCE_2026-10-18.md) công bố proposal/MD01–MD10 và sửa mô tả quantity thực tế1–1000.
TV5 chỉ tổng hợp/đề xuất; business approver **chờ TV1/chủ dự án xác nhận**. C01/E01 own-scope TV2/TV3 được ghi nhận, overall REVISE;
M01/S01/Security/Inventory/Finance vẫn PENDING. Không bật policy hoặc sửa owner runtime.

Source21f6d53: Cart6unit+1PG,Order73unit+16PG PASS; diagnostic1PASS/2FAIL,exit1.
Sau owner c97a656,source de1f5c6: Order75unit+16PG PASS,diagnostic1PASS/2FAIL,exit1; positive control còn PASS.
Sau owner5ce0630+4test TV5,source1311f8a: Cart8unit,Order81unit,Cart1PG PASS,exit0/27s. Không cộng các snapshots.
Order production/PG/diagnostic không đổi de1f5c6→1311f8a nên không chạy lại chúng trong lượt cuối; runtime findings vẫn RED, không nhận là fixPASS.

F16-16..19 bổ sung gate không reserve/write, inconsistent House/Seller trong cùng Store và actual per-Store rounding bằng mocked ports.
QA union mới với main: TV5 xác nhận giữ toàn bộ lịch sử main +hai checkpoint Finance18/10/provenance; chưa merge/resolve thay owner.
Settlement atomic/unique, Store Finance API, COD authority và live E2E tiếp tục NOT_RUN/BLOCKED. Issue5/12 OPEN/PARTIAL.

### Sau khi owner merge PR44 initial

PR44 đã merge vào28 tại ccfb38d (head1311f8a);4Finance tests và proposal không còn chờ tiếp nhận. Hai commit evidence push sau merge được chuyển sang branch codex/tv5-finance-evidence-handoff từ28ca639bb, không tiếp tục push PR đóng. Cart.claim key guard mới của owner được QA riêng; xem báo cáo cùng ngày để tách lượt chạy tiếp theo và phần evidence chờ review.

### M01 owner ledger r2 — docs-only,18/10/2026

PR45 đã được owner merge vào28 tại5a3788b. [Canonical packet §§8–11](TV5_M01_HUMAN_DECISION_PACKET.md) ghi đủ10MD/6tradeoffs/partial price-slice gates,
giữ TV2/TV3 own-scope confirmations và TV1 OD07/09. M01/MD01–MD10 vẫnPENDING; business signer chờTV1/chủdựánxácnhận, chưa mở ProductJPA/Checkout money/Finance runtime.
[Checkpoint/commands/resume](../reports/TV5_FINANCE_ACCEPTANCE_2026-10-18.md) và [derived audit](../reports/evidence/m01-owner-ledger-20261018.json) xác minh61existing raw hashes,
source equivalence và original diagnostic1PASS/2FAIL; **runtime NOT_RUN trong lượt docs này**, không cộng lại các test đã chạy hoặc yêu cầu duyệt lại DTO/price compatibility giống cũ.

### M01 re-audit r3 — actual run tại8cc5ce4,18/10/2026

PR42/46 đã merge vào28; không push/request trên PRđóng. [Re-audit](../reports/TV5_M01_REAUDIT_2026-10-18.md)
và [raw manifest](../reports/evidence/m01-reaudit-20261018/manifest.json): **83Order unitPASS,16Order PGPASS, diagnostic1PASS/2FAIL**,
0error/skip, combined commandexit1/73s tại **8cc5ce4af1beacfe0d8467ec037d55e59a065691**. Đây là lượtTV5 mới, không phải17assertion harness củaTV3 hoặc cộng các snapshots cũ.
New malformed-request guard verified; FIN01precision/replay vàFIN06pre-reserve vẫnFAIL, positivecontrolPASS; assertions/source giữ nguyên.
TV3 MD05/06/08 engineering reply đã ghi nhận trong canonicalpacket; không businesssignature. M01/all10MDPENDING,
S01/Security/Inventory PENDING, C01/E01/C06REVISE; chưa production slice mới. LiveE2E/settlement/Storequeries/currentCOD NOT_RUN/BLOCKED.
Lưu22rawassets exactbytes,61historicalhashes+Gitblobs reverified;0missinginitialDockerVolumes. Giữ toàn bộ QA history, không giải quyết conflict thayowner.
