# TV5 Final UAT Evidence Kit — 21/11/2026

**Repository:** `duyxl56785/Project_HK5`

**Owner:** TV5 — QA Lead

**Case set:** 37 (17 baseline + 5 Voucher C1 + 4 Promotion + 11 Voucher C2)
**Execution status hiện tại:** `NOT RUN`; các gate/contract chưa đạt giữ `BLOCKED`.

## 1. Nguồn chính thức

- [Ma trận 37 case](../../../tests/TV5_FINAL_UAT_W5_C2_MATRIX_20261119.md)
- [Checklist thao tác 37 case](../../../tests/TV5_FINAL_UAT_EXECUTION_CHECKLIST_20261121.md)
- [Evidence intake và owner mapping](../../../tests/TV5_FINAL_UAT_EVIDENCE_INTAKE_20261121.md)
- [M01 Finance](../../../docs/project-report/M01_FINANCE_DECISION.md)
- [M01 Voucher C2](../../../docs/project-report/M01_VOUCHER_C2_DECISION.md)

Không dùng file template này thay cho matrix/checklist. Không có frozen SHA được xác nhận trong checkpoint hiện tại.

## 2. Cấu trúc thư mục khi có run thật

```text
tv5-final-uat-20261121/
  <run-id>/
    run-manifest.json
    checksums.sha256
    <test-id>/
      manifest.json
      raw/
      redacted/
      screenshots/
```

Không tạo `<run-id>` cho đến khi TV2 công bố frozen SHA và xác nhận môi trường. Mỗi case có một manifest; `run-manifest.json` liên kết các case trong cùng lượt.

## 3. Bộ template

- `run-manifest.template.json`: nguồn SHA, thời gian, máy, stack, DB, flags, operator và case IDs.
- `evidence-manifest.template.json`: metadata của một test case và artifacts.
- `case-evidence-map.md`: bằng chứng bắt buộc/owner cho đủ 37 ID.
- `test-result.template.md`: kết quả thực tế và oracle.
- `defect-report.template.md`: ghi nhận lỗi P0/P1/P2.
- `uat-minutes.template.md`: biên bản phiên UAT, quyết định và sign-off.
- `severity-summary.template.md`: tổng hợp P0/P1/P2, triage owner và waiver.

Template có placeholder và trạng thái `NOT RUN`; không phải receipt. Chỉ copy thành manifest/result mới khi người vận hành có giá trị thực tế.

## 4. Quy tắc chụp ảnh và lưu log

- Ảnh UI: PNG gốc, route/page, viewport, actor giả, test ID, run ID, timestamp +07:00, full frozen SHA. Chụp cả ngữ cảnh cần thiết; không dùng ảnh mock, CI hoặc rehearsal làm ảnh final.
- Ảnh 6.1–6.8: chỉ chụp trên frozen SHA theo mapping TV2; 6.4 Admin Seller approval, 6.5 Cart hai Store, 6.6 Parent Order + hai SellerOrders, 6.7 Seller isolation, 6.8 Finance/COD reconciliation. Chú thích 6.8 là “đã ghi nhận đối soát”, không phải “đã chuyển tiền”.
- HTTP: giữ request/response raw, method/path/status/body, request/correlation ID và actor; redact header/token ở bản sao.
- DB: chỉ query read-only trên DB identity được owner cấp; ghi schema/migration, query fingerprint, timestamp và result rows đã redact. Không chạy mutation trên DB dùng chung.
- Inventory/COD: lưu operation/receipt IDs, state, quantity/amount, before-after và actor/time. Không suy từ UI.
- Hash file raw trên Windows PowerShell: `Get-FileHash -LiteralPath <path> -Algorithm SHA256`. Ghi kết quả vào `checksums.sha256`; không sửa file sau khi hash.
- Thời gian dùng ISO-8601 có offset, ví dụ `2026-11-21T09:15:00+07:00`. Không ghi thời gian local thiếu offset.
- Raw có PII/secret không được upload lên GitHub. Lưu ở kho hạn chế truy cập; cung cấp redacted copy và SHA raw.

## 5. Owner gửi evidence

| Owner | Phạm vi chính | Gửi cho TV5 |
|---|---|---|
| TV2 | Freeze, CI, clean clone/stack, flags, Auth, Catalog/Store, Admin, P0, policy, ảnh 6.1–6.4, official DOCX | Frozen SHA/time, run URLs, machine/tool versions, image digests, DB identity, runtime flag receipt, P0 triage, decision links, screenshots |
| TV3 | Cart, Checkout, Order, Parent/Child, Idempotency, Voucher C1/C2, Promotion conflict | HTTP receipts, Cart/quote/Order IDs giả, key fingerprint, DB read-only snapshot, voucher ledger, rollback/error evidence |
| TV4 | Inventory, Seller isolation, fulfillment, Return, COD, RET-05 | Operation tuple/IDs, stock before-after, state transitions, COD collection, Return receipts, RET-05 exactly-once proof nếu có contract/implementation |
| TV5 | Finance money oracle/report, evidence intake, QA disposition | Calculation sheet, gross/discount/fee/net, report exports, checksum audit, defects and sign-off status |

## 6. Gate an toàn

- `HK5_VOUCHER_C2_ENABLED=false` mặc định. Chỉ TV2 mới quyết định opt-in local `.env.demo` sau khi đủ bốn điều kiện tại comment TV2 #211: #211/#212 merged; TV5 money review #211 (đã đạt); CI trên frozen SHA có `voucher-c2.spec.ts` PASS 0 skip; không còn P0 C2.
- `HK5_FINANCE_POLICY_APPROVED=false` mặc định; M01 MD01–MD11 được duyệt cho demo local/bản nộp, không phải production approval. Chỉ TV2 mới opt-in `.env.demo` trong đúng phạm vi.
- UNKNOWN claim-conflict/lost-response không chạy nếu thiếu runner, immutable tuple, provider oracle/receipt và approval riêng.
- RET-05 restock không chạy nếu TV4 chưa cung cấp contract/implementation và exactly-once receipt đã được review.
- Không xóa/prune volume, không dọn stack dùng chung, không bật cờ hoặc tạo giao dịch thật.

## 7. Liên hệ trạng thái hiện tại

Main candidate quan sát: `4b5bcb4ceb5a6a636e4c943db2d41c8a4c1bb14f`, chưa được TV2 xác nhận frozen. Post-merge CI candidate: [44502375038](https://github.com/duyxl56785/Project_HK5/actions/runs/44502375038), attempt 2 SUCCESS. Final UAT chưa chạy; không có receipt nào được tạo bởi bộ template này.
