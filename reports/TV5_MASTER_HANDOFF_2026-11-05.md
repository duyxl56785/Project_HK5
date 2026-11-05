# TV5 toàn bộ PR và bàn giao — 05/11/2026

Main fetched: `00426dc3d933d02ec9545be7aea43b8a448f0829`. Live API authenticated as nguyenminhduy090-alt; all-state pagination **154 PR: 9 OPEN (3 draft), 109 merged, 36 closed unmerged**; 19 open issues. Deep metadata collected for all 9 open PRs. Closed/merged inventory is metadata, not renewed source approval. [Raw snapshot](minh-chung-kiem-thu/tv5-master-20261105/github-snapshot.json). Source policies read from current main: CONTRIBUTING/OWNERSHIP/CODEOWNERS/CONTRACTS/RUN_LOCAL/AGENTS. TV2 now owns former TV1 scope. Workspace WIP preserved.

## Bảng 1 — Toàn bộ PR mở

| PR | Author | HEAD | CI | Requested reviewers | Effective review / SHA | Blocker/gate | TV5 đã làm |
|---|---|---|---|---|---|---|---|
| #152 | duyxl56785 | `edc6a17021e0960a9b8763bb06758300b2bfd9de` | NO_CHECKS | phamthecuong2022006-alt | nguyenminhduy090-alt:APPROVED@edc6a17 | owner integration review | See action/evidence table below |
| #165 | nguyenminhduy090-alt | `8900712098f1ff202901eedba09be18ddfe79aff` | PENDING | duyxl56785 | No approval | REVIEW_REQUIRED, CI_PENDING | See action/evidence table below |
| #169 | phamthecuong2022006-alt | `436358a10f0fc8f7c9f36f5002cae47ad099558d` | SUCCESS | None | No approval | DRAFT, REVIEW_REQUIRED | See action/evidence table below |
| #170 | phamthecuong2022006-alt | `2ddc0f3d5283d6a1cc985fa203c8d0d380bc164a` | NO_CHECKS | None | nguyenminhduy090-alt:APPROVED@2ddc0f3 | DRAFT | See action/evidence table below |
| #173 | phamthecuong2022006-alt | `7ce40eb3f868bb840fddc01a6b166dc630a5d4e7` | SUCCESS | tranvanchinhbg2006-alt | No approval | MERGE_CONFLICT | See action/evidence table below |
| #175 | duyxl56785 | `4ca06352368f2cf610bde5fc1dd3cc9886e08c28` | NO_CHECKS | phamthecuong2022006-alt | tranvanchinhbg2006-alt:APPROVED@4ca0635, nguyenminhduy090-alt:APPROVED@4ca0635 | DRAFT | See action/evidence table below |
| #177 | duyxl56785 | `18b20a6f775124dc2e9c9196b61c3d378407e1ff` | SUCCESS | lexuanbao258-collab | nguyenminhduy090-alt:APPROVED@18b20a6, lexuanbao258-collab:CHANGES_REQUESTED@eb3a207 | CHANGES_REQUESTED | See action/evidence table below |
| #179 | nguyenminhduy090-alt | `7cf05ea9cc3025b5646ea4a67ee012de4b081731` | NO_CHECKS | duyxl56785 | No approval | REVIEW_REQUIRED | See action/evidence table below |
| #180 | tranvanchinhbg2006-alt | `66f3edbc921e599372fe0a2668b8e643c7d4d696` | SUCCESS | phamthecuong2022006-alt | nguyenminhduy090-alt:APPROVED@66f3edb | owner integration review | See action/evidence table below |

## Bảng 2 — Công việc TV5 và phụ thuộc

| Task | Issue/PR | Owner | Trước | Hành động | Sau | Evidence |
|---|---|---|---|---|---|---|
| Proxy forward lặp | #170/#18 | TV4 mechanics; TV5 QA | TV5 CHANGES_REQUESTED | Review source; 12 regression PASS; formal APPROVE | FIXED_VERIFIED in handler lifetime; live BLOCKED | raw TAP; review 5782069567 |
| UNKNOWN packet | #152/#175 | TV2/TV3/TV4; TV5 QA | TV5 requested | Approve docs/privacy gate; preserve execution NOT_GRANTED | WAITING_OWNER runner/stop, actual tuple/waiver, provider COMMIT receipt; claim generator absent | review 5782070115; no live fault |
| Duplicate retention patch | #173 | TV4 | conflict/request TV5 | Read diff and TV2 disposition; formal COMMENT | SUPERSEDED; owner decides retirement | review 5782070563; #171 merged |
| Return Finance oracle | #155/#156/#157/#180 | TV3 API; TV4 Seller/restock; TV5 Finance | policy now signed; TV5 pending | 4 unit +18 PG PASS; scoped APPROVE; RET matrix updated | guard FIXED_VERIFIED on PR, WAITING_INTEGRATION; final lifecycle partial | review 5782081625; raw XML |
| Evidence PR | #165 | TV5; TV2 reviewer | waiting review | verify historical hashes; add exact-head logs/oracle | READY_FOR_REVIEW scoped evidence | existing PR, no duplicate reviewer |
| Soopi | #179 | TV5; TV2 reviewer | existing tool | 6 server tests, build; start localhost and Run Selected | existing tool on 127.0.0.1:4318; UAT not implied | local tool logs/report; PR179 evidence remains distinct |
| Final UAT | #18/#157/#162 | TV5 after owners | BLOCKED | inspect latest gates; keep UNKNOWN mandatory | BLOCKED; no final run SHA | current UAT matrix |
| Report/deck final | #162 | TV5 after accepted UAT | 0/6 | preserve report/code gaps and required evidence | NOT_READY: final screenshots/traceability depend on accepted integrated flow | unchecked criteria inventory |
| Production Finance | #5/#12 | business/TV2/TV4/TV5 | PARTIAL | signed Return policy applied only to submission scope | NOT_VERIFIED; no production enablement | exact policy #155; RET03/05/live gaps |

## Bảng 3 — Checklist live

| Issue | Tick trước | Thêm | Sau | Còn thiếu |
|---|---|---|---|---|
| #162 | 0/6 | 0 | 0/6 | final UAT, report/deck/screenshots, traceability |
| #157 | 3/6 | 0 | 3/6 | Return guard reviewed on #180 but not main integrated; #5/#12 closure |
| #18 | 1/4 | 0 | 1/4 | both UNKNOWN and final UAT/release |
| #12 | 43/75 | 0 | 43/75 | 32 composite integration/acceptance criteria |
| #5 | 141/219 | 0 | 141/219 | 78 historical/composite criteria |

[Every unchecked criterion retained verbatim](TV5_CHECKLIST_REMAINDER_2026-11-05.md). No checkbox promoted by scoped branch tests. No issue closed.

## Bảng 4 — Source commit và kiểm thử phiên này

| Commit | Branch/PR | Command | Kết quả | Raw evidence |
|---|---|---|---|---|
| 2ddc0f3d5283d6a1cc985fa203c8d0d380bc164a | #170 | node --test --test-reporter=tap scripts/qa/tv4-unknown/lost-response.test.mjs | PASS 12, exit0 after sandbox EACCES 1P/11F exit1 | pr170-test*.log |
| 66f3edbc921e599372fe0a2668b8e643c7d4d696 | #180 | gradlew :order-service:test --tests '*ReturnRequestServiceRulesTest' --no-daemon --console=plain | PASS 4, exit0 | unit log/XML |
| same exact #180 | #180 | gradlew :order-service:postgresIntegrationTest --tests '*FulfillmentPostgres16IT' --no-daemon --console=plain | PASS 18, 0 skip, exit0; MockMvc + PG16, external providers mocked | PG log/XML |
| 844c6f310c4d9adca696e4d519e44f8bbe17bc26 | #179 | npm test; npm run build | PASS 6; typecheck/build exit0 | soopi-test.log; build observed |
| no live run | #18 | UNKNOWN fault/final two-Store UAT | NOT_RUN/BLOCKED: explicit execution permission and artifacts missing | no production PASS asserted |

[Current raw artifacts/SHA256](minh-chung-kiem-thu/tv5-master-20261105/). Historical PR165 manifest verification: 58 files, 0 mismatch. Generated raw logs/XML preserve bytes; CRLF/trailing whitespace may be reported by diff-check; Markdown is checked separately.

At this checkpoint **0 PR still waiting on TV5 formal review** among the four requested scopes; owner review remains. #177 retains another reviewer's CHANGES_REQUESTED at old SHA; TV5 cannot dismiss it. #169 has no reviewer (draft); #170 has no outstanding request after TV5 approval. #152/#170/#175/#179 have NO_CHECKS, not CI PASS. Other five open PRs report SUCCESS; no FAIL/PENDING in this snapshot. All open threads observed: 0. #173 conflict is superseded and remains owner's disposition. Completed 4 review handoffs including removal of 1 TV5 CHANGES_REQUESTED finding; no merge/close/dismiss. Default reviewer duyxl56785 already requested on TV5 #165/#179, so retain without duplicate. Remaining implementable TV4/TV2 work is Seller transitions/restock/Admin read-only and UNKNOWN executable/receipt binding; final evidence/report depends on these gates. No authority is inferred to sign business policy or enable flags.


## Soopi follow-up và main CI

Main `00426dc3d933d02ec9545be7aea43b8a448f0829` hosted readiness/frontend/e2e đều completed/success; [raw checks](minh-chung-kiem-thu/tv5-master-20261105/main-checks.json). Đây không khép hai UNKNOWN hoặc Return integration.

Run Selected trên tool HEAD `844c6f3`: F15 16, F16 15, JWT 2, RBAC 5 PASS. Run All Safe trên cùng HEAD: F15 16, F16 15, JWT 2, RBAC 5, Catalog 100, Inventory 9, Cart 10, Frontend 157 PASS; từng suite có exit0/log/XML hoặc TAP, không cộng hai run. Các manifest run được kiểm SHA256 trước xuất bản.

Phát hiện/sửa lỗi TV5 tool: F15 title/expected nói R06 nhưng allowlist thiếu `ReviewCommentLimitTest`. Commit **b51f4ad9638b7a1ad157b72fafeb12ed2697479c**, branch `codex/tv5-qa-dashboard-20261105`, đã push vào #179, thêm đúng class vào suite. Không có assertion RED nhân tạo; baseline report chứng minh chỉ 16 rules, không có R06 XML. Self-tests 6 PASS/typecheck/build PASS. Rerun F15/QA-UI tại commit này được lưu riêng, không gán run cũ cho source mới. Reviewer duyxl56785 đã có request trên #179, giữ nguyên.

Kiểm timeline trước khi bổ sung reviewer #169/#170: điều kiện giữ quyết định trước đó được kích hoạt, không request lại. #169 vẫn thiếu reviewer ở snapshot; TV5 không đảo quyết định đã gỡ/request/review trước đó. Báo cáo metadata có thể thay đổi sau snapshot; các review URL/source SHA là immutable provenance.

Rerun at b51f4ad: **F15 18 PASS (16 rules +2 R06), QA-UI 2 PASS**, two actual screenshots inspected; raw receipts committed/pushed as **7cf05ea** on #179. No other tests rerun on evidence-only head.


### Publication receipt

Evidence/oracle commit **8900712098f1ff202901eedba09be18ddfe79aff** pushed to existing #165; Soopi code **b51f4ad9638b7a1ad157b72fafeb12ed2697479c** and raw receipts **7cf05ea9cc3025b5646ea4a67ee012de4b081731** pushed to existing #179. Final table uses post-push snapshot at 08:57:33 UTC: #165 CI PENDING, no FAIL observed; #179 NO_CHECKS. Both retain requested duyxl56785. Raw evidence: 13/13 manifest entries matched committed Git blob SHA256, not only working tree.

Reviewer clarification after timeline/review readback: #169/#170 have zero duyxl56785 removals, zero current requests, **one formal review event at current head** each (COMMENTED scope is omitted by the audit's effective-opinionated-review column). Therefore do not re-request solely because requested list is empty. #169 lacks approval, not proof that nobody reviewed it. This supersedes earlier wording about missing reviewer; no reviewer request was added.

Issues read back after checkpoint comments: #162 0/6, #157 3/6, #18 1/4, #12 43/75, #5 141/219. New comments: #165/6653881238, #179/6653881887, #157/6653882491, #18/6653883118, #5/6653883695, #12/6653884555. No checkbox mutation, close or merge.
