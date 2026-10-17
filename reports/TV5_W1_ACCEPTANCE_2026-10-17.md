# TV5 — QA matrix và điều kiện nghiệm thu tuần 1, 17/10/2026

Refs #5. **OPEN / PARTIAL / WAITING_REVIEW.** Phạm vi: kiểm tra PR, rules/unit, database tests đã có, QA/docs/evidence; không triển khai API/persistence/UI/E2E mới. Không Approve/Merge/Close hoặc sửa source của TV1–TV4.

## 1. Snapshot GitHub đã đối chiếu

| PR | Head đầy đủ | Trạng thái / kết luận TV5 |
|---|---|---|
| [#19](https://github.com/duyxl56785/Project_HK5/pull/19) | 1407e9d9f4f5b13286474728f1beefebcb163abc | OPEN; #21 đã được tiếp nhận; còn owner/scope confirmation và quyết định review |
| [#26](https://github.com/duyxl56785/Project_HK5/pull/26) | c4288d5c8c0cc4b8e3919dfce988b05c95a726a6 | OPEN;3 file tài liệu đã push đủ;21 ca NOT_RUN; hai finding tài liệu đã sửa |
| [#27](https://github.com/duyxl56785/Project_HK5/pull/27) | 6a7f1724bb533cc5ee92c431afb24d9844dff631 | OPEN; CI readiness success đúnghead; timezone dev còn cần TV1 xác nhận/runbook |
| [#28](https://github.com/duyxl56785/Project_HK5/pull/28) | 63f63c16d8e031f080e88d9e60a82f7d64bdf1af | OPEN;3 finding chưa có commit sửa; không review lại như đã resolved |
| [#29](https://github.com/duyxl56785/Project_HK5/pull/29) | f8b0ff4c3d0fbb4fc141cdf9ff98150b230142b8 | OPEN; Seller PostgreSQL21 PASS độc lập; chờ TV1 review/acceptance |

Không có Draft trong5 PR này. Main98268c291ed30ed5df1c981073e23bfa9b5093b2. PR #20 đã merge vào main (578a8487); #21 đã merge vào branch #19 (1407e9d); #25 đã merge vào branch TV2 (5581d59), vì vậy #29 mới là bàn giao persistence vào main. Đây là snapshot, không thay API GitHub live.

Reviewer: #26 đang requested duyxl56785/TV1/TV3; #27 duyxl56785 đã COMMENT cùnghead; #28 duyxl56785 đã CHANGES_REQUESTED cùnghead, TV1/TV4 requested. #19/#29 do duyxl56785 tạo nên không thể gán tác giả làm reviewer. TV5 đã COMMENT #29, TV1 requested. Không gửi lại request nếu đã có hoặc review cùnghead chưa có source delta.

## 2. QA matrix — lượt TV5 thực thi mới

Môi trường Java21.0.7, Gradle8.14.3, Docker Linux Engine29.8.1; Gradle dùng `--rerun-tasks --no-daemon --console=plain`. Các source snapshot đúng SHA bảng trên; raw log/XML và counts ở [evidence](evidence/w1-gates-20261017/README.md).

| ID / phạm vi | Source | PASS | FAIL | SKIP | NOT_RUN / giới hạn | Evidence |
|---|---|---:|---:|---:|---|---|
| TV5-RF, Review/Finance rules | main98268c2 | 63 | 0 | 0 | API/JWT/DB uniqueness/atomic settlement chưa kiểm | tv5-rules.log; xml/tv5-rules |
| TV2-PG, Seller persistence | #29 f8b0ff4 | 21 | 0 | 0 | JWT/REST/Store/Gateway/E2E chưa kiểm | pr29-postgres.log; xml/pr29-pg |
| TV3-PG, Cart/Order schema/constraints | #28 63f63c1 | 3 | 0 | 0 | Không kiểm3 finding đang mở hoặc live dependencies/recovery | pr28-postgres.log; xml/pr28-pg |
| TV1-TZ-FAIL, JVM Asia/Saigon | #27 6a7f172 | 1 | 2 | 0 | Hai Spring contexts lỗi trước DB test body; không phải2 ca nghiệp vụ đã chạy rồi sai assertion | pr27-saigon-tests.log; xml/pr27-saigon |
| TV1-TZ-UTC, cùng source JVM UTC | #27 6a7f172 | 3 | 0 | 0 | Hai live DB readiness +1 HS256 dependency; chưa Auth API/JWT runtime | pr27-utc-tests.log; xml/pr27-utc |
| TV4-PLAN, kiểm tra3 Markdown | #26 c4288d5 | kiểm tra tĩnh đạt | — | — | Cả21 kịch bản runtime NOT_RUN; không chuyển thành21 PASS | qa-static-checks.log |

Không cộng PASS giữa các dòng thành một release acceptance hoặc full backend build; không cộng lần timezone FAIL vào lần UTC. Lượt này không chạy full backend check/build. Source rules không đổi, không thêm regression test hình thức để có số PASS.

### Docker và dữ liệu

Trước chạy có1 container hello-world, không có named volume. Sau chạy không mất container/volume nào của inventory trước. Testcontainers dùng DB riêng cho Seller/Cart/Order. Readiness #27 dùng Compose project riêng `tv5-w1-gates-20261017142049`, localhost port tự cấp, credentials ngẫu nhiên trong bộ nhớ, không sửa `.env`/DB stack khác. Sau test đã stop riêng service readiness; giữ container/volume mới để không xóa dữ liệu. Không `down -v`, prune, DROP DATABASE, rotate secret hiện có hoặc thay timezone Windows. Xem resource-preservation.json và raw inventory logs.

## 3. Kết quả của thành viên khác / lịch sử — không gọi là lượt chạy mới TV5

| Nguồn | Kết quả được ghi | Giới hạn |
|---|---|---|
| TV2 [#29 report](https://github.com/duyxl56785/Project_HK5/blob/f8b0ff4c3d0fbb4fc141cdf9ff98150b230142b8/backend/seller-service/SELLER_TEST_REPORT.md),16/10 | 231 PASS combined main+Seller, gồm21 Seller và14 Category PostgreSQL | Raw artifacts của TV2 không có tại checkout TV5 để đối chiếu hashes; lượt TV5 mới độc lập21 Seller xác nhận phạm vi đó, chưa chạy lại14 Category/full231 |
| TV2 [#28 review](https://github.com/duyxl56785/Project_HK5/pull/28#pullrequestreview-5546002734),16/10 | 54unit+3PG PASS;3 probe FAIL | Probes của TV2, không phải3 ca TV5 đã tự chạy lại;3PG TV5 mới không phủ nhận probes |
| TV2 [#27 review](https://github.com/duyxl56785/Project_HK5/pull/27#pullrequestreview-5545551443),16/10 | Asia/Saigon1PASS/2FAIL; UTC3PASS | Nay TV5 tái hiện độc lập bằng raw evidence mới, giữ tách hai lượt/người chạy |
| TV5 audit trước | #29 80unit; #28 54unit; #27 local1PASS/2SKIP; #7 116unit/#25 133unit | Có SHA/log và ngày riêng; không cộng vào kết quả hôm nay |
| Báo cáo V2 | 440/51/146/16 và1 NOT_RUN theo ledger trước | Lịch sử tài liệu V2, không phải test repo hiện tại |

## 4. PR #19 / #21 và owner confirmations

GitHub #21 CLOSED/MERGED vào docs/cod-mvp-21-day-plan với merge1407e9d. Blob `docs/COD_MVP_21_DAY_PLAN.md` tại head#21 e8efeda và head#19 đều **5e3d39835fcfbcd5b8a35c267ba34aa2d94dbca2**, diff file rỗng. Đây là kiểm nội dung: #21 squash merge nên không dùng ancestor của e8efeda làm bằng chứng tiếp nhận.

Đã có trong #19: buyer+DELIVERED; unique(order,product); Admin hide/unhide và hidden loại khỏi public rating; fee5%/House0/net; snapshots subtotal/rate/commission/net/currency; settle DELIVERED+PAID; unique SellerOrder+transaction; Store isolation. Không cần tạo PR đưa lại cùng sửa đổi.

Còn chờ owner: TV3/TV2/TV5 chốt order/product identity; TV3/TV4/TV5 chốt currency/scale/rounding, monetary snapshots và4 nhóm doanh thu; TV1/TV2 xác nhận identity/Store policies và error mapping; TV3/TV4 event/truth source/replay/settlement transaction; chủ dự án+TV1–TV5 xác nhận việc xếp F15/F16 là P1 có phù hợp tiêu chí học phần. Bản kế hoạch có yêu cầu không đồng nghĩa những contract chi tiết đó đã được duyệt. Không tự dismiss review cũ hoặc Approve #19.

## 5. Blocker / điều kiện còn thiếu

| ID | Mức / trạng thái | Owner | Bằng chứng / bước tiếp theo |
|---|---|---|---|
| G01 | WAITING_REVIEW | TV1/TV2/TV3 | #26 đủ3 file/21 ca, Wrapper đã có; duyxl56785 đã được yêu cầu lại; chờ quyết định tài liệu, runtime NOT_RUN |
| G02 | MAJOR, reproduced / WAITING_TV1 | TV1 | PostgreSQL16.15 Debian không có Asia/Saigon trong pg_timezone_names. JVM gửi timezone đó → SQLState22023 → context FAIL. UTC ở cùng source đạt3PASS; provision/isolation/rerun đạt;2public schema vẫn0table. Cần TV1 xác nhận hướng dẫn JVM timezone hợp lệ cho test/bootRun; không sửa runbook TV1 thay owner |
| G03 | P1, NEEDS_FIX | TV3, TV1 review | #28 Order YAML không map app.security issuer/kid/key từ biến môi trường; finding TV5 vẫn mở tại head63f63c1 |
| G04 | P1, NEEDS_FIX | TV3 | #28 replay SUCCEEDED đọc lazy SellerOrder ngoài transaction, TV2 đã tái hiện LazyInitializationException |
| G05 | P1, NEEDS_FIX | TV3/TV4 | #28 stale scanner ghi đè SUCCEEDED của writer thành NEEDS_RECONCILIATION; TV2 đã tái hiện race. Không retest finding khi chưa có commit sửa |
| G06 | WAITING_REVIEW | TV1 | #29 Seller21PG nay TV5 PASS độc lập; scope persistence chưa có blocker source mới được TV5 chứng minh. Owner review/acceptance vẫn bắt buộc; không biến FixturePolicy thành verified runtime auth |
| G07 | BLOCKED contract | TV1–TV5/chủ dự án | D02–D10 chưa đủ xác nhận. D01 Auth v1.0 đã merge qua#24; runtime adapter/Store policies vẫn chưa hoàn tất |
| G08 | NOT_RUN / giai đoạn sau | TV2/TV3/TV4/TV5 | Review DB uniqueness, atomic settlement/concurrency, production Store-scoped query/aggregate, provider-consumer contract/live UAT2Store/API/JWT/UI/E2E; không triển khai tuần2–3 để tự đóng W1 |

Fee5%, House0%, net=subtotal-commission là yêu cầu đã ghi; chưa bật gate Finance của TV3. PG tests #28 inject app.security trực tiếp và không gọi full replay/scanner flow;3PASS không đóng G03–G05.

## 6. Checklist bàn giao phần TV5 tự hoàn thành

- [x] Đọc Issue/PR/head/review/comments/CI và xác minh #21 đã vào #19.
- [x] Xác minh #26 push đủ sửa tài liệu/21 NOT_RUN, không sửa branch TV4.
- [x] Rerun rules63 và PostgreSQL Seller21/CartOrder3; ghi raw logs/XML/counts/hashes.
- [x] Tái hiện timezone FAIL→UTC PASS; kiểm resource preservation và không commit secrets.
- [x] Phân biệt nguồn kết quả TV5/TV2/CI/lịch sử/V2; gắn owner cho blocker.
- [ ] TV1–TV4 review và chốt contract còn thiếu; TV3 sửa findings trước re-review.
- [ ] Bàn giao QA/evidence mới được reviewer xác nhận; Issue #5 đủ DONE.

**Kết luận:** phần QA kiểm chứng có thể làm độc lập đã được bàn giao; Issue #5 chưa đủ DONE. G02/G03–G05 và G07 cần owner; G08 ghi rõ chưa kiểm chứng, không tạo bằng chứng giả hoặc mở rộng scope.

## 7. Bản sửa bổ sung cho các finding

Người dùng đã ủy quyền TV5 trực tiếp sửa những lỗi kỹ thuật/tài liệu đủ căn cứ trên branch riêng. Các bảng1–6 và evidence c9d3921 ở trên là snapshot bàn giao trước; phần này cập nhật tiến độ mà không sửa/xóa bằng chứng lịch sử.

| Parent / fix PR | Source / commit đã push | Kết quả mới và giới hạn |
|---|---|---|
| #19 ← [#32](https://github.com/duyxl56785/Project_HK5/pull/32) | base1407e9d →0c77df6 | Đã sửa JWKS thành profile HS256/#24 và các câu#7Draft/chưa merge; static checks PASS, F15/F16 giữ nguyên. Runtime NOT_RUN/docs-only. TV1 đã APPROVED đúng0c77df6; parent#19 vẫn chờ tác giả tiếp nhận. Metadata PR#19 cũng đã sửa câu trạng thái cũ có kiểm phiên bản |
| #28 ← [#33](https://github.com/duyxl56785/Project_HK5/pull/33) | base63f63c1 →4e4ab83→59dc952→11dcf39 | JWT YAML real-env, replay/unknown-commit fetch graph có customer scope, scanner/failure-handler conditional DB transitions; final build56unit+11PG PASS. Chưa claim parent#28 resolved trước tích hợp; Finance policy/contract/V1 không đổi |
| #27 ← [#34](https://github.com/duyxl56785/Project_HK5/pull/34) | TV1a810312; TV5 giữ owner fix trong369b4f8 | Ban đầu TV5 chuẩn bị7175ea2; owner đồng thời sửa runbook nên đã đồng bộ, bỏ phần hướng dẫn trùng và chỉ thêm evidence. Java source6a7f172=a810312; Asia/Saigon1PASS2FAIL, UTC3PASS, Asia/Ho_Chi_Minh3PASS. Không đổi assertions để che lỗi, không vá timezone DB/Windows |

### RED/GREEN ledger của bản sửa Order

| Run độc lập | PASS/FAIL thực tế | Evidence trong fix PR#33 |
|---|---|---|
| JWT RED trước YAML |1PASS/1FAIL,exit1 | jwt-red |
| JWT GREEN |56unitPASS,exit0 | jwt-green (Order XML/log; Cart task/count được đối chiếu ở lượt chạy) |
| Replay RED sau JWT-only fix |1PASS/3FAIL,exit1 | replay-red |
| Replay GREEN |56unit+7PG PASS,exit0 | replay-green |
| Scanner RED ban đầu |2PASS/1FAIL,exit1 | scanner-red |
| Thêm failure-handler cùng race, RED |2PASS/2FAIL,exit1 | transitions-red |
| Final Cart/Order build +PG tasks |56unit+11PG PASS,0fail/error/skip,exit0/41s | final-green;43raw hashes/blob bytes verified |

PG11 gồm1Cart+2Order có sẵn+4replay+4scanner/failure-handler invocations. Test race giữ writer row lock thật, quan sát scanner/failure UPDATE chờ trong pg_stat_activity rồi cho writer commit; bản cũ ghi đè SUCCEEDED, bản mới giữ state/orderId. Kiểm thêm scanner thắng trước writer thì Order/outbox rollback. Không dùng mock repository hoặc sleep-only để giả concurrency. Replay tests dùng real writer/transactions/PostgreSQL, ports liên service mocked; policy tính tiền trong fixture không bật cờ Finance production.

Evidence#33: [TEST_RESULTS](https://github.com/duyxl56785/Project_HK5/blob/11dcf3969dc61ced0b819931cd946d8719617763/backend/order-service/TEST_RESULTS_TV5_FIXES.md), [raw artifacts](https://github.com/duyxl56785/Project_HK5/tree/11dcf3969dc61ced0b819931cd946d8719617763/backend/order-service/verification/tv5-fixes). Evidence#34: [timezone report](https://github.com/duyxl56785/Project_HK5/blob/369b4f8184fb70b32ea870e5d9e1fc5d572a6199/docs/TV1_TIMEZONE_RECHECK.md), [raw artifacts](https://github.com/duyxl56785/Project_HK5/tree/369b4f8184fb70b32ea870e5d9e1fc5d572a6199/docs/verification/tv5-timezone) với32hashes/blob bytes verified. Các lượt mới tách khỏi63rules/21Seller/3PG/231TV2/V2 cũ; không cộng thành một release total.

### Điều kiện tiếp nhận còn mở

- G01/#26 và#29: TV5 đã Approve đúng scope/docs c4288d5 vàpersistence f8b0ff4 trong đợt trước; đợt sửa này không thay source#26/#29. Approval không tự merge hoặc đóng Issue.
- G02: runbook đã được **TV1** sửa trong parent#27a810312. Evidence độc lập3timezone ở#34 đang chờ review/tiếp nhận; alias Asia/Saigon vẫn không hợp lệ trên image đã kiểm. Không ghi unsupported diagnostic là PASS.
- G03–G05 và failure-handler liên quan: đã sửa/test tại#33; **WAITING_REVIEW/INTEGRATION** vào#28. TV1 đã yêu cầu scanner trên head59dc952; bản11dcf39 bổ sung CAS/race evidence trả lời yêu cầu đó, không tự resolve/dismiss review của TV1/TV2.
- G07/G08: D02–D10, tiền/currency/scale/snapshots,4nhóm doanh thu, Review key/event/Store identity, COD/settlement truth source và cross-service recovery còn chờ owner. Không tự chốt contract, triển khai API/UI/E2E mới hoặc gọi source bugfix là production acceptance.
- Reviewer sau push được kiểm tra qua request/review/events. Giữ người đã chủ động bị gỡ, không tự thêm lại. Các PR mới nhắm branch parent, không main; không force push hoặc ghi đè source thành viên.

**Trạng thái cuối đợt sửa:** patch/evidence đã bàn giao, Issue#5 OPEN/PARTIAL. Chờ tác giả tiếp nhận các PR bổ sung, owner re-review head mới và xác nhận các quyết định còn mở.
