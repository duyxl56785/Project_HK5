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
