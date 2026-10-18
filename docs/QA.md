# Kiểm thử và bằng chứng

Main scaffold chưa có test nghiệp vụ; branch PR #20 đã có unit tests rules Review/Finance của TV5. Mỗi thành viên tự chạy test liên quan và báo cáo:

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

## TV5 — Finance contract / current-head checkpoint 18/10/2026

- [Sáu contract, owner/signature/module matrix và M01 candidate TV5-FIN-v0.1](TV5_FINANCE_CONTRACT_SIGNOFF.md):
  S01/M01/Inventory/Security PENDING, C01/E01 và C06 REVISE; không có cross-owner APPROVED.
- [Lượt chạy mới TV5 và QA evidence](../reports/TV5_FINANCE_QA_2026-10-18.md): parent28 bd5ff09
  61unit+11PG PASS; branch bổ sung44f2838 72unit+13PG PASS,0fail/error/skip. Hai lượt riêng, không cộng.
- FIN-01..05 ghi rõ giới hạn precision/snapshots/settlement/Store authority/rounding. Test cấu hình dùng
  fixture policy không bật cờ deploy. Live UAT2Store/COD/settlement/Store query NOT_RUN/BLOCKED.
