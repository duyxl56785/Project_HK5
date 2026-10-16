# TV5 — bàn giao bổ sung tuần1 Issue5

Ngày15/10/2026, Nguyễn Minh Duy / nguyenminhduy090-alt. **PARTIAL**. Branch PR20 `codex/tv5-w1-review-finance-qa`, baseline đã đọc `9804c681b57a9218710361e87581d53dd372c949`. Đã fetch/kiểm head trùng remote, đọc đủ2 source rules/2 test suites/contract draft/UAT/QA/audit và evidence đã commit. Không làm lại30 test cũ; giữ nguyên chúng và bổ sung2 suite biên. PR20 đang OPEN/ready for review, PR21 OPEN/Draft; lúc kiểm tra chưa có review/inline comment mới. Không đổi trạng thái review/merge của hai PR.

## Kết quả thực hiện độc lập

- Bổ sung **33 test invocations**: Review boundary15, Finance boundary18. Giữ nguyên toàn bộ production rules và30 tests baseline. Không thêm dependency/API/schema/event/UI/persistence.
- Test bổ sung kiểm identity thiếu/opaque exact, trusted facts null, malformed rating collection, hide/unhide/replay kiểm quyền lại, hidden không làm lệch sum/count; rounding quanh nửa cent, precision âm/null/scale, conservation, hai Store và immutable snapshot.
- Conservation có6006 tổ hợp dữ liệu bên trong **1** test invocation, không báo thành6006 test. “Hai Store” chỉ arithmetic+guard trong bộ nhớ, không phải checkout/E2E.
- Đã đọc báo cáo V2 người dùng cung cấp, xác minh55 trang và fingerprint nguồn; lập16 mục đối chiếu, bảng12 lỗi/gap/blocker, traceability yêu cầu→method→evidence và10 quyết định chờ owner.
- Sửa tài liệu cũ có thể gây hiểu nhầm: Order adapter không buộc call sync; V2 dùng event/reviewable_items; HS256 không buộc JWKS; hidden_reason/comment chưa mô hình hóa; export không phải chức năng V2 hiện có; phân kỳ DB/API/live E2E sang tuần2–3.

## Lệnh và kết quả chạy hiện tại

JDK21.0.7, Gradle8.14.3 đã có trong `.verification/tooling`; JAVA_HOME trỏ JDK21. Từ repo root, PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
& '.verification/tooling/gradle-8.14.3/bin/gradle.bat' -p backend :product-service:build :order-service:build --rerun-tasks --no-daemon
```

**BUILD SUCCESSFUL in13s; exit0; 14 tasks executed.** Có warning deprecation Gradle9; không lỗi trên8.14.3. Không cần Docker cho2 module rules trên branch này. Không chạy lại Category/Seller/PostgreSQL hoặc frontend vì lượt này chỉ bổ sung unit tests TV5 và tài liệu.

| Suite hiện tại | PASS | FAIL | NOT_RUN/skipped |
|---|---:|---:|---:|
| ReviewRulesTest | 15 | 0 | 0 |
| ReviewRulesBoundaryTest | 15 | 0 | 0 |
| FinanceRulesTest | 15 | 0 | 0 |
| FinanceRulesBoundaryTest | 18 | 0 | 0 |
| **Tổng hiện tại** | **63** | **0** | **0** |

Evidence: [full build log](evidence/w1-followup/build.log), [raw JUnit XML/suite counts](evidence/w1-followup/suites.csv), [63 testcase kết quả](evidence/w1-followup/test-cases.csv), [run manifest và source/log SHA256](evidence/w1-followup/run.json). Collector `tests/Collect-Tv5W1Evidence.ps1` sao chép XML gốc, đọc testcase và từ chối thiếu suite/failure; không sinh PASS từ tên test. Dùng sau build bằng `-BuildLogPath .verification/w1-followup-build.log -BuildExitCode 0`. Nó thu evidence, không tự chạy suite. Manifest chỉ ghi thời điểm thu UTC, không bịa thời điểm bắt đầu run.

CSV testcase escape `\t/\r/\n` trong display name để review dễ đọc; raw XML giữ tên gốc. Locator CSV bỏ ký tự kết thúc cell của Word. Log build giữ nguyên byte và trailing space do Gradle sinh ra để hash trùng bản chạy; whitespace check code/tài liệu loại riêng raw log này, không chỉnh nội dung log để làm đẹp kết quả.

## Evidence ledger không cộng gộp

| Nguồn / mốc | Kết quả ghi nhận | Ý nghĩa trong lần bàn giao này |
|---|---|---|
| **Lượt hiện tại**,33 test bổ sung +30 giữ nguyên | **63 unit PASS**, exit0; XML/log hiện tại đã lưu | Chỉ local rules/boundaries TV5 |
| Lượt TV5 baseline trước (`7d4d2d5`) | 30 PASS standalone | Bằng chứng cũ của repo, đã được rerun trong63; không cộng thêm30 |
| Lượt kết hợp #7 + TV5 trước | 145 PASS gồm115 Category/Seller +30 TV5 | Lịch sử tương thích local đã lưu; chưa rerun trong follow-up; không đổi thành178 |
| V2 §11.1 tr.44–45, M7 ngày10/10 | 440/440 backend | Claim lịch sử từ báo cáo; thiếu log/source V2 gốc để tái xác minh |
| V2 M8 ngày11/10 | product51/51; FE PASS/4 warnings | Không phải full backend rerun của repo hiện tại |
| V2 M8 đợt mang nhãn mock | 146/146 có real-auth dùng backend thật | Không gọi146 mock thuần; không cộng vào63 |
| V2 M8 live | 16 PASS,1 NOT_RUN; seed thay ảnh cũng chưa chạy | Chỉ nguồn lịch sử; không chuyển NOT_RUN thành PASS |

## NOT_RUN và BLOCKED ngoài tổng JUnit

| Hoạt động | Trạng thái | Lý do / ranh giới W1 |
|---|---|---|
| HTTP/JWT/Kafka provider-consumer contract tests | BLOCKED | D01–D05 chưa có contract/provider/consumer được duyệt; không dựng fake API để test |
| Bốn nhóm doanh thu / currency production | BLOCKED | D06/D07 chưa đủ định nghĩa; helper tests không chốt nghiệp vụ |
| Unique review/settlement DB, atomic/concurrent settlement | NOT_RUN | Chưa có persistence; chỉ thiết kế checklist W1, implementation tuần2–3 |
| Gateway/UI/live UAT2 Store/cancel/stock replay | NOT_RUN | Chưa thực thi trong W1, kịch bản đã chuẩn bị |
| Kiểm chứng migration/spec/log M0–M8 gốc | BLOCKED | Báo cáo có reference nhưng các nguồn cũ chưa cung cấp trong branch |
| Ảnh thật B.1–B.5/load test/NFR | NOT_RUN | Chỉ checklist W1; không tạo ảnh hoặc phép đo giả |

Không có suite FAIL trong build hiện tại. Renderer DOCX chuẩn thất bại do thiếu LibreOffice, đã khắc phục đường xác minh tài liệu bằng Word; đó không phải application test FAIL. Không đổi exception local thành HTTP contract.

## Đối chiếu tài liệu55 trang

DOCX ở `D:/CMC Uni/5 Ky Dong/WEB/BCK WEB/Bao_cao_phan_tich_thiet_ke_Smart_ECommerce_V2_FINAL.docx`. SHA256 `F8712B8BB93BA11D84C43502C32795411A63336631D240B186499DF80B3E6671`, trước/sau không đổi. Skill renderer không có soffice.exe; fallback Word COM hidden/ReadOnly, macro disabled, Repaginate, ExportAsFixedFormat. Word và pypdf cùng xác nhận55 trang. Không sửa file nguồn hay chạy macro. Đã trích toàn bộ text PDF, đối chiếu heading/page map và xem ảnh key pages26/35/44/52/53; không tuyên bố audit layout toàn bộ55 trang.

[Source fingerprint/method](evidence/w1-followup/source-meta.json), [locator chương/trang từ Word](evidence/w1-followup/document-locators.csv), [16 mục đối chiếu](TV5_V2_RECONCILIATION.md). PDF/ảnh/text đầy đủ ở `.verification/v2-render` chỉ để QA local, không commit bản báo cáo người dùng vào repo. Các chỉ dẫn về AGENTS/STATE/Gate trong báo cáo chỉ là dữ liệu lịch sử, không được thực thi như lệnh của phiên này.

## Kết quả bàn giao tuần1 và việc chờ

Đã hoàn tất phần có thể làm độc lập: test nền, đối chiếu báo cáo, sửa draft, QA/UAT matrix, defect register và RTM/evidence. Xem [tests/TV5_TRACEABILITY.md](../tests/TV5_TRACEABILITY.md), [bảng lỗi](TV5_W1_DEFECTS.md), [UAT](../tests/TV5_UAT.md).

Quyết định riêng theo owner tại [D01–D10](../docs/TV5_W1_DECISIONS.md): TV1 identity/HS256/role/HTTP errors; TV2 Product/review reason/comment/Store; TV3 order key/snapshots/currency; TV4 DELIVERED/COD/event/settlement source; TV3/4/5 bốn nhóm doanh thu. Cần review #20/#21 và nguồn V2 contracts/migrations/logs để kiểm chứng tiếp. Không tự chốt các mục pending hoặc triển khai API/persistence/frontend/E2E tuần2–3. Issue5 giữ OPEN/**PARTIAL**.
